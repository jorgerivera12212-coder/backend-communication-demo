import httpx
import pytest
from fastapi.testclient import TestClient

from app import main
from app.main import app

# raise_server_exceptions=False: una excepción no controlada se ve como 500, igual que en producción
client = TestClient(app, raise_server_exceptions=False)

USER = {"id": 1, "name": "John Doe", "email": "john@example.com"}


def mock_backend_b(monkeypatch, handler):
    """Make httpx.AsyncClient inside app.main use a fake transport instead of the network."""
    real_client = httpx.AsyncClient

    def fake_client(*args, **kwargs):
        return real_client(*args, transport=httpx.MockTransport(handler), **kwargs)

    monkeypatch.setattr(main.httpx, "AsyncClient", fake_client)


def test_health():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "backend-a"}


def test_profile_returns_user_from_backend_b(monkeypatch):
    def handler(request):
        assert request.url.path == "/user"
        return httpx.Response(200, json=USER)

    mock_backend_b(monkeypatch, handler)

    response = client.get("/profile")

    assert response.status_code == 200
    assert response.json() == {
        "message": "Profile retrieved from backend-b",
        "user": USER,
    }


def test_profile_returns_502_when_backend_b_fails(monkeypatch):
    mock_backend_b(monkeypatch, lambda request: httpx.Response(500))

    response = client.get("/profile")

    assert response.status_code == 502
    assert response.json() == {"detail": "Error communicating with backend-b"}


def test_profile_returns_502_when_backend_b_is_unreachable(monkeypatch):
    def handler(request):
        raise httpx.ConnectError("connection refused")

    mock_backend_b(monkeypatch, handler)

    response = client.get("/profile")

    assert response.status_code == 502


@pytest.mark.parametrize(
    "backend_b_response",
    [
        httpx.Response(200, text="<html>proxy error</html>"),
        httpx.Response(200, text="{not json", headers={"Content-Type": "application/json"}),
        httpx.Response(200),
        httpx.Response(200, json=["not", "an", "object"]),
    ],
    ids=["html", "invalid-json", "empty-body", "json-not-object"],
)
def test_profile_returns_502_when_backend_b_returns_invalid_body(monkeypatch, backend_b_response):
    mock_backend_b(monkeypatch, lambda request: backend_b_response)

    response = client.get("/profile")

    assert response.status_code == 502
    assert response.json() == {"detail": "Error communicating with backend-b"}

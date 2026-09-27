import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException

from app.config import settings

logging.basicConfig(
    level=settings.log_level.upper(),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger(settings.app_name)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "Starting %s (env=%s, backend_b_url=%s)",
        settings.app_name,
        settings.app_env,
        settings.backend_b_url,
    )
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}


@app.get("/profile")
async def profile():
    url = f"{settings.backend_b_url}/user"
    logger.info("GET /profile -> calling %s", url)

    try:
        async with httpx.AsyncClient(timeout=settings.backend_b_timeout) as client:
            response = await client.get(url)
            response.raise_for_status()

        logger.debug("backend-b response: %s", response.text)

        return {
            "message": "Profile retrieved from backend-b",
            "user": response.json(),
        }

    except httpx.HTTPError as exc:
        logger.error("Error communicating with backend-b: %r", exc)
        raise HTTPException(
            status_code=502,
            detail="Error communicating with backend-b",
        )

FROM python:3.12-slim

# Sin .pyc (el usuario de runtime no puede escribir en /app) y logs sin buffer
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Solo dependencias de runtime, con versiones fijas; pytest está en requirements-dev.txt
COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Usuario sin privilegios: el código queda como root (solo lectura para la app)
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
USER app

EXPOSE 8000

# Solo consulta el /health propio (no llama a backend-b), así no hay dependencias circulares.
# python:3.12-slim no trae curl: se usa urllib, que falla con cualquier respuesta que no sea 2xx.
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD ["python", "-c", "import os, urllib.request; urllib.request.urlopen('http://127.0.0.1:' + os.environ.get('SERVER_PORT', '8000') + '/health', timeout=2)"]

# Arranca Uvicorn en el puerto de SERVER_PORT (ver app/__main__.py)
CMD ["python", "-m", "app"]

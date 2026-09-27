"""Punto de entrada: `python -m app`.

Arranca Uvicorn con el puerto definido en SERVER_PORT, igual que backend-b
lee server.port desde su configuración.
"""

import uvicorn

from app.config import settings

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.server_port)

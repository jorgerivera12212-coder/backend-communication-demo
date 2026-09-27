FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Arranca Uvicorn en el puerto de SERVER_PORT (ver app/__main__.py)
CMD ["python", "-m", "app"]

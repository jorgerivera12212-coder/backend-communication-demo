from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuración leída de variables de entorno.

    Desde el IDE se cargan de backend-a/.env.local; en Docker llegan desde
    env/<DEPLOY_ENV>/backend-a.env (env_file de Compose).
    Las variables de entorno reales tienen prioridad sobre el archivo .env.local.
    """

    model_config = SettingsConfigDict(env_file=".env.local", extra="ignore")

    app_name: str = "backend-a"
    app_env: str = "dev"
    log_level: str = "INFO"
    server_port: int = 8000
    backend_b_url: str = "http://localhost:8080"
    backend_b_timeout: float = 5.0


settings = Settings()

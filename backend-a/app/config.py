from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuración leída de variables de entorno.

    En Docker llegan desde env/<DEPLOY_ENV>/backend-a.env (env_file de Compose).
    Desde el IDE no hace falta ningún archivo: se usan los valores por defecto (dev, localhost).
    """

    model_config = SettingsConfigDict(extra="ignore")

    app_name: str = "backend-a"
    app_env: str = "dev"
    log_level: str = "INFO"
    server_port: int = 8000
    backend_b_url: str = "http://localhost:8080"
    backend_b_timeout: float = 5.0


settings = Settings()

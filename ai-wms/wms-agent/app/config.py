# -*- coding: utf-8 -*-
"""服务配置"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ---- 服务自身 ----
    app_name: str = "AI-WMS Agent"
    version: str = "1.0.0"
    port: int = 8000

    # ---- Java 业务服务（同步调用） ----
    java_base_url: str = "http://localhost:8080/api"

    # ---- 数据库（读波次任务、库位坐标） ----
    db_host: str = "localhost"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = "123456"
    db_name: str = "ai_wms"

    # ---- LLM（可选，未配置时自动降级为规则桩） ----
    # 支持任何 OpenAI 兼容接口：通义千问 / DeepSeek / 智谱 等
    llm_api_key: str = ""
    llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    llm_model: str = "qwen-plus"

    @property
    def db_url(self) -> str:
        return (f"mysql+pymysql://{self.db_user}:{self.db_password}"
                f"@{self.db_host}:{self.db_port}/{self.db_name}?charset=utf8mb4")

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()

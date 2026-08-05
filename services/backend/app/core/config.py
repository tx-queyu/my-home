"""应用配置：环境变量驱动，前缀 MYHOME_。"""
from pydantic import model_validator
from pydantic_settings import BaseSettings


DEFAULT_JWT_SECRET = "dev-secret-change-in-production-please"


class Settings(BaseSettings):
    app_name: str = "MyHome-Backend"
    environment: str = "dev"
    debug: bool = False

    database_url: str = "postgresql+asyncpg://myhome:myhome_dev@localhost:5432/myhome"

    cors_origins: str = "*"

    pool_size: int = 5
    pool_pre_ping: bool = True

    jwt_secret: str = DEFAULT_JWT_SECRET
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440
    refresh_token_expire_days: int = 90

    # 生产环境关闭 /auth/register：家庭 App 不应任何人都能创建家庭。
    # 部署后用 MYHOME_REGISTRATION_ENABLED=false 关闭；首次部署时设 true 创建首个家庭后改 false。
    registration_enabled: bool = False

    # 凭证加密 key（Fernet）。prod 必须显式设置；dev 派生兜底。
    # key 丢失 = 所有历史 AK/SK 不可解；prod 部署后必须备份。
    crypto_key: str = ""

    # 验证码服务参数
    verification_code_ttl_minutes: int = 10
    verification_code_max_attempts: int = 5
    verify_token_ttl_minutes: int = 10
    verification_daily_target_limit: int = 10
    verification_hourly_ip_limit: int = 20

    # 讯飞语音评测（ISE）—— child 朗读单词评分。
    # 三件套：APP_ID/API_KEY/API_SECRET 从 https://www.xfyun.cn 注册「语音评测」服务获取。
    # 未配置时 /assess endpoint 返回 503 disabled（朗读练习降级为「固定等待」模式）。
    iflytek_app_id: str = ""
    iflytek_api_key: str = ""
    iflytek_api_secret: str = ""
    # 通过分数阈值（0-100，KET 朗读单词推荐 60-70）
    reading_pass_score: int = 60

    @model_validator(mode="after")
    def _assert_prod_secrets(self):
        if self.environment == "dev":
            return self
        if not self.jwt_secret or self.jwt_secret == DEFAULT_JWT_SECRET:
            raise ValueError(
                "MYHOME_JWT_SECRET 必须在非 dev 环境显式设置为非默认值"
                f"（当前 environment={self.environment!r}）"
            )
        if not self.crypto_key:
            raise ValueError(
                "MYHOME_CRYPTO_KEY 必须在非 dev 环境显式设置"
                f"（当前 environment={self.environment!r}）"
            )
        return self

    class Config:
        env_file = ".env"
        env_prefix = "MYHOME_"


settings = Settings()

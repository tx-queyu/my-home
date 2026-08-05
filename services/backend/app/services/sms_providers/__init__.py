"""短信服务商 provider 工厂：按 provider 名称路由 probe / send 函数。

每个 provider 模块暴露两个函数：
- probe(cfg, secrets) -> None  探活（调只读 list API，不真实发短信）
- send(cfg, secrets, phone, code) -> None  真实发码

失败抛 Exception（由调用方分类转中文错误信息）。
"""

from app.services.sms_providers import aliyun_provider, huawei_provider, tencent_provider

_PROVIDERS = {
    "aliyun": aliyun_provider,
    "tencent": tencent_provider,
    "huawei": huawei_provider,
}


def get_probe(provider: str):
    module = _PROVIDERS.get(provider)
    return module.probe if module else None


def get_sender(provider: str):
    module = _PROVIDERS.get(provider)
    return module.send if module else None

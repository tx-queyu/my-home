"""SMTP 探活 + 发码 — stdlib smtplib。

encryption=ssl → SMTP_SSL(port=465)；encryption=starttls → SMTP(port=587)+starttls()；
encryption=none → SMTP(port=25) 明文（不推荐）。
"""

import smtplib
import ssl
from email.mime.text import MIMEText
from email.utils import formataddr, formatdate


def _open_connection(cfg, secrets: dict) -> smtplib.SMTP:
    """建立 SMTP 连接（不发认证）。调用方负责 close。"""
    host = cfg.smtp_host
    port = cfg.smtp_port or 465
    username = cfg.username
    password = secrets.get("password", "")
    encryption = (cfg.encryption or "ssl").lower()

    if encryption == "ssl":
        context = ssl.create_default_context()
        client: smtplib.SMTP = smtplib.SMTP_SSL(host, port, context=context, timeout=15)
    elif encryption == "starttls":
        client = smtplib.SMTP(host, port, timeout=15)
        client.ehlo()
        client.starttls(context=ssl.create_default_context())
        client.ehlo()
    else:
        client = smtplib.SMTP(host, port, timeout=15)
        client.ehlo()

    if username:
        client.login(username, password)
    return client


def probe(cfg, secrets: dict) -> None:
    """SMTP 探活。secrets = {"password": str}。

    成功 login 即认为 AK/SK 可用；不实际发邮件。
    """
    if not cfg.smtp_host:
        raise Exception("SMTP 主机未配置")
    if not cfg.username:
        raise Exception("SMTP 用户名未配置")
    if not secrets.get("password"):
        raise Exception("SMTP 密码未配置")

    client = _open_connection(cfg, secrets)
    try:
        client.noop()
    finally:
        try:
            client.quit()
        except Exception:
            pass


def send(cfg, secrets: dict, to: str, code: str) -> None:
    """SMTP 发码。secrets = {"password": str}。"""
    if not cfg.smtp_host:
        raise Exception("SMTP 主机未配置")
    if not cfg.username:
        raise Exception("SMTP 用户名未配置")
    if not cfg.from_email:
        raise Exception("发件人邮箱未配置")

    subject = "您的验证码"
    body = f"您的验证码是：{code}，10 分钟内有效。如非本人操作，请忽略本邮件。"

    msg = MIMEText(body, "plain", "utf-8")
    msg["Subject"] = subject
    msg["From"] = formataddr((cfg.from_name or cfg.smtp_host, cfg.from_email))
    msg["To"] = to
    msg["Date"] = formatdate(localtime=True)

    client = _open_connection(cfg, secrets)
    try:
        client.sendmail(cfg.from_email, [to], msg.as_string())
    finally:
        try:
            client.quit()
        except Exception:
            pass

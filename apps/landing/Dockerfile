# MyHome landing 主页 — 多阶段构建（node 构建 + nginx 服务）
# 单 VitePress 工程，npm install，无需 pnpm workspace。
FROM docker.m.daocloud.io/library/node:22-alpine AS build-stage

WORKDIR /app
ENV CI=true
# npm 走 npmmirror 加速国内构建
ENV npm_config_registry=https://registry.npmmirror.com

COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# ------------------------------------------------------------
# 生产镜像
# ------------------------------------------------------------
FROM docker.m.daocloud.io/library/nginx:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
# vitepress build 产物（HTML/CSS/JS）
COPY --from=build-stage /app/.vitepress/dist/ /usr/share/nginx/html/
# APK 等静态资源（vitepress publicDir 在 srcDir 模式下不一定拷贝，直接绕过）
COPY public/ /usr/share/nginx/html/
# VitePress 产物文件默认 0640，nginx worker（非 root）读不到 → 统一放开读权限
RUN chmod -R a+rX /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

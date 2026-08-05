<script setup lang="ts">
// CTA 指向 /docs，nginx 反代到 backend FastAPI Swagger UI。
// target="_top" 让浏览器 full page load，绕过 VitePress SPA router 拦截。
const docsUrl = "/docs";
</script>

<template>
  <div class="landing-cta">
    <a class="cta-btn primary" :href="docsUrl" target="_top">查看 API 文档</a>
  </div>
</template>

<style scoped>
.landing-cta {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  padding: 24px 0 16px;
}

.cta-btn {
  display: inline-flex;
  align-items: center;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-alt);
  color: var(--vp-c-text-1);
}

.cta-btn.primary {
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
  border-color: var(--vp-c-brand-1);
}

.cta-btn.primary:hover {
  background: var(--vp-c-brand-2);
}
</style>

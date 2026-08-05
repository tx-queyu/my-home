<script setup lang="ts">
// 下载卡片：Android 可下载（APK 文件在 landing 的 public/ 目录），iOS/HarmonyOS 待发布。
// 版本号、发布日期、版本描述动态拉取 /version.json（同源静态文件，由 nginx serve）。
// 拉取失败时 fallback 到本地常量，保证页面不空白。

import { computed, onMounted, ref } from "vue";

interface VersionInfo {
  version: string;
  apkUrl: string;
  description?: string;
  releaseDate?: string;
}

interface Platform {
  key: "android" | "harmony" | "ios";
  name: string;
  status: "available" | "coming";
  statusLabel: string;
  sysReq: string;
  sizeNote: string;
  storeLabel: string;
  downloadUrl?: string;
}

const FALLBACK_VERSION = "0.1.0";
const APK_URL = "/myhome.apk";
const APK_SIZE_NOTE = "约 18 MB";

const version = ref(FALLBACK_VERSION);
const releaseDate = ref<string | null>(null);
const description = ref<string | null>(null);
const fetchError = ref(false);
const loading = ref(true);

onMounted(async () => {
  try {
    const resp = await fetch("/version.json", { cache: "no-store" });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const data = (await resp.json()) as VersionInfo;
    version.value = data.version || FALLBACK_VERSION;
    releaseDate.value = data.releaseDate ?? null;
    description.value = data.description ?? null;
  } catch (e) {
    fetchError.value = true;
  } finally {
    loading.value = false;
  }
});

const platforms: Platform[] = [
  {
    key: "android",
    name: "Android",
    status: "available",
    statusLabel: "已发布",
    sysReq: "Android 8.0（API 26）及以上",
    sizeNote: APK_SIZE_NOTE,
    storeLabel: "下载 APK",
    downloadUrl: APK_URL,
  },
  {
    key: "harmony",
    name: "鸿蒙",
    status: "coming",
    statusLabel: "敬请期待",
    sysReq: "HarmonyOS 4.0 及以上",
    sizeNote: "AppGallery 内测",
    storeLabel: "通知我",
  },
  {
    key: "ios",
    name: "iOS",
    status: "coming",
    statusLabel: "敬请期待",
    sysReq: "iOS 15 及以上",
    sizeNote: "TestFlight 内测",
    storeLabel: "通知我",
  },
];

const installSteps = computed(() => [
  "点击 Android 卡片的「下载 APK」按钮，浏览器会自动下载安装包。",
  "在通知栏或文件管理器中点击下载好的 myhome.apk 文件。",
  "如系统提示「未知来源」，按引导允许当前来源安装。",
  `打开 MyHome App（v${version.value}），用家庭账号登录即可使用。`,
]);

const features = [
  { title: "家居管理", details: "电器状态、维修记录一目了然，全家共享。" },
  { title: "学习任务", details: "作业记录、积分兑换，家长陪伴式管理。" },
  { title: "多用户", details: "家长与孩子独立账号，权限分级。" },
  { title: "数据隔离", details: "多家庭数据严格隔离，跨家庭访问返回 404。" },
  { title: "加密存储", details: "Token 落地 EncryptedSharedPreferences，防备份提取。" },
  { title: "自托管", details: "服务部署在自家服务器，数据自主可控。" },
];
</script>

<template>
  <div class="download-page">
    <section class="hero">
      <h1 class="title">MyHome 客户端</h1>
      <p class="subtitle">家庭生活管理原生客户端，选择你的平台下载。</p>
      <div class="badges">
        <span class="badge">v{{ version }}</span>
        <span v-if="releaseDate" class="badge">发布于 {{ releaseDate }}</span>
        <span class="badge">3 个平台</span>
      </div>
      <p v-if="description" class="release-note">{{ description }}</p>
      <p v-else-if="fetchError" class="release-note error">
        版本信息暂时不可用，显示的是本地缓存版本号。
      </p>
    </section>

    <section class="platform-grid">
      <div
        v-for="p in platforms"
        :key="p.key"
        class="platform-card"
        :class="{ 'is-available': p.status === 'available' }"
      >
        <div class="platform-head">
          <h3 class="platform-name">{{ p.name }}</h3>
          <span
            class="platform-status"
            :class="p.status === 'available' ? 'status-available' : 'status-coming'"
          >
            {{ p.statusLabel }}
          </span>
        </div>
        <dl class="platform-meta">
          <div class="meta-row">
            <dt>系统要求</dt>
            <dd>{{ p.sysReq }}</dd>
          </div>
          <div class="meta-row">
            <dt>安装包</dt>
            <dd>{{ p.sizeNote }}</dd>
          </div>
        </dl>
        <div class="platform-actions">
          <a
            v-if="p.status === 'available' && p.downloadUrl"
            class="cta-btn primary"
            :href="p.downloadUrl"
            download
          >
            {{ p.storeLabel }}
          </a>
          <button v-else class="cta-btn disabled" type="button" disabled>
            {{ p.storeLabel }}
          </button>
        </div>
      </div>
    </section>

    <section class="meta">
      <div class="meta-block">
        <h2>安装步骤（Android）</h2>
        <ol>
          <li v-for="(step, i) in installSteps" :key="i">{{ step }}</li>
        </ol>
      </div>
    </section>

    <section class="features">
      <h2>主要功能</h2>
      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <h3>{{ f.title }}</h3>
          <p>{{ f.details }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.download-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

.hero {
  text-align: center;
  margin-bottom: 40px;
}

.title {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 8px;
  color: var(--vp-c-text-1);
}

.subtitle {
  font-size: 16px;
  color: var(--vp-c-text-2);
  margin: 0 0 16px;
}

.badges {
  display: inline-flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--vp-c-divider);
  color: var(--vp-c-text-2);
  background: var(--vp-c-bg-soft);
}

.release-note {
  margin: 16px 0 0;
  font-size: 14px;
  color: var(--vp-c-text-2);
  line-height: 1.6;
}

.release-note.error {
  color: var(--vp-c-danger-1);
}

.platform-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.platform-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
}

.platform-card.is-available {
  border-color: var(--vp-c-brand);
  box-shadow: 0 0 0 1px var(--vp-c-brand-soft);
}

.platform-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.platform-name {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--vp-c-text-1);
}

.platform-status {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
}

.status-available {
  color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
}

.status-coming {
  color: var(--vp-c-text-2);
  background: var(--vp-c-bg-alt);
  border: 1px solid var(--vp-c-divider);
}

.platform-meta {
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}

.meta-row dt {
  flex: 0 0 64px;
  color: var(--vp-c-text-2);
}

.meta-row dd {
  margin: 0;
  color: var(--vp-c-text-1);
}

.platform-actions {
  margin-top: auto;
}

.cta-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-alt);
  color: var(--vp-c-text-1);
  width: 100%;
  box-sizing: border-box;
}

.cta-btn.primary {
  background: var(--vp-c-brand-1);
  color: var(--vp-c-white);
  border-color: var(--vp-c-brand-1);
}

.cta-btn.primary:hover {
  background: var(--vp-c-brand-2);
}

.cta-btn.disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 32px;
  margin-bottom: 48px;
}

.meta-block h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--vp-c-text-1);
}

.meta-block ol {
  padding-left: 20px;
  margin: 0;
  color: var(--vp-c-text-2);
  font-size: 14px;
  line-height: 1.8;
}

.features h2 {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 16px;
  color: var(--vp-c-text-1);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.feature-card {
  padding: 16px;
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
}

.feature-card h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 6px;
  color: var(--vp-c-text-1);
}

.feature-card p {
  font-size: 13px;
  margin: 0;
  color: var(--vp-c-text-2);
  line-height: 1.6;
}

@media (max-width: 640px) {
  .title {
    font-size: 28px;
  }
  .download-page {
    padding: 20px 16px 48px;
  }
  .platform-grid {
    grid-template-columns: 1fr;
  }
}
</style>

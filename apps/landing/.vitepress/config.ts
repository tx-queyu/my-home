import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "zh-CN",
  title: "MyHome",
  description: "家庭级生活管理平台",
  base: "/",
  cleanUrls: true,
  srcDir: "content",
  publicDir: "public",
  lastUpdated: false,
  themeConfig: {
    nav: [
      { text: "首页", link: "/" },
      { text: "下载 App", link: "/download" },
      { text: "API 文档", link: "/docs" },
    ],
    footer: {
      message: "自托管家庭级生活管理平台",
      copyright: "© 2026 MyHome",
    },
  },
});

import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import LandingCTA from "./components/LandingCTA.vue";
import VersionFooter from "./components/VersionFooter.vue";
import DownloadCard from "./components/DownloadCard.vue";
import "./style.css";

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "home-hero-after": () => h(LandingCTA),
    }),
  enhanceApp(ctx) {
    ctx.app.component("VersionFooter", VersionFooter);
    ctx.app.component("DownloadCard", DownloadCard);

    if (typeof window !== "undefined") {
      // nav 里指向 /docs 的链接加 target="_top"，绕过 VitePress SPA router
      // landing 站点只有 / 和 /download 两个路由，/docs 是反代到 backend Swagger 的外部入口
      const patchNavLinks = () => {
        document
          .querySelectorAll<HTMLAnchorElement>('.VPNavBarMenuLink[href^="/docs"]')
          .forEach((a) => {
            a.target = "_top";
          });
      };
      setTimeout(patchNavLinks, 200);
      ctx.router.onAfterRouteChange = () => {
        setTimeout(patchNavLinks, 100);
      };
    }
  },
};

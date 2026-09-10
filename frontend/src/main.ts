import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import riIcons from "@iconify-json/ri/icons.json";
import tablerIcons from "@iconify-json/tabler/icons.json";
import { addCollection } from "@iconify/vue/offline";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./App.vue";
import router from "./router";
import { useSessionStore } from "./stores/session";
import "element-plus/dist/index.css";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/buttons.css";

// 图标集合随构建产物发布，禁止运行时向第三方图标服务请求资源。
addCollection(tablerIcons);
addCollection(riIcons);

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(ElementPlus, { locale: zhCn });

const sessionStore = useSessionStore();
await sessionStore.initialize();

app.use(router);
app.mount("#app");

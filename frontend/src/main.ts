import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./App.vue";
import router from "./router";
import { useSessionStore } from "./stores/session";
import "./styles/tokens.css";
import "./styles/base.css";
import "element-plus/dist/index.css";

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(ElementPlus, { locale: zhCn });
app.use(router);

const sessionStore = useSessionStore();
await sessionStore.initialize();

app.mount("#app");

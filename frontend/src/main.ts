import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { createPinia } from "pinia";
import { createApp } from "vue";

import App from "./App.vue";
import router from "./router";
import { useSessionStore } from "./stores/session";
import "element-plus/dist/index.css";
import "./styles/tokens.css";
import "./styles/base.css";
import "./styles/buttons.css";

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(ElementPlus, { locale: zhCn });

const sessionStore = useSessionStore();
await sessionStore.initialize();

app.use(router);
app.mount("#app");

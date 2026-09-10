import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  realpathSync,
  statSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test, { after } from "node:test";

// 所有文件均为隔离夹具；Docker 命令被专用替身拦截，不连接任何真实引擎或业务服务。
// 按运维安全约定保留测试目录，不调用删除、删卷或清理命令。
const repository = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const script = join(repository, "scripts/production.sh");
const testRoot = mkdtempSync(join(realpathSync(tmpdir()), "materials-lab-production-tests-"));
const fakeBin = join(testRoot, "bin");
mkdirSync(fakeBin);

const fakeDocker = `#!${process.execPath}
import { appendFileSync, existsSync, readFileSync, writeFileSync } from "node:fs";
const args = process.argv.slice(2);
const log = process.env.LAB_PRODUCTION_TEST_DOCKER_LOG;
const stateFile = process.env.LAB_PRODUCTION_TEST_DOCKER_STATE;
const mode = process.env.LAB_PRODUCTION_TEST_DOCKER_MODE || "healthy";
if (!log || !stateFile) throw new Error("Docker 替身必须由隔离测试调用");
appendFileSync(log, JSON.stringify(args) + "\\n");
const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, "utf8")) : {};
const finish = (code = 0, output = "") => { process.stdout.write(output); process.exit(code); };
if (args[0] === "info") finish(mode === "engine-fails" ? 23 : 0);
if (args[0] === "image" && args[1] === "inspect") {
  const postgresImage = args.at(-1).startsWith("postgres:18.4-alpine@sha256:");
  const rustfsImage = args.at(-1).startsWith("rustfs/rustfs:v1.0.0-rc.5@sha256:");
  const unavailable = mode === "missing-images" || (mode === "missing-postgres" && postgresImage) || (mode === "build-images-absent" && !postgresImage && !rustfsImage && !state.built);
  if (unavailable) finish(33);
  if (args.includes("--format")) {
    const image = args.at(-1);
    const digest = image.startsWith("materials-lab-api:") ? "a" : image.startsWith("materials-lab-web:") ? "b" : "c";
    finish(0, "sha256:" + digest.repeat(64) + "\\n");
  }
  finish();
}
if (args[0] === "build") {
  if (mode === "build-fails") finish(41);
  state.built = true;
  writeFileSync(stateFile, JSON.stringify(state));
  finish();
}
if (args[0] === "inspect" && args.at(-1) === "synthetic-container-id") {
  finish(mode === "inspect-fails" ? 73 : 0, mode === "running" ? "running\\n" : "exited\\n");
}
if (args[0] === "inspect" && /^synthetic-(api|web|postgres|rustfs)-container$/.test(args.at(-1))) {
  const service = args.at(-1).split("-")[1];
  const image = service === "postgres" ? "postgres:18.4-alpine" : service === "rustfs" ? "rustfs/rustfs:v1.0.0-rc.5" : "materials-lab-" + service + ":source-release-20260801";
  const hash = { api: "a", web: "b", postgres: "c", rustfs: "d" }[service].repeat(64);
  finish(0, image + " sha256:" + hash + "\\n");
}
if (args[0] !== "compose") throw new Error("拒绝未建模的 Docker 操作");
let offset = 1;
while (["--project-directory", "--env-file", "-p", "-f"].includes(args[offset])) offset += 2;
const action = args[offset];
const tail = args.slice(offset + 1);
if (action === "config") finish(mode === "config-fails" ? 19 : 0);
if (action === "ps") {
  if (mode === "ps-fails") finish(71);
  // 单服务查询用于备份来源；多服务查询用于检查写入口是否停止。
  if (tail.length === 3 && tail[0] === "--all" && tail[1] === "-q" && ["api", "web", "postgres", "rustfs"].includes(tail[2])) {
    finish(0, "synthetic-" + tail[2] + "-container\\n");
  }
  finish(0, ["running", "inspect-fails"].includes(mode) ? "synthetic-container-id\\n" : "");
}
if (action === "stop" && tail.join(" ") === "web api") {
  finish(process.env.LAB_PRODUCTION_TEST_STOP_FAILURE === "true" ? 57 : 0);
}
if (action === "up" && tail.includes("api") && tail.includes("web")) {
  state.started = true;
  writeFileSync(stateFile, JSON.stringify(state));
  finish(mode === "api-up-fails" ? 42 : 0);
}
if (action === "up" && tail.includes("postgres") && !tail.includes("api") && !tail.includes("web")) finish();
if (action === "run" && tail.at(-1) === "bootstrap") finish();
if (action === "exec" && tail.includes("postgres")) {
  const command = tail.at(-1);
  const pgRestore = tail.includes("pg_restore") || command.includes("pg_restore");
  if (command.includes("psql")) {
    const query = readFileSync(0, "utf8");
    if (query.includes("pg_class")) finish(0, "0\\n");
    if (query.includes("project_document_content") && query.includes("uk_user_account_username")) {
      finish(0, "1|1|1\\n");
    }
    if (query.includes("duplicate_usernames")) {
      state.globalUsernameChecks = (state.globalUsernameChecks || 0) + 1;
      writeFileSync(stateFile, JSON.stringify(state));
      const duplicate = mode === "duplicate-usernames" || (mode === "duplicate-usernames-after-stop" && state.globalUsernameChecks >= 2);
      finish(0, duplicate ? "1\\n" : "0\\n");
    }
    if (query.includes("SELECT version, script, checksum, success FROM flyway_schema_history")) {
      finish(0, "1|V1__materials_lab_schema.sql|111|t\\n");
    }
    finish(0, mode === "seeded" || (mode === "post-start-data-fails" && state.started) ? "1|1|2\\n" : "0|1|2\\n");
  }
  if (command.includes("pg_dump")) finish(mode === "backup-fails" ? 37 : 0, "SYNTHETIC-POSTGRES-DUMP");
  if (pgRestore && tail.includes("--list")) {
    readFileSync(0);
    finish(0, "synthetic archive listing\\n");
  }
  if (pgRestore) {
    readFileSync(0);
    finish(0);
  }
}
throw new Error("拒绝未建模的 Compose 操作：" + action);
`;
writeFileSync(join(fakeBin, "docker"), fakeDocker, { mode: 0o755 });

const fakeGit = `#!${process.execPath}
const args = process.argv.slice(2);
const mode = process.env.LAB_PRODUCTION_TEST_GIT_MODE || "clean";
const finish = (code = 0, output = "") => { process.stdout.write(output); process.exit(code); };
if (args.at(-1) === "--is-inside-work-tree") finish(0, "true\\n");
if (args.includes("status") && args.includes("--porcelain=v1")) finish(0, mode === "dirty" ? " M backend/src/main/java/Example.java\\n" : "");
if (args.includes("rev-parse") && args.includes("HEAD")) finish(0, "0123456789abcdef0123456789abcdef01234567\\n");
finish(97);
`;
writeFileSync(join(fakeBin, "git"), fakeGit, { mode: 0o755 });

/** 创建独立配置；密码仅用于测试，不来自工作区配置或外部数据源。 */
function fixture(overrides = {}) {
  const directory = mkdtempSync(join(testRoot, "case-"));
  const values = {
    MATERIALS_LAB_COMPOSE_PROJECT: "materials-lab-production-test",
    MATERIALS_LAB_RELEASE: "test-release-20260908",
    MATERIALS_LAB_DATA_ROOT: join(directory, "work/data/labAssistant"),
    MATERIALS_LAB_SECRETS_DIR: join(directory, "work/server/labAssistant/data/secrets"),
    MATERIALS_LAB_DB_NAME: "materials_lab_test",
    MATERIALS_LAB_DB_USER: "materials_lab_test_app",
    MATERIALS_LAB_OBJECT_STORAGE_BUCKET: "materials-lab-test",
    MATERIALS_LAB_PUBLIC_HOST: "198.51.100.10",
    MATERIALS_LAB_HTTP_BIND_ADDRESS: "0.0.0.0",
    MATERIALS_LAB_HTTP_BIND_PORT: "15105",
    MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_CODE: "TEST_ORGANIZATION",
    MATERIALS_LAB_BOOTSTRAP_ORGANIZATION_NAME: "隔离测试组织",
    MATERIALS_LAB_BOOTSTRAP_ADMIN_USERNAME: "fixture_admin",
    MATERIALS_LAB_BOOTSTRAP_ADMIN_DISPLAY_NAME: "隔离测试管理员",
    ...overrides,
  };
  const envFile = join(directory, ".env.production");
  const content = Object.entries(values).map(([key, value]) => `${key}=${value}`).join("\n") + "\n";
  writeFileSync(envFile, content, { mode: 0o600 });
  return { directory, values, envFile, content, logFile: join(directory, "docker.jsonl"), stateFile: join(directory, "docker-state.json") };
}

/** 执行真实运维脚本，但 PATH 首项始终为不能透传命令的 Docker 替身。 */
function run(f, args, extraEnvironment = {}) {
  const environment = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith("MATERIALS_LAB_")));
  const result = spawnSync("bash", [script, ...args], {
    cwd: repository,
    encoding: "utf8",
    timeout: 15_000,
    maxBuffer: 1024 * 1024,
    env: {
      ...environment,
      HOME: f.directory,
      PATH: `${fakeBin}:${process.env.PATH}`,
      LAB_PRODUCTION_TEST_DOCKER_LOG: f.logFile,
      LAB_PRODUCTION_TEST_DOCKER_STATE: f.stateFile,
      ...extraEnvironment,
    },
  });
  assert.ifError(result.error);
  return { ...result, output: `${result.stdout}${result.stderr}` };
}

function command(f, action, args = [], extraEnvironment = {}) {
  return run(f, ["--env", f.envFile, action, ...args], extraEnvironment);
}

function calls(f) {
  return existsSync(f.logFile) ? readFileSync(f.logFile, "utf8").trim().split("\n").filter(Boolean).map(JSON.parse) : [];
}

function composeAction(call) {
  if (call[0] !== "compose") return null;
  let offset = 1;
  while (["--project-directory", "--env-file", "-p", "-f"].includes(call[offset])) offset += 2;
  return { action: call[offset], args: call.slice(offset + 1) };
}

function deniedBeforeDocker(f, result, message) {
  assert.notEqual(result.status, 0);
  assert.match(result.output, message);
  assert.deepEqual(calls(f), [], "无效输入不得触发 Docker 命令");
}

/** 仅创建本例使用的合成密码和空持久目录，权限与生产脚本约定一致。 */
function writeReleaseManifest(f, release = f.values.MATERIALS_LAB_RELEASE) {
  writeFileSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "releases", `${release}.json`), JSON.stringify({
    release,
    git_sha: "0123456789abcdef0123456789abcdef01234567",
    api_image_id: `sha256:${"a".repeat(64)}`,
    web_image_id: `sha256:${"b".repeat(64)}`,
    built_at: "2026-09-09 12:00:00",
  }, null, 2) + "\n", { mode: 0o600 });
}

function ready(f, withReleaseManifest = true) {
  mkdirSync(f.values.MATERIALS_LAB_SECRETS_DIR, { recursive: true, mode: 0o700 });
  chmodSync(f.values.MATERIALS_LAB_SECRETS_DIR, 0o700);
  const passwords = ["SyntheticPostgresPassword!0123456789", "SyntheticApplicationPassword!0123456789", "SyntheticAdministratorPassword!0123456789"];
  for (const [index, name] of ["postgres_password", "database_password", "bootstrap_admin_password", "object_storage_access_key", "object_storage_secret_key"].entries()) {
    const value = name === "object_storage_access_key" ? "ML" + "A".repeat(48)
      : name === "object_storage_secret_key" ? "Aa1!" + "b".repeat(64) : passwords[index];
    writeFileSync(join(f.values.MATERIALS_LAB_SECRETS_DIR, name), value, { mode: 0o444 });
  }
  mkdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "postgres"), { recursive: true });
  mkdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "rustfs", "data"), { recursive: true });
  mkdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "rustfs", "logs"), { recursive: true });
  mkdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "backups"), { recursive: true });
  mkdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "releases"), { recursive: true });
  if (withReleaseManifest) writeReleaseManifest(f);
  return passwords;
}

test("--help 不需要配置，不访问 Docker", () => {
  const f = fixture();
  const result = run(f, ["--help"]);
  assert.equal(result.status, 0);
  assert.match(result.output, /用法：.*production\.sh/);
  assert.deepEqual(calls(f), []);
});

test("拒绝空配置、重复键、空值和占位符", () => {
  for (const [content, expected] of [
    ["", /请填写/],
    ["duplicate", /重复/],
    ["empty", /请填写/],
    ["placeholder", /请填写/],
  ]) {
    const f = fixture();
    const text = content === "duplicate" ? f.content + "MATERIALS_LAB_RELEASE=another\n"
      : content === "empty" ? f.content.replace("MATERIALS_LAB_RELEASE=test-release-20260908", "MATERIALS_LAB_RELEASE=")
        : content === "placeholder" ? f.content.replace("test-release-20260908", "CHANGE_ME") : "";
    writeFileSync(f.envFile, text);
    deniedBeforeDocker(f, command(f, "status"), expected);
  }
});

test("拒绝开发项目、开发数据库和可变 latest 标签", () => {
  for (const [overrides, expected] of [
    [{ MATERIALS_LAB_COMPOSE_PROJECT: "materials-lab-assistant" }, /不能复用开发项目/],
    [{ MATERIALS_LAB_COMPOSE_PROJECT: "unrelated-project" }, /独立 materials-lab-/],
    [{ MATERIALS_LAB_DB_NAME: "materials_lab_dev" }, /不能含 dev/],
    [{ MATERIALS_LAB_DB_USER: "postgres" }, /materials_lab 前缀/],
    [{ MATERIALS_LAB_RELEASE: "latest" }, /latest/],
  ]) {
    const f = fixture(overrides);
    deniedBeforeDocker(f, command(f, "status"), expected);
  }
});

test("配置不允许系统变量、Shell 语句、引号或命令替换，且不回显值", () => {
  for (const kind of ["system", "statement", "substitution", "backticks", "quoted"]) {
    const f = fixture();
    const marker = join(f.directory, "must-not-exist");
    const sentinel = "SYNTHETIC_SECRET_MUST_NOT_BE_PRINTED";
    const line = {
      system: `HOME=${sentinel}`,
      statement: `touch ${marker}`,
      substitution: `MATERIALS_LAB_EXTRA=${sentinel}$(touch ${marker})`,
      backticks: `MATERIALS_LAB_EXTRA=${sentinel}\`touch ${marker}\``,
      quoted: `MATERIALS_LAB_EXTRA="${sentinel}"`,
    }[kind];
    writeFileSync(f.envFile, f.content + `${line}\n`);
    const result = command(f, "status");
    deniedBeforeDocker(f, result, /非法赋值|不支持/);
    assert.ok(!existsSync(marker), "配置内容不得被执行");
    assert.ok(!result.output.includes(sentinel), "错误输出不得包含配置值");
  }
});

test("无效命令、未知参数与缺少操作确认均在 Docker 之前拒绝", () => {
  for (const [action, args, expected] of [
    ["destroy", [], /未知命令/],
    ...["secrets", "prepare", "preflight", "build", "status", "check"].map(action => [action, ["--unknown"], /不接受额外参数/]),
    ["backup", ["--unknown"], /--confirm/],
    ...["bootstrap", "up", "upgrade"].map(action => [action, [], /--confirm/]),
    ["up", ["--confirm", "materials-lab-other"], /--confirm/],
    ["rollback", ["old-release"], /--schema-compatible/],
    ["restore-new", [], /--confirm/],
  ]) {
    const f = fixture();
    deniedBeforeDocker(f, command(f, action, args), expected);
  }
});

test("拒绝相对配置文件、配置软链接及非项目部署路径", () => {
  const relative = fixture();
  deniedBeforeDocker(relative, run(relative, ["--env", "relative.env", "status"]), /绝对路径/);
  const linked = fixture();
  const linkFile = join(linked.directory, "linked.env");
  symlinkSync(linked.envFile, linkFile);
  deniedBeforeDocker(linked, run(linked, ["--env", linkFile, "status"]), /软链接/);
  for (const path of ["/", "/private/tmp/unrelated", "/private/tmp/work/data/labAssistant", "/private/tmp/work/data/labAssistant/../other", "/private/tmp/work/data/labAssistant//nested"]) {
    const f = fixture({ MATERIALS_LAB_DATA_ROOT: path });
    deniedBeforeDocker(f, command(f, "prepare"), /规范绝对路径|目录必须位于/);
  }
  const legacySecrets = fixture();
  const legacyPath = join(legacySecrets.directory, "work/code/labAssistant/data/production-secrets");
  writeFileSync(legacySecrets.envFile, legacySecrets.content.replace(legacySecrets.values.MATERIALS_LAB_SECRETS_DIR, legacyPath));
  deniedBeforeDocker(legacySecrets, command(legacySecrets, "prepare"), /目录必须位于/);
  const unscopedServerSecrets = fixture();
  const unscopedServerPath = join(unscopedServerSecrets.directory, "work/server/data/production-secrets");
  writeFileSync(unscopedServerSecrets.envFile, unscopedServerSecrets.content.replace(unscopedServerSecrets.values.MATERIALS_LAB_SECRETS_DIR, unscopedServerPath));
  deniedBeforeDocker(unscopedServerSecrets, command(unscopedServerSecrets, "prepare"), /目录必须位于/);
  const misplacedData = fixture();
  const misplacedPath = join(misplacedData.directory, "work/server/data/labAssistant");
  writeFileSync(misplacedData.envFile, misplacedData.content.replace(misplacedData.values.MATERIALS_LAB_DATA_ROOT, misplacedPath));
  deniedBeforeDocker(misplacedData, command(misplacedData, "prepare"), /持久化数据目录必须位于/);
});

test("拒绝部署路径中的软链接和数据密钥共用目录", () => {
  const f = fixture();
  const target = join(f.directory, "synthetic-target");
  mkdirSync(target);
  mkdirSync(dirname(f.values.MATERIALS_LAB_DATA_ROOT), { recursive: true });
  symlinkSync(target, f.values.MATERIALS_LAB_DATA_ROOT);
  deniedBeforeDocker(f, command(f, "prepare"), /不能包含软链接/);
  assert.deepEqual(readdirSync(target), []);
  const same = fixture();
  writeFileSync(same.envFile, same.content.replace(same.values.MATERIALS_LAB_SECRETS_DIR, same.values.MATERIALS_LAB_DATA_ROOT));
  deniedBeforeDocker(same, command(same, "prepare"), /不能与数据根目录相同/);
});

test("公网 HTTP 入口只接受有效的 IP 地址与端口", () => {
  for (const [overrides, expected] of [
    [{ MATERIALS_LAB_PUBLIC_HOST: "materials-lab.invalid" }, /公网 IPv4/],
    [{ MATERIALS_LAB_PUBLIC_HOST: "999.51.100.10" }, /公网 IPv4/],
    [{ MATERIALS_LAB_HTTP_BIND_ADDRESS: "localhost" }, /HTTP 绑定地址/],
    [{ MATERIALS_LAB_PUBLIC_PORT: "0" }, /公开端口/],
    [{ MATERIALS_LAB_PUBLIC_PORT: "65536" }, /公开端口/],
    [{ MATERIALS_LAB_HTTP_BIND_PORT: "65536" }, /HTTP 宿主绑定端口/],
    [{ MATERIALS_LAB_HTTP_BIND_PORT: "15106" }, /必须与公开端口一致/],
  ]) {
    const f = fixture(overrides);
    deniedBeforeDocker(f, command(f, "status"), expected);
  }
});

test("secrets 生成独立密码且不输出密码，重复运行拒绝覆盖", () => {
  const f = fixture();
  const first = command(f, "secrets");
  assert.equal(first.status, 0, first.output);
  const directory = f.values.MATERIALS_LAB_SECRETS_DIR;
  assert.equal(statSync(directory).mode & 0o777, 0o700);
  const secrets = ["postgres_password", "database_password", "bootstrap_admin_password", "object_storage_access_key", "object_storage_secret_key"].map(name => {
    const path = join(directory, name);
    assert.equal(statSync(path).mode & 0o777, 0o444);
    return readFileSync(path, "utf8");
  });
  assert.equal(new Set(secrets).size, 5);
  for (const secret of secrets) {
    assert.ok(secret.length >= 32);
    assert.ok(!first.output.includes(secret), "生成操作不得输出密码");
  }
  const second = command(f, "secrets");
  deniedBeforeDocker(f, second, /拒绝覆盖/);
  for (const [index, name] of ["postgres_password", "database_password", "bootstrap_admin_password", "object_storage_access_key", "object_storage_secret_key"].entries()) {
    assert.equal(readFileSync(join(directory, name), "utf8"), secrets[index]);
    assert.ok(!second.output.includes(secrets[index]));
  }
});

test("secrets 拒绝已有单个密码与悬空软链接，不生成其他密码", () => {
  for (const link of [false, true]) {
    const f = fixture();
    mkdirSync(f.values.MATERIALS_LAB_SECRETS_DIR, { recursive: true });
    const file = join(f.values.MATERIALS_LAB_SECRETS_DIR, "database_password");
    if (link) symlinkSync(join(f.directory, "nonexistent"), file);
    else writeFileSync(file, "SYNTHETIC_EXISTING_SECRET");
    const result = command(f, "secrets");
    deniedBeforeDocker(f, result, /拒绝覆盖/);
    assert.deepEqual(readdirSync(f.values.MATERIALS_LAB_SECRETS_DIR), ["database_password"]);
    assert.ok(!result.output.includes("SYNTHETIC_EXISTING_SECRET"));
  }
});

test("secrets 生成中途失败时不发布不完整的最终目录", () => {
  const f = fixture();
  const opensslBin = join(f.directory, "openssl-bin");
  const counter = join(f.directory, "openssl-count");
  mkdirSync(opensslBin);
  writeFileSync(join(opensslBin, "openssl"), `#!${process.execPath}\n`
    + `import { existsSync, readFileSync, writeFileSync } from "node:fs";\n`
    + `const file = process.env.LAB_PRODUCTION_TEST_OPENSSL_COUNT;\n`
    + `const count = existsSync(file) ? Number(readFileSync(file, "utf8")) + 1 : 1;\n`
    + `writeFileSync(file, String(count));\n`
    + `if (count >= 3) process.exit(29);\n`
    + `process.stdout.write("0123456789abcdef0123456789abcdef0123456789abcdef");\n`, { mode: 0o755 });
  const result = command(f, "secrets", [], {
    PATH: `${opensslBin}:${fakeBin}:${process.env.PATH}`,
    LAB_PRODUCTION_TEST_OPENSSL_COUNT: counter,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /最终目录未发布/);
  assert.ok(!existsSync(f.values.MATERIALS_LAB_SECRETS_DIR), "失败时不得暴露部分最终密钥目录");
  assert.ok(readdirSync(dirname(f.values.MATERIALS_LAB_SECRETS_DIR)).some(name => name.startsWith(".materials-lab-secrets.")), "私有暂存目录应保留供审计");
});

test("预检拒绝权限或内容不合规的管理员密码密钥", () => {
  for (const kind of ["permissions", "weak", "multiple-newlines"]) {
    const f = fixture();
    ready(f);
    const file = join(f.values.MATERIALS_LAB_SECRETS_DIR, "bootstrap_admin_password");
    if (kind === "permissions") {
      chmodSync(file, 0o400);
    } else {
      chmodSync(file, 0o600);
      const password = kind === "weak" ? "weak-password" : "StrongAdministrator1!\n\n";
      writeFileSync(file, password);
      chmodSync(file, 0o444);
    }
    const result = command(f, "preflight");
    assert.notEqual(result.status, 0);
    assert.match(result.output, /管理员密码|bootstrap_admin_password/);
    assert.ok(!calls(f).map(composeAction).some(call => call?.action === "config"));
  }
});

test("预检接受应用支持的管理员密码单个 CRLF 行尾", () => {
  const f = fixture();
  ready(f);
  const file = join(f.values.MATERIALS_LAB_SECRETS_DIR, "bootstrap_admin_password");
  chmodSync(file, 0o600);
  writeFileSync(file, "admin@123!\r\n");
  chmodSync(file, 0o444);
  const result = command(f, "preflight");
  assert.equal(result.status, 0, result.output);
});

test("预检拒绝与首次管理员用户名相同的密码", () => {
  const f = fixture({ MATERIALS_LAB_BOOTSTRAP_ADMIN_USERNAME: "admin123" });
  ready(f);
  const file = join(f.values.MATERIALS_LAB_SECRETS_DIR, "bootstrap_admin_password");
  chmodSync(file, 0o600);
  writeFileSync(file, "admin123");
  chmodSync(file, 0o444);
  const result = command(f, "preflight");
  assert.notEqual(result.status, 0);
  assert.match(result.output, /不得与管理员用户名相同/);
  assert.ok(!calls(f).map(composeAction).some(call => call?.action === "config"));
});

test("预检仅执行引擎和 Compose 配置检查，显式使用所选项目与配置", () => {
  const f = fixture();
  const passwords = ready(f);
  const result = command(f, "preflight", [], { MATERIALS_LAB_COMPOSE_PROJECT: "materials-lab-untrusted-override" });
  assert.equal(result.status, 0, result.output);
  const invoked = calls(f);
  assert.deepEqual(invoked[0], ["info"]);
  assert.equal(invoked.length, 4);
  assert.deepEqual(invoked[1], ["image", "inspect", "postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15"]);
  assert.deepEqual(invoked[2], ["image", "inspect", "rustfs/rustfs:v1.0.0-rc.5@sha256:b7014e0ce2bc703c1316b3ef760e29dfae61fe4a50d1a66fa89638e0f8ea211f"]);
  assert.deepEqual(composeAction(invoked[3]), { action: "config", args: ["--quiet"] });
  assert.equal(invoked[3][invoked[3].indexOf("-p") + 1], f.values.MATERIALS_LAB_COMPOSE_PROJECT);
  assert.equal(invoked[3][invoked[3].indexOf("--env-file") + 1], f.envFile);
  for (const password of passwords) assert.ok(!result.output.includes(password));
});

test("prepare 创建 PostgreSQL、RustFS 子目录、备份和发布清单目录", () => {
  const f = fixture();

  const result = command(f, "prepare");

  assert.equal(result.status, 0, result.output);
  for (const directory of ["postgres", "rustfs/data", "rustfs/logs", "backups", "releases"]) {
    assert.ok(existsSync(join(f.values.MATERIALS_LAB_DATA_ROOT, directory)), `prepare 必须创建 ${directory}`);
  }
  assert.deepEqual(calls(f), [], "准备目录不得调用 Docker");
});

test("预检保留引擎失败和配置失败，不触发启动", () => {
  for (const mode of ["engine-fails", "config-fails"]) {
    const f = fixture();
    ready(f);
    const result = command(f, "preflight", [], { LAB_PRODUCTION_TEST_DOCKER_MODE: mode });
    assert.notEqual(result.status, 0);
    assert.ok(calls(f).every(call => call[0] === "info" || (call[0] === "image" && call[1] === "inspect") || composeAction(call)?.action === "config"));
  }
});

test("预检拒绝不能解析锁定摘要的 PostgreSQL 镜像", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "preflight", [], { LAB_PRODUCTION_TEST_DOCKER_MODE: "missing-postgres" });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /锁定摘要/);
  assert.ok(!calls(f).map(composeAction).some(call => call?.action === "config"));
});

test("构建只调用 Dockerfile 生成新标签，运行时 Compose 不含源码构建", () => {
  const f = fixture();
  ready(f, false);
  const result = command(f, "build", [], { LAB_PRODUCTION_TEST_DOCKER_MODE: "build-images-absent" });
  assert.equal(result.status, 0, result.output);
  const invoked = calls(f);
  const directBuilds = invoked.filter(call => call[0] === "build");
  assert.deepEqual(directBuilds, [
    ["build", "--file", join(repository, "infra/Dockerfile.api"), "--tag", `materials-lab-api:${f.values.MATERIALS_LAB_RELEASE}`, repository],
    ["build", "--file", join(repository, "infra/Dockerfile.web"), "--tag", `materials-lab-web:${f.values.MATERIALS_LAB_RELEASE}`, repository],
  ]);
  assert.ok(!invoked.map(composeAction).some(call => call?.action === "build"));
  assert.match(result.output, /发布清单已生成/);
  const manifest = JSON.parse(readFileSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "releases", `${f.values.MATERIALS_LAB_RELEASE}.json`), "utf8"));
  assert.deepEqual(manifest, {
    release: f.values.MATERIALS_LAB_RELEASE,
    git_sha: "0123456789abcdef0123456789abcdef01234567",
    api_image_id: `sha256:${"a".repeat(64)}`,
    web_image_id: `sha256:${"b".repeat(64)}`,
    built_at: manifest.built_at,
  });
  assert.match(manifest.built_at, /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
});

test("构建拒绝脏工作树，不调用 Docker 构建", () => {
  const f = fixture();
  ready(f, false);
  const result = command(f, "build", [], {
    LAB_PRODUCTION_TEST_DOCKER_MODE: "build-images-absent",
    LAB_PRODUCTION_TEST_GIT_MODE: "dirty",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /工作树存在已修改/);
  assert.ok(!calls(f).some(call => call[0] === "build"), "脏工作树不得触发 Docker 构建");
});

test("up 拒绝缺失镜像、已运行写入口和开发数据哨兵", () => {
  for (const [mode, message] of [["missing-images", /镜像不齐|锁定摘要/], ["running", /仍运行|未完全停止/], ["seeded", /DEV_TEST/]]) {
    const f = fixture();
    ready(f);
    const result = command(f, "up", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT], { LAB_PRODUCTION_TEST_DOCKER_MODE: mode });
    assert.notEqual(result.status, 0);
    assert.match(result.output, message);
    assert.ok(!calls(f).map(composeAction).some(call => call?.action === "up" && call.args.includes("api")));
  }
});

test("启动前拒绝缺失或与本机镜像不一致的发布清单", () => {
  const missing = fixture();
  ready(missing, false);
  const missingResult = command(missing, "up", ["--confirm", missing.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.notEqual(missingResult.status, 0);
  assert.match(missingResult.output, /缺少受控发布清单/);
  assert.ok(!calls(missing).map(composeAction).some(call => call?.action === "ps"), "缺少清单不得读取或变更运行容器");

  const mismatch = fixture();
  ready(mismatch);
  const manifestPath = join(mismatch.values.MATERIALS_LAB_DATA_ROOT, "releases", `${mismatch.values.MATERIALS_LAB_RELEASE}.json`);
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  manifest.api_image_id = `sha256:${"f".repeat(64)}`;
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
  const mismatchResult = command(mismatch, "up", ["--confirm", mismatch.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.notEqual(mismatchResult.status, 0);
  assert.match(mismatchResult.output, /镜像 ID 与发布清单不匹配/);
  assert.ok(!calls(mismatch).map(composeAction).some(call => call?.action === "ps"), "镜像不匹配不得读取或变更运行容器");
});

test("首次初始化先等待 PostgreSQL 与 RustFS 健康，再运行无依赖初始化任务", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "bootstrap", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.equal(result.status, 0, result.output);
  const actions = calls(f).map(composeAction).filter(Boolean);
  const dependencyStart = actions.find(call => call.action === "up" && call.args.includes("postgres") && call.args.includes("rustfs"));
  assert.ok(dependencyStart);
  assert.ok(dependencyStart.args.includes("--wait"));
  assert.ok(actions.some(call => call.action === "run" && call.args.includes("--no-deps") && call.args.at(-1) === "bootstrap"));
  assert.ok(!actions.some(call => call.action === "up" && call.args.includes("api")), "初始化不得启动 API/Web 写入口");
});

test("读取容器列表或 inspect 失败时拒绝继续，不把读取失败当作已停止", () => {
  for (const mode of ["ps-fails", "inspect-fails"]) {
    const f = fixture();
    ready(f);
    const result = command(f, "up", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT], { LAB_PRODUCTION_TEST_DOCKER_MODE: mode });
    assert.notEqual(result.status, 0, mode);
    const invoked = calls(f);
    assert.ok(invoked.some(call => composeAction(call)?.action === "ps"));
    if (mode === "inspect-fails") assert.ok(invoked.some(call => call[0] === "inspect"));
    else assert.match(result.output, /无法读取目标容器状态/);
    assert.ok(!invoked.map(composeAction).some(call => call && ["up", "run", "stop", "exec"].includes(call.action)), "状态未知时不得启动、停写或访问数据库");
  }
});

test("up、upgrade、rollback 启动失败后停止写入口，并保留原退出码", () => {
  for (const action of ["up", "upgrade", "rollback"]) {
    const f = fixture();
    const passwords = ready(f);
    const args = action === "rollback" ? ["previous-release", "--schema-compatible", "--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT] : ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT];
    if (action === "rollback") writeReleaseManifest(f, "previous-release");
    const result = command(f, action, args, { LAB_PRODUCTION_TEST_DOCKER_MODE: "api-up-fails" });
    assert.equal(result.status, 42, `${action}: ${result.output}`);
    assert.match(result.output, /上线失败.*停止.*写入口/);
    const actions = calls(f).map(composeAction).filter(Boolean);
    assert.deepEqual(actions.at(-1), { action: "stop", args: ["web", "api"] });
    assert.equal(actions.filter(call => call.action === "stop").length, action === "up" ? 1 : 2);
    assert.ok(!actions.some(call => ["down", "rm", "restart"].includes(call.action)));
    for (const password of passwords) assert.ok(!result.output.includes(password));
  }
});

test("启动后数据检查失败仍停写；停写本身失败给出人工警告且不吞原错误", () => {
  const postStart = fixture();
  ready(postStart);
  const checkFailure = command(postStart, "up", ["--confirm", postStart.values.MATERIALS_LAB_COMPOSE_PROJECT], { LAB_PRODUCTION_TEST_DOCKER_MODE: "post-start-data-fails" });
  assert.notEqual(checkFailure.status, 0);
  assert.match(checkFailure.output, /DEV_TEST/);
  assert.deepEqual(calls(postStart).map(composeAction).filter(Boolean).at(-1), { action: "stop", args: ["web", "api"] });
  const stopFailure = fixture();
  ready(stopFailure);
  const result = command(stopFailure, "up", ["--confirm", stopFailure.values.MATERIALS_LAB_COMPOSE_PROJECT], {
    LAB_PRODUCTION_TEST_DOCKER_MODE: "api-up-fails",
    LAB_PRODUCTION_TEST_STOP_FAILURE: "true",
  });
  assert.equal(result.status, 42);
  assert.match(result.output, /自动停写失败.*人工/);
});

test("升级备份失败保留 partial，不启动新版本并再次确保停写", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "upgrade", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT], { LAB_PRODUCTION_TEST_DOCKER_MODE: "backup-fails" });
  assert.equal(result.status, 37);
  const actions = calls(f).map(composeAction).filter(Boolean);
  assert.ok(!actions.some(call => call.action === "up"));
  assert.deepEqual(actions.at(-1), { action: "stop", args: ["web", "api"] });
  const archives = readdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "backups"));
  assert.equal(archives.length, 2);
  assert.ok(archives.some(name => name.endsWith(".dump.partial")));
  assert.ok(archives.some(name => name.endsWith(".dump.metadata.txt.partial")));
  assert.ok(!archives.some(name => name.endsWith(".dump") || name.endsWith(".sha256")));
  assert.doesNotMatch(result.output, /备份完成/);
});

test("升级在停写前拒绝不满足全局登录名唯一约束的重复用户名", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "upgrade", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT], {
    LAB_PRODUCTION_TEST_DOCKER_MODE: "duplicate-usernames",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /跨组织重复用户名/);
  const actions = calls(f).map(composeAction).filter(Boolean);
  assert.ok(actions.some(call => call.action === "exec" && call.args.includes("postgres")), "必须先只读预检数据库");
  assert.ok(!actions.some(call => ["stop", "up"].includes(call.action)), "重复用户名时不得停写或启动新版本");
  assert.equal(readdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "backups")).length, 0, "预检失败前不得生成备份");
});

test("升级停写后会再次核验重复用户名，避免并发写入进入不兼容结构", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "upgrade", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT], {
    LAB_PRODUCTION_TEST_DOCKER_MODE: "duplicate-usernames-after-stop",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /跨组织重复用户名/);
  const actions = calls(f).map(composeAction).filter(Boolean);
  assert.equal(actions.filter(call => call.action === "stop").length, 2, "停写后的预检失败应保持写入口关闭");
  assert.ok(!actions.some(call => call.action === "up"), "第二次预检失败不得进入新版本启动");
  assert.equal(readdirSync(join(f.values.MATERIALS_LAB_DATA_ROOT, "backups")).length, 0, "第二次预检失败前不得生成备份");
});

test("成功备份生成完整归档和校验文件，中文日志不会误读变量边界", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "backup", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /恢复组备份完成：.*\.dump 与 .*\.rustfs-data\.tar\.gz（Asia\/Shanghai/);
  assert.doesNotMatch(result.output, /unbound variable/);
  const directory = join(f.values.MATERIALS_LAB_DATA_ROOT, "backups");
  const archives = readdirSync(directory);
  const dump = archives.find(name => name.endsWith(".dump"));
  assert.ok(dump);
  const storage = dump.replace(/\.dump$/, ".rustfs-data.tar.gz");
  assert.deepEqual(archives.sort(), [dump, `${dump}.sha256`, `${dump}.metadata.txt`, storage, `${storage}.sha256`].sort());
  assert.match(readFileSync(join(directory, `${dump}.sha256`), "utf8"), /^[a-f0-9]{64}\n$/);
  assert.match(readFileSync(join(directory, `${storage}.sha256`), "utf8"), /^[a-f0-9]{64}\n$/);
  assert.match(readFileSync(join(directory, `${dump}.metadata.txt`), "utf8"), /^recovery_unit=postgres_dump\+rustfs_data$/m);
  assert.match(readFileSync(join(directory, `${dump}.metadata.txt`), "utf8"), /^schema_profile=materials-lab-first-production-v1$/m);
});

test("空目标恢复同时校验并还原 RustFS 数据归档", () => {
  const source = fixture();
  ready(source);
  const sourceObject = join(source.values.MATERIALS_LAB_DATA_ROOT, "rustfs", "data", "fixture-object.bin");
  writeFileSync(sourceObject, "synthetic-rustfs-object");
  const backup = command(source, "backup", ["--confirm", source.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.equal(backup.status, 0, backup.output);
  const dump = readdirSync(join(source.values.MATERIALS_LAB_DATA_ROOT, "backups")).find(name => name.endsWith(".dump"));
  assert.ok(dump);

  const target = fixture({ MATERIALS_LAB_COMPOSE_PROJECT: "materials-lab-restore-test" });
  ready(target);
  const restore = command(target, "restore-new", [
    join(source.values.MATERIALS_LAB_DATA_ROOT, "backups", dump),
    "--confirm", target.values.MATERIALS_LAB_COMPOSE_PROJECT,
  ]);
  assert.equal(restore.status, 0, restore.output);
  assert.equal(readFileSync(join(target.values.MATERIALS_LAB_DATA_ROOT, "rustfs", "data", "fixture-object.bin"), "utf8"), "synthetic-rustfs-object");
  assert.match(restore.output, /数据库及 RustFS 数据目录/);
});

test("恢复在写入目标数据库前拒绝不兼容的恢复组架构标识", () => {
  const source = fixture();
  ready(source);
  const backup = command(source, "backup", ["--confirm", source.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.equal(backup.status, 0, backup.output);
  const backupDirectory = join(source.values.MATERIALS_LAB_DATA_ROOT, "backups");
  const dump = readdirSync(backupDirectory).find(name => name.endsWith(".dump"));
  const metadata = join(backupDirectory, `${dump}.metadata.txt`);
  writeFileSync(metadata, readFileSync(metadata, "utf8").replace(/^schema_profile=.*\n/m, ""));

  const target = fixture({ MATERIALS_LAB_COMPOSE_PROJECT: "materials-lab-restore-incompatible" });
  ready(target);
  const restore = command(target, "restore-new", [
    join(backupDirectory, dump), "--confirm", target.values.MATERIALS_LAB_COMPOSE_PROJECT,
  ]);
  assert.notEqual(restore.status, 0);
  assert.match(restore.output, /架构不兼容/);
  assert.ok(!calls(target).map(composeAction).some(call => call?.action === "up" || call?.action === "exec"), "不兼容恢复组不得写入目标数据库");
});

test("升级和回退备份记录实际旧容器镜像与 Flyway 历史，不用目标标签冒充来源", () => {
  for (const action of ["upgrade", "rollback"]) {
    const f = fixture();
    const passwords = ready(f);
    const args = action === "rollback" ? ["rollback-target-release", "--schema-compatible", "--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT]
      : ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT];
    if (action === "rollback") writeReleaseManifest(f, "rollback-target-release");
    const result = command(f, action, args);
    assert.equal(result.status, 0, result.output);
    const directory = join(f.values.MATERIALS_LAB_DATA_ROOT, "backups");
    const metadataName = readdirSync(directory).find(name => name.endsWith(".dump.metadata.txt"));
    assert.ok(metadataName);
    assert.ok(!metadataName.includes(f.values.MATERIALS_LAB_RELEASE));
    assert.ok(!metadataName.includes("rollback-target-release"));
    const metadata = readFileSync(join(directory, metadataName), "utf8");
    assert.match(metadata, /^backup_started_at=\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/m);
    assert.match(metadata, /^timezone=Asia\/Shanghai$/m);
    assert.match(metadata, /^compose_project=materials-lab-production-test$/m);
    for (const [service, digest] of [["api", "a"], ["web", "b"], ["postgres", "c"], ["rustfs", "d"]]) {
      const image = service === "postgres" ? "postgres:18.4-alpine" : service === "rustfs" ? "rustfs/rustfs:v1.0.0-rc.5" : `materials-lab-${service}:source-release-20260801`;
      assert.ok(metadata.includes(`${service}_source_image=${image} sha256:${digest.repeat(64)}`));
    }
    assert.match(metadata, /flyway_history_version_script_checksum_success:\n1\|V1__materials_lab_schema\.sql\|111\|t/);
    assert.ok(!metadata.includes(f.values.MATERIALS_LAB_RELEASE));
    assert.ok(!metadata.includes("rollback-target-release"));
    for (const password of passwords) assert.ok(!metadata.includes(password));
    const sourceInspects = calls(f).filter(call => call[0] === "inspect" && call[2] === "{{.Config.Image}} {{.Image}}");
    assert.equal(sourceInspects.length, 4, "必须实际读取四个来源容器的镜像标签和镜像 ID");
  }
});

test("成功 up 撤销失败处理，不误停已经通过检查的服务", () => {
  const f = fixture();
  ready(f);
  const result = command(f, "up", ["--confirm", f.values.MATERIALS_LAB_COMPOSE_PROJECT]);
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /仍需通过公网 HTTP 地址完成登录和业务验收/);
  assert.ok(!calls(f).map(composeAction).some(call => call?.action === "stop"));
});

/** 只抽取固定顶层服务块以校验文本模型，不用替身结果冒充 Compose 的 YAML 解析。 */
function yamlBlock(source, name, indent = 2) {
  const marker = `${" ".repeat(indent)}${name}:`;
  const lines = source.split("\n");
  const start = lines.findIndex(line => line === marker);
  assert.notEqual(start, -1, `缺少配置块 ${name}`);
  let end = start + 1;
  while (end < lines.length && (lines[end].trim() === "" || lines[end].startsWith(" ".repeat(indent + 1)) || lines[end].trimStart().startsWith("#"))) end += 1;
  return lines.slice(start, end).join("\n");
}

test("生产 Compose 静态模型：固定 prod、只读应用、独立非公开数据库与文件密钥", () => {
  const source = readFileSync(join(repository, "infra/compose.production.yml"), "utf8");
  const productionProfile = readFileSync(join(repository, "backend/src/main/resources/application-prod.yml"), "utf8");
  const services = yamlBlock(source, "services", 0);
  const api = yamlBlock(services, "api");
  const postgres = yamlBlock(services, "postgres");
  const web = yamlBlock(services, "web");
  const bootstrap = yamlBlock(services, "bootstrap");
  assert.deepEqual([...services.matchAll(/^  ([a-z][a-z_-]*):$/gm)].map(match => match[1]), ["postgres", "api", "web", "rustfs-permissions", "rustfs", "bootstrap"]);
  assert.match(source, /SPRING_PROFILES_ACTIVE: prod/);
  assert.match(source, /SPRING_CONFIG_IMPORT: configtree:\/run\/secrets\//);
  assert.match(source, /SESSION_COOKIE_SECURE: "false"/);
  assert.doesNotMatch(source, /POSTGRES_PROD_PASSWORD:|POSTGRES_PASSWORD:|docker\.sock|profiles=dev|:latest\b/);
  assert.match(productionProfile, /url: \$\{JDBC_DATABASE_URL\}/);
  assert.match(productionProfile, /username: \$\{POSTGRES_PROD_USER\}/);
  assert.doesNotMatch(productionProfile, /JDBC_DATABASE_URL:|POSTGRES_PROD_USER:/);
  for (const service of [api, web, bootstrap]) {
    assert.match(service, /read_only: true/);
    assert.match(service, /cap_drop: \[ALL\]/);
    assert.match(service, /no-new-privileges:true/);
    // 含逗号的 tmpfs 参数必须是一个带引号的流式数组元素，否则 YAML 会拆成两个挂载。
    const tmpfsLine = service.split("\n").find(line => line.trimStart().startsWith("tmpfs:"));
    assert.ok(tmpfsLine);
    const mounts = JSON.parse(tmpfsLine.slice(tmpfsLine.indexOf(":") + 1).trim());
    assert.equal(mounts.length, 1);
    assert.match(mounts[0], /^\/tmp:size=\d+m,mode=1777$/);
  }
  assert.doesNotMatch(api, /^    ports:/m);
  assert.doesNotMatch(postgres, /^    ports:/m);
  assert.doesNotMatch(api, /^    build:/m);
  assert.doesNotMatch(web, /^    build:/m);
  assert.match(postgres, /image: postgres:18\.4-alpine/);
  assert.match(postgres, /POSTGRES_USER: postgres/);
  assert.match(postgres, /POSTGRES_PASSWORD_FILE: \/run\/secrets\/postgres_password/);
  assert.match(postgres, /target: \/var\/lib\/postgresql\n/);
  assert.match(source, /  database:\n    internal: true/);
  assert.match(api, /target: spring\.datasource\.password/);
  assert.match(web, /MATERIALS_LAB_HTTP_BIND_ADDRESS:-0\.0\.0\.0/);
  assert.match(bootstrap, /profiles: \[maintenance\]/);
  assert.match(bootstrap, /ProductionBootstrapApplication/);
  assert.match(bootstrap, /networks: \[database, storage\]/);
  assert.match(bootstrap, /restart: "no"/);
  const rustfs = yamlBlock(services, "rustfs");
  assert.match(rustfs, /rustfs\/rustfs:v1\.0\.0-rc\.5@sha256:b7014e0ce2bc703c1316b3ef760e29dfae61fe4a50d1a66fa89638e0f8ea211f/);
  assert.match(rustfs, /\/health\/ready/);
  assert.match(api, /rustfs:\n        condition: service_healthy/);
  assert.match(bootstrap, /rustfs:\n        condition: service_healthy/);
  const developmentCompose = readFileSync(join(repository, "infra/docker-compose.yml"), "utf8");
  assert.doesNotMatch(developmentCompose, /^  redis:/m);
  assert.doesNotMatch(developmentCompose, /REDIS_/);
});

test("生产 Compose 仅发布明确的公网 HTTP Web 端口", () => {
  const source = readFileSync(join(repository, "infra/compose.production.yml"), "utf8");
  assert.match(source, /MATERIALS_LAB_HTTP_BIND_ADDRESS:-0\.0\.0\.0/);
  assert.match(source, /MATERIALS_LAB_HTTP_BIND_PORT:-15105/);
  assert.match(source, /:8080/);
  assert.doesNotMatch(source, /8443|tls\/fullchain|privkey/);
});

test("生产 Nginx 使用同源 CSP，避免前端运行时第三方资源", () => {
  const source = readFileSync(join(repository, "infra/nginx/locations.conf.template"), "utf8");
  assert.match(source, /Content-Security-Policy/);
  assert.match(source, /connect-src 'self'/);
  assert.match(source, /script-src 'self'/);
});

test("隔离生产验收由 Docker 分配并回读 HTTP 端口", () => {
  const source = readFileSync(join(repository, "scripts/tests/production-acceptance.mjs"), "utf8");
  assert.match(source, /HTTP_BIND_PORT: '0'/);
  assert.match(source, /'port', 'web', '8080'/);
  assert.match(source, /--manifest/);
  assert.match(source, /copyReleaseManifest/);
  assert.match(source, /work', 'data', 'labAssistant'/);
  assert.doesNotMatch(source, /git', \['rev-parse'/);
  assert.doesNotMatch(source, /server\.listen\(0|function availablePort/);
});

after(() => {
  console.log(`隔离运维测试夹具已保留：${testRoot}；未调用真实 Docker 或清理资源。`);
});

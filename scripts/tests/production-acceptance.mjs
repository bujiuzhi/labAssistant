#!/usr/bin/env node
/**
 * 生产 Compose 隔离验收：HTTP 身份初始化、会话、上传下载与异库恢复。
 * 用法：node scripts/tests/production-acceptance.mjs --release 已构建同标签 --manifest 发布清单绝对路径
 * 仅创建 materials-lab-acceptance-* 项目，保留所有容器与 data/acceptance-* 数据供审查。
 * 不读取现有项目配置、不复用生产库、不删除容器或数据；故障退出非零。
 */
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { lstat, mkdir, readFile, readdir, realpath, writeFile } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const argumentsList = process.argv.slice(2);
if (argumentsList.length === 1 && ['--help', '-h'].includes(argumentsList[0])) {
  console.log('用法：node scripts/tests/production-acceptance.mjs --release 已构建同标签 --manifest 发布清单绝对路径');
  console.log('要求 Node.js 24、Docker Compose、openssl；保留隔离验收容器与数据，不自动清理。');
  process.exit(0);
}
assert.equal(argumentsList.length, 4, '必须提供 --release 标签和 --manifest 发布清单绝对路径');
assert.equal(argumentsList[0], '--release', '必须显式选择同标签 API/Web 镜像');
const release = argumentsList[1];
assert.equal(argumentsList[2], '--manifest', '必须显式提供发布清单');
const manifestInput = argumentsList[3];
assert.match(release, /^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$/, '发布标签格式无效');
assert.notEqual(release, 'latest', '验收不允许 latest 标签');
assert.ok(path.isAbsolute(manifestInput), '发布清单必须使用绝对路径');
assert.ok(Number(process.versions.node.split('.')[0]) >= 24, '需要 Node.js 24 或更新版本');

const repository = await realpath(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'));
const homeDirectory = process.env.HOME;
assert.ok(homeDirectory && path.isAbsolute(homeDirectory), '验收需要有效的 HOME 目录以隔离持久数据');
const manifestStat = await lstat(manifestInput);
assert.ok(manifestStat.isFile() && !manifestStat.isSymbolicLink(), '发布清单必须是普通文件，不能使用软链接');
const manifestPath = await realpath(manifestInput);
const releaseManifestRaw = await readFile(manifestPath, 'utf8');
const releaseManifest = JSON.parse(releaseManifestRaw);
assert.equal(releaseManifest.release, release, '发布清单标签必须与验收镜像标签一致');
assert.match(releaseManifest.git_sha, /^[0-9a-f]{40}$/i, '发布清单 Git SHA 无效');
assert.match(releaseManifest.api_image_id, /^sha256:[0-9a-f]{64}$/i, '发布清单 API 镜像 ID 无效');
assert.match(releaseManifest.web_image_id, /^sha256:[0-9a-f]{64}$/i, '发布清单 Web 镜像 ID 无效');
const runId = randomBytes(6).toString('hex');
const runDirectory = path.join(homeDirectory, 'work', 'data', 'labAssistant', `acceptance-${runId}`);
const privateRunDirectory = path.join(repository, 'data', `acceptance-${runId}`);
const summaryPath = path.join(runDirectory, 'summary.json');
const summary = {
  status: 'running', release, timezone: 'Asia/Shanghai',
  started_at: timestamp(), current_stage: '准备隔离目录',
  isolation: '测试身份、项目和文档仅存在本次 materials-lab-acceptance-* 隔离项目中',
  retained: true, directory: runDirectory, projects: [], checks: [],
};

/** 人工记录使用上海时间，明确到秒，不记录任何凭据。 */
function timestamp() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date());
}

/** 只输出阶段与退出状态；子进程正文可能含连接上下文，始终不写日志或验收摘要。 */
function command(program, args, label, { input, expectFailure = false } = {}) {
  summary.current_stage = label;
  console.log(`[验收] ${label}`);
  const result = spawnSync(program, args, {
    cwd: repository, encoding: 'utf8', input, timeout: 300_000,
    maxBuffer: 16 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'],
    env: Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('MATERIALS_LAB_'))),
  });
  if (result.error) throw new Error(`${label} 无法完成（${result.error.code ?? '子进程错误'}）`);
  if (expectFailure) {
    assert.ok(Number.isInteger(result.status) && result.status !== 0, `${label} 应被拒绝且退出非零`);
  } else {
    assert.equal(result.status, 0, `${label} 失败，退出码 ${result.status ?? result.signal}`);
  }
  return result.stdout.trim();
}

/** 每次只调用既有生产运维入口，变更使用当前隔离 Compose 项目明确确认。 */
function operation(target, action, extras = [], expectFailure = false) {
  const args = ['scripts/production.sh', '--env', target.environment, action, ...extras];
  if (['bootstrap', 'up', 'restore-new'].includes(action)) args.push('--confirm', target.project);
  return command('bash', args, `${target.label} ${action}`, { expectFailure });
}

/** 隔离目标仅复制构建交付的清单，不能用当前源码或本地标签临时伪造。 */
async function copyReleaseManifest(target) {
  await writeFile(path.join(target.dataRoot, 'releases', `${release}.json`), releaseManifestRaw,
    { flag: 'wx', mode: 0o600 });
}

/** SQL 仅通过本次项目容器的本地管理连接执行，不读取或输出数据库密码。 */
function query(target, sql) {
  return command('docker', [
    'compose', '--project-directory', path.join(repository, 'infra'), '--env-file', target.environment,
    '-p', target.project, '-f', path.join(repository, 'infra/compose.production.yml'), 'exec', '-T', 'postgres',
    'sh', '-ec', 'exec psql -X -v ON_ERROR_STOP=1 -U postgres -d "$MATERIALS_LAB_DB_NAME" -At',
  ], `${target.label} 只读数据库断言`, { input: sql });
}

function counts(target) {
  return JSON.parse(query(target, `SELECT json_build_object(
    'organization', (SELECT count(*) FROM organization),
    'users', (SELECT count(*) FROM user_account),
    'admins', (SELECT count(*) FROM user_account WHERE is_super_admin AND is_active),
    'roles', (SELECT count(*) FROM role),
    'permissions', (SELECT count(*) FROM permission),
    'project', (SELECT count(*) FROM project),
    'experiment', (SELECT count(*) FROM experiment),
    'document', (SELECT count(*) FROM project_document),
    'content', (SELECT count(*) FROM project_document_content),
    'development_organization', (SELECT count(*) FROM organization WHERE lower(organization_code)='dev_test'));`));
}

/** 指纹只在进程内比较，既不导出密码散列，也不写入摘要。 */
function identityFingerprint(target) {
  const tables = ['organization', 'user_account', 'role', 'permission', 'user_role', 'role_permission'];
  return query(target, `SELECT ${tables.map(table =>
    `(SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '' ORDER BY t.id), '')) FROM ${table} t)`).join(" || '|' || ")};`);
}

function composeArguments(target, ...args) {
  return [
    'compose', '--project-directory', path.join(repository, 'infra'), '--env-file', target.environment,
    '-p', target.project, '-f', path.join(repository, 'infra/compose.production.yml'), ...args,
  ];
}

/** 由 Docker 原子分配宿主端口，容器启动后读取实际映射，避免探测后释放产生竞态。 */
function resolvePublishedPort(target) {
  const output = command('docker', composeArguments(target, 'port', 'web', '8080'), `${target.label} 读取动态 HTTP 端口`);
  const matches = output.split('\n').map(line => line.trim().match(/:(\d+)$/)).filter(Boolean);
  assert.equal(matches.length, 1, 'Web 必须且只能发布一个 HTTP 宿主端口');
  target.port = Number(matches[0][1]);
  assert.ok(Number.isInteger(target.port) && target.port >= 1 && target.port <= 65535, 'Docker 返回的 HTTP 端口无效');
  target.summary.url = `http://127.0.0.1:${target.port}`;
}

/** 每个目标拥有独立数据库和密钥目录；从不读取用户已有 .env。 */
async function prepareTarget(label) {
  const dataRoot = path.join(runDirectory, label);
  const privateRoot = path.join(privateRunDirectory, label);
  const target = {
    label, dataRoot, privateRoot, project: `materials-lab-acceptance-${runId}-${label}`,
    environment: path.join(privateRoot, '.env.production'), port: undefined,
    secrets: path.join(privateRoot, 'secrets'),
  };
  assert.match(target.project, /^materials-lab-acceptance-[a-f0-9]+-(source|restore)$/);
  await mkdir(dataRoot, { recursive: false, mode: 0o700 });
  await mkdir(privateRoot, { recursive: false, mode: 0o700 });
  const config = {
    COMPOSE_PROJECT: target.project, RELEASE: release, DATA_ROOT: target.dataRoot,
    SECRETS_DIR: target.secrets, DB_NAME: `materials_lab_acceptance_${runId}_${label}`,
    DB_USER: `materials_lab_acceptance_${runId}_${label}`, DB_POOL_SIZE: '5',
    PUBLIC_HOST: '127.0.0.1', PUBLIC_PORT: '15105', HTTP_BIND_ADDRESS: '127.0.0.1', HTTP_BIND_PORT: '0',
    BOOTSTRAP_PLATFORM_ADMIN_USERNAME: `platform_${runId}`,
    BOOTSTRAP_PLATFORM_ADMIN_DISPLAY_NAME: '隔离部署验收平台管理员',
  };
  await writeFile(target.environment, Object.entries(config).map(([key, value]) => `MATERIALS_LAB_${key}=${value}`).join('\n') + '\n', { flag: 'wx', mode: 0o600 });
  target.platformUsername = config.BOOTSTRAP_PLATFORM_ADMIN_USERNAME;
  target.tenantUsername = `tenant_${runId}`;
  target.username = target.platformUsername;
  target.summary = { name: target.project, data_directory: dataRoot, private_directory: privateRoot, environment: target.environment };
  summary.projects.push(target.summary);
  operation(target, 'secrets');
  operation(target, 'prepare');
  await copyReleaseManifest(target);
  operation(target, 'preflight');
  return target;
}

/** 最小 HTTP Cookie 客户端；仅用于明确授权的非 TLS 隔离验收。 */
function client(target) {
  const cookies = new Map();
  let csrfToken;
  async function request(method, route, { body, headers = {}, csrf = true } = {}) {
    const requestHeaders = { ...headers };
    if (cookies.size) requestHeaders.Cookie = [...cookies].map(([key, value]) => `${key}=${value}`).join('; ');
    if (csrf && csrfToken && method !== 'GET') requestHeaders['X-CSRFToken'] = csrfToken;
    if (body) requestHeaders['Content-Length'] = body.length;
    return new Promise((resolve, reject) => {
      const req = http.request({ hostname: '127.0.0.1', family: 4, port: target.port, path: route,
        method, headers: requestHeaders, timeout: 20_000 }, response => {
        const parts = [];
        response.on('data', part => parts.push(part));
        response.on('error', reject);
        response.on('end', () => {
          for (const cookie of response.headers['set-cookie'] ?? []) {
            const pair = cookie.split(';', 1)[0];
            const separator = pair.indexOf('=');
            cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
          }
          resolve({ status: response.statusCode, headers: response.headers, body: Buffer.concat(parts) });
        });
      });
      req.on('timeout', () => req.destroy(new Error('隔离 HTTP 请求超时')));
      req.on('error', reject);
      req.end(body);
    });
  }
  async function refreshCsrf() {
    const response = await request('GET', '/api/v1/auth/csrf');
    assert.equal(response.status, 200, 'CSRF 接口应成功');
    csrfToken = JSON.parse(response.body).data.csrf_token;
    assert.ok(csrfToken, 'CSRF 令牌不能为空');
    assert.ok(cookies.has('csrftoken'), 'CSRF Cookie 必须设置');
    return response;
  }
  async function login(password) {
    await refreshCsrf();
    const response = await request('POST', '/api/v1/auth/login', {
      body: Buffer.from(JSON.stringify({ username: target.username, password })),
      headers: { 'Content-Type': 'application/json' },
    });
    assert.equal(response.status, 200, 'HTTP 管理员登录应成功');
    const sessionCookie = (response.headers['set-cookie'] ?? []).find(cookie => cookie.startsWith('JSESSIONID='));
    assert.ok(sessionCookie && !/;\s*Secure(?:;|$)/i.test(sessionCookie), 'HTTP 入口的会话不得带 Secure 属性');
    assert.ok(/;\s*HttpOnly(?:;|$)/i.test(sessionCookie), '会话必须使用 HttpOnly Cookie');
    assert.equal(JSON.parse(response.body).data.username, target.username, '应登录隔离验收管理员');
    await refreshCsrf();
  }
  return { request, login };
}

async function verifyRestored(target, password, projectId, documentId, content) {
  summary.current_stage = 'restore HTTP 重新登录与文档恢复断言';
  const authenticated = client(target);
  await authenticated.login(password);
  const projects = await authenticated.request('GET', '/api/v1/projects');
  assert.equal(projects.status, 200);
  const page = JSON.parse(projects.body);
  assert.equal(page.meta.total, 1, '恢复后应只有隔离验收项目');
  assert.equal(page.data[0].id, projectId, '恢复后项目主键应保留');
  const response = await authenticated.request('GET', `/api/v1/projects/${projectId}/documents/${documentId}/content?download=true`);
  assert.equal(response.status, 200);
  assert.ok(response.body.equals(content), '恢复后文档原始字节必须一致');
}

await mkdir(path.dirname(runDirectory), { recursive: true });
await mkdir(runDirectory, { mode: 0o700 });
await mkdir(privateRunDirectory, { recursive: true, mode: 0o700 });
try {
  command('docker', ['image', 'inspect', `materials-lab-api:${release}`, `materials-lab-web:${release}`], '确认同标签 API/Web 镜像');
  const source = await prepareTarget('source');
  operation(source, 'bootstrap');
  const expectedEmpty = { organization: 1, users: 1, admins: 0, roles: 0, permissions: 11,
    project: 0, experiment: 0, document: 0, content: 0, development_organization: 0 };
  assert.deepEqual(counts(source), expectedEmpty, '空库只能包含平台管理员和系统权限字典，不能预置业务组织');
  const before = identityFingerprint(source);
  operation(source, 'bootstrap', [], true);
  assert.deepEqual(counts(source), expectedEmpty, '重复初始化不得插入数据');
  assert.equal(identityFingerprint(source), before, '重复初始化不得修改账号、密码或角色');
  summary.checks.push('空库无开发种子和业务组织', '重复初始化安全拒绝且身份数据不变');
  operation(source, 'up');
  resolvePublishedPort(source);
  operation(source, 'check');
  summary.current_stage = 'source HTTP 匿名权限与平台管理员登录';
  const anonymous = await client(source).request('GET', '/api/v1/projects');
  assert.equal(anonymous.status, 401, '匿名业务请求应返回 401');
  const platformPassword = await readFile(path.join(source.secrets, 'bootstrap_platform_admin_password'), 'utf8');
  const platformClient = client(source);
  await platformClient.login(platformPassword);
  const platformUserManagement = await platformClient.request('GET', '/api/v1/auth/users');
  assert.equal(platformUserManagement.status, 403, '平台管理员不得读取租户用户管理接口');
  summary.current_stage = 'source 平台开通首个业务组织及组织管理员';
  const tenantPassword = 'Tenant123';
  const createdOrganization = await platformClient.request('POST', '/api/v1/organizations', {
    body: Buffer.from(JSON.stringify({
      organization_code: `ACCEPTANCE_${runId.toUpperCase()}`,
      organization_name: '隔离部署验收组织',
      admin_username: source.tenantUsername,
      admin_display_name: '隔离部署验收组织管理员',
      admin_email: '',
      admin_password: tenantPassword,
    })),
    headers: { 'Content-Type': 'application/json' },
  });
  assert.equal(createdOrganization.status, 201, '平台管理员应能原子开通组织及其管理员');
  assert.match(JSON.parse(createdOrganization.body).data.id, /^[a-f0-9-]{36}$/i);
  const expectedProvisioned = { ...expectedEmpty, organization: 2, users: 2, admins: 1, roles: 3 };
  assert.deepEqual(counts(source), expectedProvisioned, '组织开通应创建首个组织管理员和内置角色');
  source.username = source.tenantUsername;
  const authenticated = client(source);
  await authenticated.login(tenantPassword);
  summary.current_stage = 'source CSRF 写入限制与项目创建';
  const projectPayload = Buffer.from(JSON.stringify({ name: `隔离部署验收-${runId}`, description: '仅用于独立 Compose 验收，不属于正式业务数据' }));
  const rejected = await authenticated.request('POST', '/api/v1/projects', {
    body: projectPayload, headers: { 'Content-Type': 'application/json' }, csrf: false,
  });
  assert.equal(rejected.status, 403, '缺少 CSRF 的写请求应被拒绝');
  const created = await authenticated.request('POST', '/api/v1/projects', {
    body: projectPayload, headers: { 'Content-Type': 'application/json' },
  });
  assert.equal(created.status, 201, '项目应真实创建成功');
  const projectId = JSON.parse(created.body).data.id;
  assert.match(projectId, /^[a-f0-9-]{36}$/i);
  summary.current_stage = 'source 文档安全边界、上传与下载字节断言';
  const boundary = `materials-lab-${randomBytes(12).toString('hex')}`;
  const rejectedMultipart = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nother\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="version_label"\r\n\r\nacceptance\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="unsafe.html"\r\nContent-Type: text/html\r\n\r\n`),
    Buffer.from('<!doctype html><script>throw new Error("must not upload")</script>', 'utf8'),
    Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  const rejectedDocument = await authenticated.request('POST', `/api/v1/projects/${projectId}/documents`, {
    body: rejectedMultipart, headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
  });
  assert.equal(rejectedDocument.status, 400, '非白名单文档必须被服务端拒绝');

  const content = Buffer.from(`隔离部署验收文档 ${runId}\n仅包含可验证的 UTF-8 文本。\n`, 'utf8');
  const multipart = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nother\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="version_label"\r\n\r\nacceptance\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="acceptance.txt"\r\nContent-Type: text/plain\r\n\r\n`),
    content, Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  const uploaded = await authenticated.request('POST', `/api/v1/projects/${projectId}/documents`, {
    body: multipart, headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
  });
  assert.equal(uploaded.status, 201, '文档应真实上传成功');
  const documentId = JSON.parse(uploaded.body).data.id;
  const downloaded = await authenticated.request('GET', `/api/v1/projects/${projectId}/documents/${documentId}/content?download=true`);
  assert.equal(downloaded.status, 200);
  assert.ok(downloaded.body.equals(content), '上传与下载字节必须一致');
  assert.deepEqual(counts(source), { ...expectedProvisioned, project: 1, document: 1, content: 0 });
  summary.checks.push('HTTP IP:端口入口', '匿名 401', '平台管理员禁止用户管理', '平台开通组织及组织管理员', 'CSRF 拒绝无令牌写入', 'HTTP 登录及 HttpOnly 会话', '非白名单文档被拒绝', '项目文档上传下载字节一致');
  operation(source, 'backup');
  const backups = (await readdir(path.join(source.dataRoot, 'backups'))).filter(name => name.endsWith('.dump'));
  assert.equal(backups.length, 1, '隔离源库应生成一个完整备份');
  const archive = path.join(source.dataRoot, 'backups', backups[0]);
  const restore = await prepareTarget('restore');
  operation(restore, 'restore-new', [archive]);
  assert.deepEqual(counts(restore), { ...expectedProvisioned, project: 1, document: 1, content: 0 });
  operation(restore, 'up');
  resolvePublishedPort(restore);
  operation(restore, 'check');
  restore.username = source.tenantUsername;
  await verifyRestored(restore, tenantPassword, projectId, documentId, content);
  summary.checks.push('逻辑备份校验', '全新隔离目标恢复', '恢复后重新登录、项目与文档字节一致');
  summary.status = 'passed';
  summary.current_stage = '全部隔离验收通过';
} catch (error) {
  summary.status = 'failed';
  // 仅记录失败阶段和异常类型，不序列化请求体、Cookie、SQL 结果或子进程输出。
  summary.failure_type = error.name;
  console.error(`隔离验收失败：${summary.current_stage}（${error.name}）；容器与数据已保留，请检查该阶段。`);
  process.exitCode = 1;
} finally {
  summary.finished_at = timestamp();
  await writeFile(summaryPath, JSON.stringify(summary, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
  console.log(`验收状态：${summary.status}；摘要：${summaryPath}`);
  console.log('所有验收容器与数据均保留；测试数据仅在上述隔离项目，未操作已有开发或生产环境。');
}

#!/usr/bin/env bash
# 生产 Compose 运维入口。只操作显式配置的项目；不 source 配置、不自动删库/删卷。
set -Eeuo pipefail
umask 077

fail() { printf '错误：%s\n' "$*" >&2; exit 1; }
info() { printf '%s\n' "$*"; }
usage() {
  printf '%s\n' \
    '用法：bash scripts/production.sh --env /绝对路径/.env.production 命令 [参数]' \
    '  secrets                         原子创建密钥目录并生成数据库和 RustFS 凭据' \
    '  prepare                         创建项目持久目录（不更改已有所有者）' \
    '  preflight                       检查配置、密钥和 Compose，不启动服务' \
    '  build                           在受控构建机生成新标签 API/Web 镜像（含测试）' \
    '  bootstrap --confirm 项目名       启动隔离 PostgreSQL，初始化空库真实身份' \
    '  up --confirm 项目名              首次启动或恢复已初始化且已停止的服务' \
    '  upgrade --confirm 项目名         停写、备份，再启动已构建的新版本' \
    '  rollback 标签 --schema-compatible --confirm 项目名' \
    '                                  仅回退应用镜像；须已核实迁移兼容性' \
    '  status                          查看本项目容器状态' \
    '  check                           检查内部健康、身份和开发数据哨兵' \
    '  backup --confirm 项目名          停写后备份 PostgreSQL 与 RustFS，生成恢复组校验文件' \
    '  restore-new 备份绝对路径 --confirm 项目名' \
    '                                  只恢复到无业务表且应用已停止的新目标库' \
    '不提供删除、自动清理、覆盖现有库、自动数据库回滚或真实服务器连接命令。'
}

lab_repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
if [[ ${1:-} == --help || ${1:-} == -h ]]; then usage; exit 0; fi
[[ ${HOME:-} == /* && -d $HOME ]] || fail '当前执行用户 HOME 必须是已有绝对目录'
lab_home=$(cd -- "$HOME" && pwd -P)
[[ ${1:-} == --env && $# -ge 3 ]] || { usage; exit 1; }
lab_env=$2
shift 2
lab_action=$1
shift
[[ $lab_env == /* && -f $lab_env && ! -L $lab_env ]] || fail '配置必须为已有普通文件的绝对路径，不能为软链接'

# 清除同前缀的外部覆盖，确保审核的配置文件就是此次操作的配置；不触碰系统环境变量。
while IFS= read -r lab_key; do unset "$lab_key"; done < <(compgen -v MATERIALS_LAB_ || true)
while IFS= read -r lab_line || [[ -n $lab_line ]]; do
  [[ -z $lab_line || $lab_line == \#* ]] && continue
  [[ $lab_line =~ ^(MATERIALS_LAB_[A-Z0-9_]+)=(.*)$ ]] || fail '配置存在非法赋值，只允许 MATERIALS_LAB_ 前缀'
  lab_key=${BASH_REMATCH[1]}
  lab_value=${BASH_REMATCH[2]}
  [[ $lab_value != *[\$\`\"\'\\\#]* && $lab_value != *$'\r'* && $lab_value != ' '* && $lab_value != *' ' ]] || fail "配置 $lab_key 包含不支持的转义、注释或首尾空格"
  [[ ! ${!lab_key+x} ]] || fail "配置 $lab_key 重复"
  export "$lab_key=$lab_value"
done < "$lab_env"

for lab_key in MATERIALS_LAB_COMPOSE_PROJECT MATERIALS_LAB_RELEASE MATERIALS_LAB_DATA_ROOT MATERIALS_LAB_SECRETS_DIR MATERIALS_LAB_DB_NAME MATERIALS_LAB_DB_USER MATERIALS_LAB_OBJECT_STORAGE_BUCKET MATERIALS_LAB_PUBLIC_HOST MATERIALS_LAB_HTTP_BIND_ADDRESS MATERIALS_LAB_HTTP_BIND_PORT MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_DISPLAY_NAME; do
  [[ -n ${!lab_key:-} && ${!lab_key} != *CHANGE_ME* ]] || fail "请填写 $lab_key"
done
[[ $MATERIALS_LAB_COMPOSE_PROJECT =~ ^materials-lab-[a-z0-9][a-z0-9_-]*$ && $MATERIALS_LAB_COMPOSE_PROJECT != materials-lab-assistant ]] || fail '必须使用独立 materials-lab- 项目名，不能复用开发项目'
[[ $MATERIALS_LAB_RELEASE =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$ && $MATERIALS_LAB_RELEASE != latest ]] || fail '发布标签无效或使用了 latest'
for lab_key in MATERIALS_LAB_DB_NAME MATERIALS_LAB_DB_USER; do
  [[ ${!lab_key} =~ ^materials_lab[a-z0-9_]*$ && ${!lab_key} != *dev* ]] || fail "$lab_key 必须使用 materials_lab 前缀且不能含 dev"
done
is_ipv4() {
  local lab_ip=$1 lab_part
  [[ $lab_ip =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || return 1
  IFS=. read -r -a lab_parts <<< "$lab_ip"
  for lab_part in "${lab_parts[@]}"; do [[ $lab_part -le 255 ]] || return 1; done
}
is_ipv4 "$MATERIALS_LAB_PUBLIC_HOST" || fail '访问主机必须是公网 IPv4，不含协议、端口或路径'
is_ipv4 "$MATERIALS_LAB_HTTP_BIND_ADDRESS" || fail 'HTTP 绑定地址必须是 IPv4'
[[ $MATERIALS_LAB_OBJECT_STORAGE_BUCKET =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ && $MATERIALS_LAB_OBJECT_STORAGE_BUCKET != *..* ]] || fail '对象存储桶名必须为 3 至 63 位小写字母、数字、点或连字符'
lab_public_port=${MATERIALS_LAB_PUBLIC_PORT:-15105}
[[ $lab_public_port =~ ^[1-9][0-9]{0,4}$ && $lab_public_port -le 65535 ]] || fail '公开端口必须为 1 到 65535 的整数'
[[ $MATERIALS_LAB_HTTP_BIND_PORT =~ ^(0|[1-9][0-9]{0,4})$ && $MATERIALS_LAB_HTTP_BIND_PORT -le 65535 ]] || fail 'HTTP 宿主绑定端口必须为 0 到 65535 的整数'
if [[ $MATERIALS_LAB_HTTP_BIND_PORT != 0 && $MATERIALS_LAB_HTTP_BIND_PORT != "$lab_public_port" ]]; then
  fail 'HTTP 宿主绑定端口必须与公开端口一致；仅隔离验收可使用 0'
fi

# 避免宽泛目录、路径穿越及软链接重定向；代码/私有配置与持久数据分离。
validate_path() {
  local lab_path=$1 lab_cursor
  [[ $lab_path == /* && $lab_path != *'//'* && $lab_path != *'/../'* && $lab_path != */.. && $lab_path != *'/./'* && $lab_path != */. ]] || fail "目录不是规范绝对路径：$lab_path"
  case "$lab_path" in
    "$lab_home/work/data/labAssistant"|"$lab_home/work/data/labAssistant/"*|"$lab_home/work/server/labAssistant/data"|"$lab_home/work/server/labAssistant/data/"*|"$lab_repo/data"|"$lab_repo/data/"*) ;;
    *) fail '目录必须位于 ~/work/data/labAssistant 或项目部署目录 data/（公网服务器为 ~/work/server/labAssistant/data）' ;;
  esac
  lab_cursor=$lab_path
  while [[ $lab_cursor != / ]]; do
    [[ ! -L $lab_cursor ]] || fail "部署路径不能包含软链接：$lab_cursor"
    lab_cursor=$(dirname -- "$lab_cursor")
  done
}
validate_data_root() {
  local lab_path=$1
  case "$lab_path" in
    "$lab_home/work/data/labAssistant"|"$lab_home/work/data/labAssistant/"*) ;;
    *) fail '持久化数据目录必须位于 ~/work/data/labAssistant' ;;
  esac
  validate_path "$lab_path"
}
validate_data_root "$MATERIALS_LAB_DATA_ROOT"
validate_path "$MATERIALS_LAB_SECRETS_DIR"
validate_path "$MATERIALS_LAB_DATA_ROOT/postgres"
validate_path "$MATERIALS_LAB_DATA_ROOT/backups"
validate_path "$MATERIALS_LAB_DATA_ROOT/rustfs"
[[ $MATERIALS_LAB_DATA_ROOT != "$MATERIALS_LAB_SECRETS_DIR" ]] || fail '密钥目录不能与数据根目录相同'
case "$MATERIALS_LAB_SECRETS_DIR" in
  "$MATERIALS_LAB_DATA_ROOT/postgres"|"$MATERIALS_LAB_DATA_ROOT/postgres/"*|"$MATERIALS_LAB_DATA_ROOT/backups"|"$MATERIALS_LAB_DATA_ROOT/backups/"*) fail '密钥目录不能位于数据库或备份目录中' ;;
esac

lab_compose=(docker compose --project-directory "$lab_repo/infra" --env-file "$lab_env" -p "$MATERIALS_LAB_COMPOSE_PROJECT" -f "$lab_repo/infra/compose.production.yml")
dc() { "${lab_compose[@]}" "$@"; }
confirm() { [[ $# == 2 && $1 == --confirm && $2 == "$MATERIALS_LAB_COMPOSE_PROJECT" ]] || fail "变更必须显式指定 --confirm $MATERIALS_LAB_COMPOSE_PROJECT"; }
require_stopped() {
  local lab_id lab_state lab_ids
  lab_ids=$(dc ps --all -q api web bootstrap) || fail '无法读取目标容器状态，拒绝继续'
  for lab_id in $lab_ids; do
    lab_state=$(docker inspect --format '{{.State.Status}}' "$lab_id")
    [[ $lab_state == exited || $lab_state == created ]] || fail '目标 API/Web/初始化任务未完全停止；不能执行此操作'
  done
}
require_images() {
  docker image inspect "materials-lab-api:$MATERIALS_LAB_RELEASE" "materials-lab-web:$MATERIALS_LAB_RELEASE" >/dev/null || fail '目标版本镜像不齐，请先 build 或导入已验证的同标签镜像'
}
release_manifest_path() {
  printf '%s/releases/%s.json' "$MATERIALS_LAB_DATA_ROOT" "$MATERIALS_LAB_RELEASE"
}
require_clean_release_source() {
  local lab_status lab_git_sha
  command -v git >/dev/null || fail '构建发布镜像需要 Git，以校验干净且已提交的源码'
  [[ $(git -C "$lab_repo" rev-parse --is-inside-work-tree 2>/dev/null) == true ]] || fail '构建目录不是 Git 工作树，拒绝生成无法追溯的发布镜像'
  lab_status=$(git -C "$lab_repo" status --porcelain=v1 --untracked-files=all) || fail '无法读取 Git 工作树状态，拒绝构建'
  [[ -z $lab_status ]] || fail '工作树存在已修改、暂存或未跟踪文件；请在已审查提交后构建发布镜像'
  lab_git_sha=$(git -C "$lab_repo" rev-parse --verify HEAD 2>/dev/null) || fail '当前源码没有已提交的 Git SHA，拒绝构建'
  [[ $lab_git_sha =~ ^[0-9a-f]{40}$ ]] || fail 'Git SHA 格式无效，拒绝构建'
}
write_release_manifest() {
  local lab_manifest lab_stage lab_git_sha lab_api_image_id lab_web_image_id
  lab_manifest=$(release_manifest_path)
  [[ -d $(dirname -- "$lab_manifest") && ! -L $(dirname -- "$lab_manifest") ]] || fail '缺少发布清单目录，请先 prepare'
  [[ ! -e $lab_manifest && ! -L $lab_manifest ]] || fail '该发布标签的清单已存在，拒绝覆盖'
  lab_git_sha=$(git -C "$lab_repo" rev-parse --verify HEAD)
  lab_api_image_id=$(docker image inspect --format '{{.Id}}' "materials-lab-api:$MATERIALS_LAB_RELEASE")
  lab_web_image_id=$(docker image inspect --format '{{.Id}}' "materials-lab-web:$MATERIALS_LAB_RELEASE")
  [[ $lab_api_image_id =~ ^sha256:[0-9a-f]{64}$ && $lab_web_image_id =~ ^sha256:[0-9a-f]{64}$ ]] || fail '无法读取两份镜像的不可变 ID，拒绝生成发布清单'
  lab_stage="$lab_manifest.$$.partial"
  umask 077
  {
    printf '{\n'
    printf '  "release": "%s",\n' "$MATERIALS_LAB_RELEASE"
    printf '  "git_sha": "%s",\n' "$lab_git_sha"
    printf '  "api_image_id": "%s",\n' "$lab_api_image_id"
    printf '  "web_image_id": "%s",\n' "$lab_web_image_id"
    printf '  "built_at": "%s"\n' "$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M:%S')"
    printf '}\n'
  } > "$lab_stage"
  mv -- "$lab_stage" "$lab_manifest"
  info "发布清单已生成：${lab_manifest}；交付镜像时必须一并分发并在目标端校验"
}
manifest_value() {
  local lab_name=$1 lab_manifest=$2
  awk -F '"' -v key="$lab_name" '$2 == key { print $4; exit }' "$lab_manifest"
}
verify_release_manifest() {
  local lab_manifest lab_release lab_git_sha lab_api_expected lab_web_expected lab_api_actual lab_web_actual
  lab_manifest=$(release_manifest_path)
  [[ -f $lab_manifest && -r $lab_manifest && ! -L $lab_manifest ]] || fail '缺少受控发布清单；请随镜像导入该标签的 releases/*.json 文件'
  lab_release=$(manifest_value release "$lab_manifest")
  lab_git_sha=$(manifest_value git_sha "$lab_manifest")
  lab_api_expected=$(manifest_value api_image_id "$lab_manifest")
  lab_web_expected=$(manifest_value web_image_id "$lab_manifest")
  [[ $lab_release == "$MATERIALS_LAB_RELEASE" && $lab_git_sha =~ ^[0-9a-f]{40}$ ]] || fail '发布清单格式或标签不匹配'
  [[ $lab_api_expected =~ ^sha256:[0-9a-f]{64}$ && $lab_web_expected =~ ^sha256:[0-9a-f]{64}$ ]] || fail '发布清单中的镜像 ID 无效'
  lab_api_actual=$(docker image inspect --format '{{.Id}}' "materials-lab-api:$MATERIALS_LAB_RELEASE")
  lab_web_actual=$(docker image inspect --format '{{.Id}}' "materials-lab-web:$MATERIALS_LAB_RELEASE")
  [[ $lab_api_actual == "$lab_api_expected" && $lab_web_actual == "$lab_web_expected" ]] || fail '本机镜像 ID 与发布清单不匹配，拒绝启动或升级'
}
# 上线阶段失败时关闭本项目写入口；保留 PostgreSQL、备份和失败证据，不自动回滚数据。
release_exit() {
  local lab_status=$1
  trap - EXIT
  # 正常完成会解除 trap；任何未解除的退出都属于未完成上线，不能报告成功。
  [[ $lab_status -ne 0 ]] || lab_status=1
  printf '上线失败，正在停止本项目 API/Web 写入口。\n' >&2
  if ! dc stop web api; then
    printf '警告：自动停写失败，必须立即人工核对并阻断目标服务写入。\n' >&2
  fi
  exit "$lab_status"
}
check_secrets() {
  local lab_name lab_mode lab_file lab_raw_bytes lab_password lab_password_bytes lab_suffix_bytes lab_suffix_hex lab_username
  [[ -d $MATERIALS_LAB_SECRETS_DIR ]] || fail '密钥目录不存在，请先 secrets'
  lab_mode=$(stat -c '%a' "$MATERIALS_LAB_SECRETS_DIR" 2>/dev/null || stat -f '%Lp' "$MATERIALS_LAB_SECRETS_DIR")
  [[ $lab_mode == 700 ]] || fail '密钥目录权限必须为 700'
  for lab_name in postgres_password database_password bootstrap_platform_admin_password object_storage_access_key object_storage_secret_key; do
    lab_file=$MATERIALS_LAB_SECRETS_DIR/$lab_name
    [[ -f $lab_file && -s $lab_file && ! -L $lab_file ]] || fail "密钥文件缺失、不是普通文件或是软链接：$lab_name"
    lab_mode=$(stat -c '%a' "$lab_file" 2>/dev/null || stat -f '%Lp' "$lab_file")
    [[ $lab_mode == 444 ]] || fail "密钥文件 $lab_name 须为 444；父目录保持 700，以供非 root 容器只读挂载"
    if [[ $lab_name != bootstrap_platform_admin_password ]]; then
      [[ $(wc -c < "$lab_file") -ge 32 ]] || fail "密钥长度不足：$lab_name"
      continue
    fi
    lab_raw_bytes=$(wc -c < "$lab_file")
    [[ $lab_raw_bytes -le 74 ]] || fail '管理员密码密钥不得超过 74 字节（含可选行尾）'
    lab_password=$(<"$lab_file")
    [[ $lab_password != *$'\r' ]] || lab_password=${lab_password%$'\r'}
    lab_password_bytes=$(LC_ALL=C printf '%s' "$lab_password" | wc -c)
    lab_suffix_bytes=$((lab_raw_bytes - lab_password_bytes))
    case $lab_suffix_bytes in
      0) ;;
      1|2)
        lab_suffix_hex=$(LC_ALL=C tail -c "$lab_suffix_bytes" "$lab_file" | od -An -tx1 | tr -d '[:space:]')
        [[ $lab_suffix_hex == 0a || $lab_suffix_hex == 0d0a ]] || fail '管理员密码密钥只能包含密码正文和一个可选行尾' ;;
      *) fail '管理员密码密钥只能包含密码正文和一个可选行尾' ;;
    esac
    [[ ${#lab_password} -ge 6 && $lab_password_bytes -le 72 ]] || fail '管理员密码长度不符合 6 字符至 72 字节要求'
    [[ ! $lab_password =~ [[:space:][:cntrl:]] ]] || fail '管理员密码不能包含空白或控制字符'
    [[ $lab_password =~ [A-Za-z] && $lab_password =~ [0-9] ]] || fail '管理员密码必须包含英文字母和数字'
    lab_username=$MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME
    [[ $(printf '%s' "$lab_password" | tr '[:upper:]' '[:lower:]') != $(printf '%s' "$lab_username" | tr '[:upper:]' '[:lower:]') ]] \
      || fail '管理员密码不得与管理员用户名相同'
  done
  ! cmp -s "$MATERIALS_LAB_SECRETS_DIR/postgres_password" "$MATERIALS_LAB_SECRETS_DIR/database_password" || fail '数据库超级用户与应用密码必须不同'
  lab_password=$(<"$MATERIALS_LAB_SECRETS_DIR/object_storage_access_key")
  [[ $lab_password =~ ^[A-Z0-9]{16,64}$ ]] || fail 'RustFS access key 必须为 16 至 64 位大写字母或数字'
  lab_password=$(<"$MATERIALS_LAB_SECRETS_DIR/object_storage_secret_key")
  [[ ${#lab_password} -ge 32 && $lab_password != *[[:space:][:cntrl:]] ]] || fail 'RustFS secret key 长度不足或包含空白、控制字符'
}
preflight() {
  command -v docker >/dev/null || fail '需要 Docker 和 Compose >= 2.24.4'
  command -v tar >/dev/null || fail '一致恢复组备份需要 tar'
  docker info >/dev/null || fail 'Docker 引擎不可用'
  docker image inspect 'postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15' >/dev/null \
    || fail '目标 Docker 引擎缺少可按锁定摘要解析的 PostgreSQL 镜像；离线部署前必须验证完整 tag@digest 引用'
  docker image inspect 'rustfs/rustfs:v1.0.0-rc.5@sha256:b7014e0ce2bc703c1316b3ef760e29dfae61fe4a50d1a66fa89638e0f8ea211f' >/dev/null \
    || fail '目标 Docker 引擎缺少可按锁定摘要解析的 RustFS 镜像；离线部署前必须验证完整 tag@digest 引用'
  check_secrets
  [[ -d $MATERIALS_LAB_DATA_ROOT/postgres && -d $MATERIALS_LAB_DATA_ROOT/backups && -d $MATERIALS_LAB_DATA_ROOT/rustfs/data && -d $MATERIALS_LAB_DATA_ROOT/rustfs/logs ]] \
    || fail '持久目录未准备，请先 prepare'
  dc config --quiet
}
db_start() { dc up -d --wait --wait-timeout 120 postgres; }
db_query() {
  dc exec -T postgres sh -ec 'exec psql -X -v ON_ERROR_STOP=1 -U postgres -d "$MATERIALS_LAB_DB_NAME" -At' <<< "$1"
}
check_data() {
  local lab_result lab_schema_signature
  # 当前首版必须具备完整 V1 业务与平台/租户身份结构；停写前只读核对，绝不自动修复或清库。
  lab_schema_signature=$(db_query "SELECT (to_regclass('public.project_document_content') IS NOT NULL)::int || '|' || (to_regclass('public.experiment_attachment_content') IS NOT NULL)::int || '|' || EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'uk_user_account_username')::int || '|' || EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='organization' AND column_name='is_platform')::int || '|' || EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uk_organization_single_platform')::int || '|' || EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.user_account'::regclass AND conname='ck_user_account_platform_not_super')::int;")
  [[ $lab_schema_signature == '1|1|1|1|1|1' ]] || fail '数据库不符合当前发布结构签名；拒绝上线，请只读核查，不要自动清库'
  lab_result=$(db_query "SELECT (SELECT count(*) FROM organization WHERE organization_code='DEV_TEST') || '|' || (SELECT count(*) FROM organization WHERE is_platform) || '|' || (SELECT count(*) FROM user_account WHERE is_platform_admin AND is_active AND status='active') || '|' || (SELECT count(*) FROM user_account WHERE is_platform_admin AND is_super_admin) || '|' || (SELECT count(*) FROM user_account WHERE is_super_admin AND is_active AND status='active') || '|' || (SELECT count(*) FROM flyway_schema_history WHERE success);")
  [[ $lab_result =~ ^0\|1\|1\|0\|[0-9]+\|[1-9][0-9]*$ ]] || fail '身份边界或迁移检查失败，或发现 DEV_TEST；拒绝上线，请只读核查，不要自动清库'
}
check_global_username_uniqueness() {
  local lab_duplicates
  # 与首版全局登录名唯一约束保持一致；只返回计数，绝不输出账号名称。
  lab_duplicates=$(db_query "SELECT count(*) FROM (SELECT username FROM user_account GROUP BY username HAVING count(*) > 1) duplicate_usernames;")
  [[ $lab_duplicates == 0 ]] || fail '发现跨组织重复用户名；全局登录名唯一约束不允许上线，已在停写前拒绝升级。请先完成受控账号重命名'
}
backup() {
  local lab_schema_profile=materials-lab-production-v1 lab_base lab_backup lab_storage_backup lab_metadata lab_digest lab_service lab_container
  # 数据库元数据与对象文件只有在 API/Web 都停止时才构成同一恢复点。
  require_stopped
  check_data
  lab_base="$MATERIALS_LAB_DATA_ROOT/backups/$(TZ=Asia/Shanghai date +%Y%m%d-%H%M%S)-$$"
  lab_backup="$lab_base.dump"
  lab_storage_backup="$lab_base.rustfs-data.tar.gz"
  lab_metadata="$lab_backup.metadata.txt"
  [[ ! -e $lab_backup && ! -e $lab_storage_backup && ! -e $lab_metadata ]] || fail '备份文件已存在'
  # 记录实际源容器而不是配置中的目标发布标签，避免升级/回退时错误识别备份版本。
  {
    printf 'backup_started_at=%s\ntimezone=Asia/Shanghai\ncompose_project=%s\n' "$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M:%S')" "$MATERIALS_LAB_COMPOSE_PROJECT"
    printf 'recovery_unit=postgres_dump+rustfs_data\nschema_profile=%s\nrustfs_data_archive=%s\n' "$lab_schema_profile" "$(basename -- "$lab_storage_backup")"
    for lab_service in api web postgres rustfs; do
      lab_container=$(dc ps --all -q "$lab_service") || fail '无法读取备份来源容器'
      if [[ -n $lab_container ]]; then
        printf '%s_source_image=' "$lab_service"
        docker inspect --format '{{.Config.Image}} {{.Image}}' "$lab_container"
      else
        printf '%s_source_image=unavailable\n' "$lab_service"
      fi
    done
    printf 'flyway_history_version_script_checksum_success:\n'
    db_query 'SELECT version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;'
  } > "$lab_metadata.partial"
  # 失败时保留 .partial 供定位，不将不完整备份标为成功，也不自动删除任何文件。
  dc exec -T postgres sh -ec 'exec pg_dump -U postgres -d "$MATERIALS_LAB_DB_NAME" --format=custom --no-owner --no-acl' > "$lab_backup.partial"
  dc exec -T postgres pg_restore --list < "$lab_backup.partial" >/dev/null
  tar -C "$MATERIALS_LAB_DATA_ROOT" -czf "$lab_storage_backup.partial" rustfs/data
  tar -tzf "$lab_storage_backup.partial" >/dev/null
  mv -- "$lab_backup.partial" "$lab_backup"
  mv -- "$lab_storage_backup.partial" "$lab_storage_backup"
  if command -v sha256sum >/dev/null; then
    lab_digest=$(sha256sum "$lab_backup")
  else
    lab_digest=$(shasum -a 256 "$lab_backup")
  fi
  printf '%s\n' "${lab_digest%% *}" > "$lab_backup.sha256"
  if command -v sha256sum >/dev/null; then
    lab_digest=$(sha256sum "$lab_storage_backup")
  else
    lab_digest=$(shasum -a 256 "$lab_storage_backup")
  fi
  printf '%s\n' "${lab_digest%% *}" > "$lab_storage_backup.sha256"
  mv -- "$lab_metadata.partial" "$lab_metadata"
  info "恢复组备份完成：${lab_backup} 与 ${lab_storage_backup}（Asia/Shanghai；尚需隔离恢复验证）"
}

case "$lab_action" in
  secrets)
    [[ $# == 0 ]] || fail 'secrets 不接受额外参数'
    command -v openssl >/dev/null || fail '生成随机密码需要 openssl'
    [[ ! -e $MATERIALS_LAB_SECRETS_DIR && ! -L $MATERIALS_LAB_SECRETS_DIR ]] || fail '密钥目录或密码文件已存在，拒绝覆盖；凭据轮换需要独立操作'
    lab_secrets_parent=$(dirname -- "$MATERIALS_LAB_SECRETS_DIR")
    mkdir -p -- "$lab_secrets_parent"
    lab_secrets_stage=$(mktemp -d "$lab_secrets_parent/.materials-lab-secrets.XXXXXXXX") || fail '无法创建私有密钥暂存目录'
    chmod 700 "$lab_secrets_stage"
    if ! (
      set -Eeuo pipefail
      for lab_name in postgres_password database_password bootstrap_platform_admin_password; do
        lab_random=$(openssl rand -hex 24) || exit 1
        [[ $lab_random =~ ^[0-9a-f]{48}$ ]] || exit 1
        lab_password="Aa1!$lab_random"
        (set -o noclobber; printf '%s' "$lab_password" > "$lab_secrets_stage/$lab_name") || exit 1
        # 私有父目录阻止其他宿主用户遍历；文件只读以兼容 Compose bind secret 的容器 UID。
        chmod 444 "$lab_secrets_stage/$lab_name" || exit 1
      done
      lab_random=$(openssl rand -hex 24 | tr '[:lower:]' '[:upper:]') || exit 1
      (set -o noclobber; printf 'ML%s' "$lab_random" > "$lab_secrets_stage/object_storage_access_key") || exit 1
      lab_random=$(openssl rand -hex 32) || exit 1
      (set -o noclobber; printf 'Aa1!%s' "$lab_random" > "$lab_secrets_stage/object_storage_secret_key") || exit 1
      chmod 444 "$lab_secrets_stage/object_storage_access_key" "$lab_secrets_stage/object_storage_secret_key" || exit 1
    ); then
      fail "密钥生成失败；最终目录未发布，私有暂存目录保留：$lab_secrets_stage"
    fi
    [[ ! -e $MATERIALS_LAB_SECRETS_DIR && ! -L $MATERIALS_LAB_SECRETS_DIR ]] || fail "密钥目录在生成期间被创建；最终目录未发布，私有暂存目录保留：$lab_secrets_stage"
    mv -- "$lab_secrets_stage" "$MATERIALS_LAB_SECRETS_DIR"
    info '已生成独立随机凭据；不会打印密码。请通过受控渠道读取并保管管理员密码。' ;;
  prepare)
    [[ $# == 0 ]] || fail 'prepare 不接受额外参数'
    mkdir -p -- "$MATERIALS_LAB_DATA_ROOT/postgres" "$MATERIALS_LAB_DATA_ROOT/rustfs/data" "$MATERIALS_LAB_DATA_ROOT/rustfs/logs" "$MATERIALS_LAB_DATA_ROOT/backups" "$MATERIALS_LAB_DATA_ROOT/releases"
    info '持久目录已准备；未修改已有数据所有者。首次启动 PostgreSQL 会设置其自身目录权限。' ;;
  preflight)
    [[ $# == 0 ]] || fail 'preflight 不接受额外参数'
    preflight; info '静态部署预检通过；尚未启动服务或验证业务。' ;;
  build)
    [[ $# == 0 ]] || fail 'build 不接受额外参数'
    preflight
    require_clean_release_source
    for lab_image in "materials-lab-api:$MATERIALS_LAB_RELEASE" "materials-lab-web:$MATERIALS_LAB_RELEASE"; do
      ! docker image inspect "$lab_image" >/dev/null 2>&1 || fail '该发布标签已存在，拒绝覆盖；请设置新的发布标签'
    done
    # 构建与运行时 Compose 分离：up/upgrade/rollback 永不根据工作区源码构建镜像。
    docker build --file "$lab_repo/infra/Dockerfile.api" --tag "materials-lab-api:$MATERIALS_LAB_RELEASE" "$lab_repo"
    docker build --file "$lab_repo/infra/Dockerfile.web" --tag "materials-lab-web:$MATERIALS_LAB_RELEASE" "$lab_repo"
    require_images
    require_clean_release_source
    write_release_manifest
    info '发布镜像构建完成；请连同发布清单经受控分发并在目标端校验后再执行部署。' ;;
  bootstrap)
    confirm "$@"; preflight; require_images; verify_release_manifest; require_stopped
    [[ -s $MATERIALS_LAB_SECRETS_DIR/bootstrap_platform_admin_password && ! -L $MATERIALS_LAB_SECRETS_DIR/bootstrap_platform_admin_password ]] || fail '缺少初始化平台管理员密码文件'
    # bootstrap 使用 --no-deps 防止隐式创建其他服务，因此须显式等待两个持久依赖均健康。
    dc up -d --wait --wait-timeout 180 postgres rustfs
    # 保留一次性容器退出结果供审计；不自动清理。
    dc run --no-deps -T bootstrap
    check_data
    info '首次身份初始化完成。生产样例数据检查通过。' ;;
  up)
    confirm "$@"; preflight; require_images; verify_release_manifest; require_stopped
    db_start; check_data
    trap 'release_exit $?' EXIT
    dc up -d --no-build --wait --wait-timeout 180 api web
    check_data
    trap - EXIT
    info '容器与健康检查通过；仍需通过公网 HTTP 地址完成登录和业务验收。' ;;
  upgrade)
    confirm "$@"; preflight; require_images; verify_release_manifest
    check_data
    check_global_username_uniqueness
    trap 'release_exit $?' EXIT
    dc stop web api
    # 停写后再次核验，覆盖首次预检与容器完全停止之间的并发账号创建窗口。
    check_global_username_uniqueness
    backup
    dc up -d --no-build --wait --wait-timeout 180 api web
    check_data
    trap - EXIT
    info '升级启动完成。失败时保持停写状态，请按备份与迁移兼容性决定恢复，不会自动回滚数据库。' ;;
  rollback)
    [[ $# == 4 && $2 == --schema-compatible ]] || fail '回退需要旧标签、--schema-compatible 和 --confirm 项目名'
    lab_previous=$1; shift 2; confirm "$@"
    [[ $lab_previous =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$ && $lab_previous != latest ]] || fail '旧标签非法'
    preflight
    export MATERIALS_LAB_RELEASE=$lab_previous
    require_images
    verify_release_manifest
    check_data
    trap 'release_exit $?' EXIT
    dc stop web api
    backup
    dc up -d --no-build --no-deps --wait --wait-timeout 180 api web
    check_data
    trap - EXIT
    info '已回退应用镜像，数据库未降级；请同步受控配置中的发布标签并重新验收。' ;;
  status)
    [[ $# == 0 ]] || fail 'status 不接受额外参数'
    dc ps -a ;;
  check)
    [[ $# == 0 ]] || fail 'check 不接受额外参数'
    preflight; check_data
    dc exec -T api curl --fail --silent --show-error http://127.0.0.1:8000/api/v1/health/ready
    dc exec -T web wget -q -O /dev/null http://127.0.0.1:8081/healthz
    info '内部健康与基础数据检查通过；不代表公网登录或完整业务验收。' ;;
  backup)
    confirm "$@"; preflight
    trap 'release_exit $?' EXIT
    dc stop web api
    backup
    trap - EXIT
    info '恢复组已生成，API/Web 保持停止；请完成异机副本或执行 up --confirm 项目名后再恢复写入。' ;;
  restore-new)
    [[ $# == 3 ]] || fail 'restore-new 需要备份绝对路径和 --confirm 项目名'
    lab_archive=$1; shift; confirm "$@"
    [[ $lab_archive == /* && -f $lab_archive && ! -L $lab_archive && -s $lab_archive.sha256 && -f $lab_archive.metadata.txt && ! -L $lab_archive.metadata.txt ]] || fail '需要普通数据库备份、元数据和同名 .sha256 文件'
    lab_storage_archive="${lab_archive%.dump}.rustfs-data.tar.gz"
    [[ $lab_archive == *.dump && -f $lab_storage_archive && ! -L $lab_storage_archive && -s $lab_storage_archive.sha256 ]] || fail '缺少同一恢复组的 RustFS 数据归档或校验文件'
    grep -Fx "recovery_unit=postgres_dump+rustfs_data" "$lab_archive.metadata.txt" >/dev/null || fail '备份元数据不属于完整恢复组'
    grep -Fx 'schema_profile=materials-lab-production-v1' "$lab_archive.metadata.txt" >/dev/null || fail '备份架构不兼容当前恢复流程；未写入目标，请使用对应版本的恢复工具'
    grep -Fx "rustfs_data_archive=$(basename -- "$lab_storage_archive")" "$lab_archive.metadata.txt" >/dev/null || fail '备份元数据与 RustFS 数据归档不匹配'
    if command -v sha256sum >/dev/null; then lab_digest=$(sha256sum "$lab_archive"); else lab_digest=$(shasum -a 256 "$lab_archive"); fi
    [[ ${lab_digest%% *} == "$(< "$lab_archive.sha256")" ]] || fail '备份校验值不匹配'
    if command -v sha256sum >/dev/null; then lab_digest=$(sha256sum "$lab_storage_archive"); else lab_digest=$(shasum -a 256 "$lab_storage_archive"); fi
    [[ ${lab_digest%% *} == "$(< "$lab_storage_archive.sha256")" ]] || fail 'RustFS 数据归档校验值不匹配'
    preflight; require_stopped
    lab_storage_directory="$MATERIALS_LAB_DATA_ROOT/rustfs/data"
    [[ ! -L $lab_storage_directory ]] || fail '目标 RustFS 数据目录不能是软链接'
    if [[ -e $lab_storage_directory ]]; then
      [[ -d $lab_storage_directory && -z $(find "$lab_storage_directory" -mindepth 1 -maxdepth 1 -print -quit) ]] || fail '目标 RustFS 数据目录非空，拒绝覆盖'
    fi
    lab_restore_stage=$(mktemp -d "$MATERIALS_LAB_DATA_ROOT/.materials-lab-rustfs-restore.XXXXXXXX") || fail '无法创建 RustFS 恢复暂存目录'
    tar -C "$lab_restore_stage" -xzf "$lab_storage_archive"
    [[ -d $lab_restore_stage/rustfs/data && -z $(find "$lab_restore_stage" -mindepth 1 -maxdepth 1 ! -name rustfs -print -quit) ]] || fail "RustFS 数据归档结构无效；暂存目录保留：$lab_restore_stage"
    db_start
    lab_tables=$(db_query "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE left(n.nspname,3) <> 'pg_' AND n.nspname <> 'information_schema' AND c.relkind IN ('r','p','v','m','S','f');")
    [[ $lab_tables == 0 ]] || fail '目标数据库非空，拒绝覆盖；使用新的隔离项目和数据目录'
    dc exec -T postgres sh -ec 'exec pg_restore -U postgres -d "$MATERIALS_LAB_DB_NAME" --role="$MATERIALS_LAB_DB_USER" --no-owner --no-acl --exit-on-error --single-transaction' < "$lab_archive"
    check_data
    mkdir -p -- "$MATERIALS_LAB_DATA_ROOT/rustfs"
    [[ ! -d $lab_storage_directory ]] || rmdir -- "$lab_storage_directory"
    if ! mv -- "$lab_restore_stage/rustfs/data" "$lab_storage_directory"; then
      fail "数据库已恢复，但 RustFS 数据仍在暂存目录：$lab_restore_stage/rustfs/data；保持服务停止，核对目标目录为空后手工原子移动该目录，再执行 up"
    fi
    rmdir -- "$lab_restore_stage/rustfs" "$lab_restore_stage"
    info '已恢复到空目标数据库及 RustFS 数据目录。请使用备份对应版本镜像验证后再评估升级。' ;;
  *) usage; fail '未知命令' ;;
esac

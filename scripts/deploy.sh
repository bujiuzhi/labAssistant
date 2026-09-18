#!/usr/bin/env bash
# 日常部署入口：从少量配置生成受控配置，复用 production.sh 的数据保护和发布流程。
set -Eeuo pipefail
umask 077
fail() { printf '错误：%s\n' "$*" >&2; exit 1; }
lab_repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
lab_engine=$lab_repo/scripts/production.sh
lab_env=$lab_repo/.env.production
if [[ ${1:-} == --env ]]; then [[ $# -ge 3 ]] || fail '--env 后需要配置路径和命令'; lab_env=$2; shift 2; fi
lab_action=${1:---help}
case "$lab_action" in
  --help|-h)
    printf '%s\n' '用法：bash scripts/deploy.sh [--env /绝对路径/.env.production] 命令' \
      '  install    首次构建、初始化空库并启动' \
      '  upgrade    构建当前提交，停写备份并升级' \
      '  restart    重启已部署版本' \
      '  status     查看服务状态' \
      '  check      检查已部署版本健康状态' \
      '  backup     停写并备份；完成后用 up 恢复' \
      '  up         恢复已部署但已停止的服务' \
      '  resume     使用失败部署的新版本配置继续启动，绝不自动退回旧镜像' \
      '  uninstall  移除服务容器和网络，保留全部数据和镜像'
    exit 0 ;;
  install|upgrade|restart|status|check|backup|up|resume|uninstall) ;;
  *) fail '未知命令，请使用 --help' ;;
esac
[[ $# == 1 ]] || fail '此入口不接受额外参数；高级操作使用 production.sh'
[[ $lab_env == /* && -f $lab_env && ! -L $lab_env ]] || fail '配置必须是绝对路径的普通文件，不能是软链接'
[[ ${HOME:-} == /* && -d $HOME ]] || fail '当前用户 HOME 必须是已有绝对目录'
lab_home=$(cd -- "$HOME" && pwd -P)

# 不执行配置内容，也不允许外部环境覆盖本次文件。
lab_port=13501
lab_username=admin
lab_display_name=平台管理员
lab_host=auto
lab_seen='|'
lab_legacy=false
while IFS= read -r lab_line || [[ -n $lab_line ]]; do
  [[ -z $lab_line || $lab_line == \#* ]] && continue
  [[ $lab_line =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || fail '配置只接受 KEY=VALUE'
  lab_key=${BASH_REMATCH[1]}; lab_value=${BASH_REMATCH[2]}
  [[ $lab_seen != *"|$lab_key|"* ]] || fail "重复配置：$lab_key"
  lab_seen="$lab_seen$lab_key|"
  [[ -n $lab_value && $lab_value != *CHANGE_ME* && $lab_value != *[\$\`\"\'\\\#]* && $lab_value != *$'\r'* && $lab_value != ' '* && $lab_value != *' ' ]] || fail "配置 $lab_key 为空或含不支持的字符"
  case "$lab_key" in
    APP_PORT) lab_port=$lab_value ;;
    ADMIN_USERNAME) lab_username=$lab_value ;;
    ADMIN_DISPLAY_NAME) lab_display_name=$lab_value ;;
    PUBLIC_HOST) lab_host=$lab_value ;;
    MATERIALS_LAB_*) lab_legacy=true ;;
    *) fail "未知配置：$lab_key" ;;
  esac
done < "$lab_env"
[[ $lab_seen != '|' ]] || fail '配置为空，请复制 infra/.env.production.example'

# 旧部署保留原项目名、目录、密钥和显式版本，避免切到新的空数据目录。
if [[ $lab_legacy == true ]]; then
  [[ $lab_action != resume ]] || fail '旧配置恢复请使用 production.sh 的 up 或回退流程'
  [[ $lab_seen != *'|APP_PORT|'* && $lab_seen != *'|ADMIN_USERNAME|'* && $lab_seen != *'|ADMIN_DISPLAY_NAME|'* && $lab_seen != *'|PUBLIC_HOST|'* ]] || fail '新旧配置不能混用；已有部署继续使用原配置'
  lab_project=$(awk -F= '$1 == "MATERIALS_LAB_COMPOSE_PROJECT" { print $2 }' "$lab_env")
  [[ -n $lab_project ]] || fail '旧配置缺少项目名'
  printf '%s\n' '检测到旧版完整配置：保留原目录与显式发布标签；升级前仍需按原流程 build。'
  case "$lab_action" in
    status|check) exec bash "$lab_engine" --env "$lab_env" "$lab_action" ;;
    *) exec bash "$lab_engine" --env "$lab_env" "$lab_action" --confirm "$lab_project" ;;
  esac
fi
[[ $lab_port =~ ^[1-9][0-9]{0,4}$ && $lab_port -le 65535 ]] || fail 'APP_PORT 必须为 1 到 65535 的整数'
if [[ $lab_host != auto ]]; then
  [[ $lab_host =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || fail 'PUBLIC_HOST 只支持 auto 或 IPv4'
  IFS=. read -r -a lab_octets <<< "$lab_host"
  for lab_octet in "${lab_octets[@]}"; do [[ $((10#$lab_octet)) -le 255 ]] || fail 'PUBLIC_HOST 不是有效 IPv4'; done
fi
lab_data=$lab_home/work/data/labAssistant
lab_secrets=$lab_home/work/server/labAssistant/data/production-secrets
lab_state=$lab_home/work/server/labAssistant/data/deployment
lab_active=$lab_state/active.env
lab_incomplete=$lab_state/incomplete.env
# 写入前逐级排除软链接；只有当前用户的项目专属目录可以存放状态。
for lab_path in "$lab_data" "$lab_secrets" "$lab_state"; do
  lab_cursor=$lab_path
  while [[ $lab_cursor != / ]]; do
    [[ ! -L $lab_cursor ]] || fail '部署目录不能包含软链接'
    lab_cursor=$(dirname -- "$lab_cursor")
  done
done
[[ ! -L $lab_active ]] || fail '运行配置不能是软链接'
[[ ! -e $lab_active || -f $lab_active ]] || fail '运行配置必须是普通文件'
[[ ! -L $lab_incomplete && ( ! -e $lab_incomplete || -f $lab_incomplete ) ]] || fail '失败部署记录必须是普通文件'
lock_operation() {
  mkdir -p -- "$lab_state" "$lab_data"
  chmod 700 "$lab_state"
  lab_lock=$lab_data/.operation-lock
  mkdir -- "$lab_lock" 2>/dev/null || fail '已有本项目运维操作或遗留锁，请核对后再操作'
  trap 'rmdir -- "$lab_lock"' EXIT
  export LABASSISTANT_OPERATION_LOCK_HELD=materials-lab-production
  case "$lab_action" in
    install|upgrade|restart|up)
      [[ ! -e $lab_incomplete ]] || fail '上次上线未完成，请先 resume 或按部署文档恢复' ;;
  esac
  [[ $lab_action != install || ! -e $lab_active ]] || fail '已有部署记录，请使用 upgrade'
}
if [[ $lab_action == resume ]]; then
  [[ -f $lab_incomplete ]] || fail '没有待恢复的部署'
  lock_operation
  bash "$lab_engine" --env "$lab_incomplete" up --confirm materials-lab-production
  bash "$lab_engine" --env "$lab_incomplete" check
  mv -- "$lab_incomplete" "$lab_active"
  printf '%s\n' '失败部署已按其目标版本恢复；请完成业务验收。'
  exit 0
fi
case "$lab_action" in
  install|upgrade|restart|up)
    [[ ! -e $lab_incomplete ]] || fail '上次上线未完成，禁止隐式回退旧镜像；请先使用 resume 恢复目标版本，或按部署文档人工处理' ;;
esac

# 状态命令只读取已部署配置；不会因新代码或编辑端口而切换运行版本。
if [[ $lab_action != install && $lab_action != upgrade ]]; then
  [[ -f $lab_active ]] || fail '尚无成功部署记录；首次部署请使用 install'
  case "$lab_action" in
    status|check) exec bash "$lab_engine" --env "$lab_active" "$lab_action" ;;
    *) lock_operation
       bash "$lab_engine" --env "$lab_active" "$lab_action" --confirm materials-lab-production
       exit 0 ;;
  esac
fi
[[ $lab_action != install || ! -e $lab_active ]] || fail '已有部署记录，请使用 upgrade'
[[ $lab_action != upgrade || -f $lab_active ]] || fail '没有成功部署记录；已有旧部署请保留原完整配置'
if [[ $lab_action == install ]]; then
  for lab_path in "$lab_data/postgres" "$lab_data/rustfs/data"; do
    [[ ! -L $lab_path ]] || fail '数据目录不能是软链接'
    if [[ -e $lab_path ]]; then
      [[ -d $lab_path ]] || fail '数据路径必须是目录'
      lab_existing=$(find "$lab_path" -mindepth 1 -maxdepth 1 -print -quit) || fail '无法检查已有数据'
      [[ -z $lab_existing ]] || fail '已有持久化数据，拒绝首次安装；请使用原配置升级或按失败配置恢复'
    fi
  done
fi
lab_release=$(git -C "$lab_repo" rev-parse --verify HEAD) || fail '无法获取当前提交'
[[ $lab_release =~ ^[0-9a-f]{40}$ ]] || fail '无效的 Git 提交号'
# 每次构建使用唯一标签，部分构建失败后可安全重试；清单仍记录完整 Git SHA。
lab_release="${lab_release:0:12}-$(date -u '+%Y%m%d%H%M%S')-$$"
lab_git_status=$(git -C "$lab_repo" status --porcelain=v1 --untracked-files=all) || fail '无法读取 Git 状态'
[[ -z $lab_git_status ]] || fail '工作树存在未提交修改，请使用已审查的干净提交'
lock_operation
lab_pending=$(mktemp "$lab_state/release.env.XXXXXXXX")
{
  printf '%s\n' 'MATERIALS_LAB_COMPOSE_PROJECT=materials-lab-production' \
    "MATERIALS_LAB_RELEASE=$lab_release" "MATERIALS_LAB_DATA_ROOT=$lab_data" \
    "MATERIALS_LAB_SECRETS_DIR=$lab_secrets" 'MATERIALS_LAB_DB_NAME=materials_lab' \
    'MATERIALS_LAB_DB_USER=materials_lab_app' 'MATERIALS_LAB_DB_POOL_SIZE=20' \
    'MATERIALS_LAB_OBJECT_STORAGE_BUCKET=materials-lab-assistant' \
    "MATERIALS_LAB_PUBLIC_HOST=$lab_host" "MATERIALS_LAB_PUBLIC_PORT=$lab_port" \
    'MATERIALS_LAB_HTTP_BIND_ADDRESS=0.0.0.0' "MATERIALS_LAB_HTTP_BIND_PORT=$lab_port" \
    "MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME=$lab_username" \
    "MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_DISPLAY_NAME=$lab_display_name"
} > "$lab_pending"
printf '本次受控配置：%s\n' "$lab_pending"
lab_compose=(docker compose --project-directory "$lab_repo/infra" --env-file "$lab_pending" -p materials-lab-production -f "$lab_repo/infra/compose.production.yml")
# 不允许宿主环境变量覆盖生成的配置。
while IFS= read -r lab_key; do unset "$lab_key"; done < <(compgen -v MATERIALS_LAB_ || true)
if [[ $lab_action == install ]]; then
  lab_existing=$("${lab_compose[@]}" ps --all -q) || fail '无法检查已有项目容器'
  [[ -z $lab_existing ]] || fail '该项目已有容器，拒绝首次安装；请保留原配置或恢复失败部署'
fi
"${lab_compose[@]}" pull postgres rustfs
if [[ $lab_action == install ]]; then
  # 构建单独执行；构建失败尚未进入上线，不标记为数据库迁移失败。
  if [[ ! -e $lab_secrets && ! -L $lab_secrets ]]; then
    bash "$lab_engine" --env "$lab_pending" secrets
  fi
  bash "$lab_engine" --env "$lab_pending" prepare
  bash "$lab_engine" --env "$lab_pending" build
  ln -- "$lab_pending" "$lab_incomplete"
  bash "$lab_engine" --env "$lab_pending" bootstrap --confirm materials-lab-production
  bash "$lab_engine" --env "$lab_pending" up --confirm materials-lab-production
  bash "$lab_engine" --env "$lab_pending" check
else
  bash "$lab_engine" --env "$lab_pending" build
  ln -- "$lab_pending" "$lab_incomplete"
  bash "$lab_engine" --env "$lab_pending" upgrade --confirm materials-lab-production
fi
# 全流程成功后才切换活动配置；失败时保留原记录与本次配置，供人工恢复。
mv -- "$lab_incomplete" "$lab_active"
printf '部署成功，默认访问 http://服务器IP:%s；管理员密码文件：%s/bootstrap_platform_admin_password\n' "$lab_port" "$lab_secrets"

#!/bin/sh
# 生成固定 HTTP 入口配置，不把任意环境变量注入 Nginx 指令。
set -eu

materials_lab_fail() {
    printf '%s\n' "生产 Web 配置错误：$1" >&2
    exit 1
}

: "${MATERIALS_LAB_PUBLIC_HOST:?必须配置生产访问 IPv4 MATERIALS_LAB_PUBLIC_HOST}"
: "${MATERIALS_LAB_PUBLIC_PORT:=13501}"

if ! printf '%s\n' "$MATERIALS_LAB_PUBLIC_HOST" | LC_ALL=C grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
    materials_lab_fail '访问主机仅允许 IPv4，不含协议、端口或路径'
fi
(
    IFS=.
    set -- $MATERIALS_LAB_PUBLIC_HOST
    for materials_lab_octet do
        [ "$materials_lab_octet" -le 255 ] || materials_lab_fail '访问主机仅允许有效 IPv4'
    done
)
case "$MATERIALS_LAB_PUBLIC_PORT" in
    ''|*[!0-9]*) materials_lab_fail '公开端口必须为 1 到 65535 的整数' ;;
esac
if [ "${#MATERIALS_LAB_PUBLIC_PORT}" -gt 5 ] \
    || [ "$MATERIALS_LAB_PUBLIC_PORT" -lt 1 ] \
    || [ "$MATERIALS_LAB_PUBLIC_PORT" -gt 65535 ]; then
    materials_lab_fail '公开端口必须为 1 到 65535 的整数'
fi

export MATERIALS_LAB_PUBLIC_HOST MATERIALS_LAB_PUBLIC_PORT
mkdir -p /tmp/nginx/client_body /tmp/nginx/proxy /tmp/nginx/fastcgi /tmp/nginx/uwsgi /tmp/nginx/scgi
# 显式限定替换列表，保留 $uri、$remote_addr 等 Nginx 运行期变量。
envsubst '${MATERIALS_LAB_PUBLIC_HOST} ${MATERIALS_LAB_PUBLIC_PORT}' \
    < "/etc/nginx/materials-lab/server-http.conf.template" \
    > /tmp/nginx/server.conf
envsubst '${MATERIALS_LAB_PUBLIC_HOST} ${MATERIALS_LAB_PUBLIC_PORT}' \
    < /etc/nginx/materials-lab/locations.conf.template \
    > /tmp/nginx/locations.conf
nginx -t
exec "$@"

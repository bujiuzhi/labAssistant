#!/bin/sh
# 仅由官方 PostgreSQL 镜像在空数据目录首次启动时执行。
# 创建业务数据库及非超级用户；业务表仍由应用内 Flyway 迁移管理。
set -eu

materials_lab_fail() {
    printf '%s\n' "生产数据库初始化失败：$1" >&2
    exit 1
}

: "${MATERIALS_LAB_DB_USER:?必须配置业务数据库用户 MATERIALS_LAB_DB_USER}"
: "${MATERIALS_LAB_DB_NAME:?必须配置业务数据库名称 MATERIALS_LAB_DB_NAME}"
: "${MATERIALS_LAB_DB_PASSWORD_FILE:?必须配置业务数据库密码文件 MATERIALS_LAB_DB_PASSWORD_FILE}"
[ "${POSTGRES_USER:-postgres}" = postgres ] || materials_lab_fail '管理用户必须为独立的 postgres，不能复用业务用户'
for materials_lab_identifier in "$MATERIALS_LAB_DB_USER" "$MATERIALS_LAB_DB_NAME"; do
    printf '%s\n' "$materials_lab_identifier" | LC_ALL=C grep -Eq '^[a-z][a-z0-9_]{0,62}$' \
        || materials_lab_fail '数据库名与用户名必须采用小写 snake_case，长度不超过 63'
    case "$materials_lab_identifier" in
        postgres|template0|template1|pg_*) materials_lab_fail '不能使用 PostgreSQL 保留的数据库名或用户名' ;;
    esac
done
[ -r "$MATERIALS_LAB_DB_PASSWORD_FILE" ] || materials_lab_fail '业务数据库密码文件不可读'
MATERIALS_LAB_INIT_APP_PASSWORD=$(cat "$MATERIALS_LAB_DB_PASSWORD_FILE")
[ "${#MATERIALS_LAB_INIT_APP_PASSWORD}" -ge 32 ] || materials_lab_fail '业务数据库密码至少需要 32 个字符'
case "$MATERIALS_LAB_INIT_APP_PASSWORD" in
    *'
'*) materials_lab_fail '业务数据库密码必须为单行' ;;
esac
export MATERIALS_LAB_INIT_APP_PASSWORD

# 用 psql 变量的标识符/字面量引用避免 SQL 注入；密码经环境读取，不进入命令行参数。
# 本地 Unix socket 仅供初始化阶段管理连接使用，不对宿主发布数据库端口。
psql --no-psqlrc --no-password --host=/var/run/postgresql --username=postgres --dbname=postgres \
    --set=ON_ERROR_STOP=1 --set=ECHO=none <<'SQL'
\getenv materials_lab_user MATERIALS_LAB_DB_USER
\getenv materials_lab_database MATERIALS_LAB_DB_NAME
\getenv materials_lab_password MATERIALS_LAB_INIT_APP_PASSWORD
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
CREATE ROLE :"materials_lab_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'materials_lab_password';
ALTER ROLE :"materials_lab_user" SET timezone TO 'Asia/Shanghai';
COMMENT ON ROLE :"materials_lab_user" IS '材料实验助手生产业务与结构迁移专用角色';
CREATE DATABASE :"materials_lab_database" OWNER :"materials_lab_user";
ALTER DATABASE :"materials_lab_database" SET timezone TO 'Asia/Shanghai';
REVOKE ALL ON DATABASE :"materials_lab_database" FROM PUBLIC;
COMMENT ON DATABASE :"materials_lab_database" IS '材料实验助手独立生产数据库';
\connect :materials_lab_database
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO :"materials_lab_user";
SQL
unset MATERIALS_LAB_INIT_APP_PASSWORD
printf '%s\n' '生产业务数据库与非超级用户已初始化；未创建任何开发测试数据。'

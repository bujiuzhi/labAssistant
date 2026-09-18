# Production Docker Compose Deployment

[English](production-deployment.en.md) | [简体中文](production-deployment.md)

## First installation

Install Git, Docker Engine, and the Docker Compose plugin on the server. Run subsequent operations as the same OS user. The server needs access to base images; installation builds the application and runs its build checks.

```bash
mkdir -p ~/work/server
cd ~/work/server
git clone --branch main https://github.com/bujiuzhi/labAssistant.git
cd labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
bash scripts/deploy.sh install
```

The defaults work without editing. Change them only if needed:

```ini
APP_PORT=13501
ADMIN_USERNAME=admin
ADMIN_DISPLAY_NAME=平台管理员
```

Open `http://server-ipv4:13501`. Paths, release tags, database settings, and secrets are automatic. Optional `PUBLIC_HOST=server-ipv4` restricts the accepted Host; the default `auto` accepts valid IPv4 Hosts, not domains or IPv6.

Configuration is data, not a shell script: no quotes, expansion, inline comments, or surrounding spaces. Never commit the real `.env.production`. Bootstrap creates only the platform administrator, internal platform organization, and permission catalog: zero business organizations, tenant users, projects, experiments, or development fixtures. Use the platform administrator to provision an organization and its first administrator.

Read the generated initial password on the server:

```bash
cat ~/work/server/labAssistant/data/production-secrets/bootstrap_platform_admin_password
```

Existing secrets are retained. Configuration edits, restarts, and upgrades do not reset existing account passwords. After changing the password in the application, this initial-password file is not updated.

> Only Web publishes a host port. API, PostgreSQL, and RustFS remain private; Redis is not used. HTTP does not encrypt passwords, cookies, or uploaded content. Restrict the port to trusted sources using the firewall/security group. Enable HTTPS before broader access or sensitive-data use.

## Upgrades and maintenance

Review the release and migrations, arrange a brief maintenance window, then run from the project directory:

```bash
git pull --ff-only origin main
bash scripts/deploy.sh upgrade
```

The wrapper pulls pinned infrastructure images, builds the clean checked-out commit with a unique tag, stops writers, backs up PostgreSQL and RustFS, then starts and checks the release. Build failures leave existing services running. Failures after entering the write-stop phase leave writers stopped; there is no automatic database or application rollback. No manual SHA lookup or additional routine `backup` is needed.

| Operation | Command |
| --- | --- |
| Status | `bash scripts/deploy.sh status` |
| Health check | `bash scripts/deploy.sh check` |
| Restart deployed API/Web | `bash scripts/deploy.sh restart` |
| Independent backup; leaves services stopped | `bash scripts/deploy.sh backup` |
| Resume services after backup | `bash scripts/deploy.sh up` |
| Resume a failed deployment's target version | `bash scripts/deploy.sh resume` |
| Remove containers/network, retain data, secrets, and images | `bash scripts/deploy.sh uninstall` |

`install` refuses existing deployment state, data, or project containers. Use `up` after uninstall, not another `install`. No shortcut permanently deletes data. Apply port edits with `upgrade`; `restart` and `up` retain the last successful release and configuration even after pulling new code.

The compact workflow supports one instance per Docker daemon, with Compose project name `materials-lab-production`. Always operate it as the same OS user. Multiple instances, custom paths, and offline image delivery use advanced configuration.

## Storage and state

Paths are resolved from the executing user's HOME:

```text
~/work/server/labAssistant/
├── .env.production
└── data/
    ├── production-secrets/
    └── deployment/
        ├── active.env          # last successful full configuration
        ├── incomplete.env      # only while deployment is incomplete
        └── release.env.*       # per-build configuration snapshots

~/work/data/labAssistant/
├── postgres/
├── rustfs/data/
├── rustfs/logs/
├── backups/
└── releases/                  # manifests binding Git SHA and image IDs
```

API/Web standard output is managed by Docker's logging driver, not mapped into individual files. Secret directories use mode `0700`; file-backed Compose secrets are not encrypted and are readable by host/Docker administrators.

A recovery unit contains a database dump, RustFS archive, two checksum files, and metadata. Keep all five files together and preserve secrets separately. Arrange encrypted off-server copies, capacity monitoring, and a retention policy; historical backups and images are not automatically deleted.

## Interrupted deployments and recovery

`incomplete.env` is written before startup; `active.env` changes only after success. An incomplete deployment blocks `install/upgrade/up/restart` from implicitly starting old code. Inspect the failure and fix its cause, then run:

```bash
bash scripts/deploy.sh resume
```

This performs only target-version `up` and health checks: no rebuild, bootstrap, or database rollback. If first-time bootstrap never completed, retain data, secrets, logs, and the failed configuration. Only after confirming the database is still empty, run the guarded initialization:

```bash
bash scripts/production.sh --env "$HOME/work/server/labAssistant/data/deployment/incomplete.env" bootstrap --confirm materials-lab-production
bash scripts/deploy.sh resume
```

For partial data or migration failures, do not remove the marker or repeatedly initialize. Make a forward fix or restore to a new isolated environment. Never edit an executed V1 migration; subsequent schema changes require V2, V3, and so on. `production.sh restore-new` requires a different project name, fresh data path, and empty database. See `bash scripts/production.sh --help` for advanced arguments.

Advanced `rollback <previous-tag> --schema-compatible` rolls back application images only and requires verified schema compatibility. It does not update compact-workflow state. Do not temporarily roll back with `active.env` and then continue using the compact wrapper: switch to a verified full configuration for the selected older release, or a later restart could select the wrong image. Incompatible schemas require forward repair or isolated recovery.

## Existing deployments and advanced mode

Keep existing `MATERIALS_LAB_*` configuration files; **do not overwrite them with the compact template**. The wrapper detects legacy configuration and delegates to the existing engine, retaining project name, paths, secrets, and explicit release tags. It neither moves data nor manages legacy versions automatically. Legacy upgrades still require a new configured tag and an explicit build:

```bash
bash scripts/production.sh --env "$PWD/.env.production" build
bash scripts/deploy.sh upgrade
```

Use the [full template](../infra/.env.production.full.example) and [advanced engine](../scripts/production.sh) for custom paths, multiple instances, and offline delivery. The legacy workflow requires the pinned PostgreSQL/RustFS images to be available in advance. Only a full configuration can be passed directly to production Compose; the three-field compact file cannot.

## Validation

```bash
bash -n scripts/deploy.sh scripts/production.sh
node --test scripts/tests/production.test.mjs
```

These tests use isolated Git/Docker doubles, not production data. Real isolated integration acceptance uses `scripts/tests/production-acceptance.mjs --release <tag> --manifest <absolute-path>`. Neither replaces target-server acceptance: verify public access, authentication, tenant/project authorization, document uploads/downloads, ELN, exposed ports, and recovery. Health checks prove readiness only.

Record releases in `audit/logs/` with Asia/Shanghai timestamps, versions, migrations, recovery units, and verification results. Do not record credentials or business content.

# Production Docker Compose Deployment

[English](production-deployment.en.md) | [简体中文](production-deployment.md)

## Scope and network boundary

Production uses Docker Compose with code and private configuration in `~/work/server/labAssistant` and persistent data in `~/work/data/labAssistant`.

The current minimal profile intentionally exposes one HTTP entry:

```text
http://<public-ipv4>:13501
```

Only Nginx/Web maps a host port. API, PostgreSQL, RustFS API, and the RustFS console are not host-published. This profile has no domain, TLS certificate, HTTPS, HSTS, or Secure cookie flag.

> HTTP exposes credentials, session cookies, and uploaded content without transport encryption. Restrict `13501/TCP` to trusted sources. Move to HTTPS before broadening access, handling higher-sensitivity data, or claiming encrypted public transport.

## Production assets

| Asset | Purpose |
| --- | --- |
| [`infra/compose.production.yml`](../infra/compose.production.yml) | PostgreSQL, RustFS, API, Web, bootstrap task, and internal networks |
| [`infra/.env.production.example`](../infra/.env.production.example) | Non-sensitive deployment template |
| [`scripts/production.sh`](../scripts/production.sh) | Controlled install, build, upgrade, restart, backup, restore, and rollback operations |
| [`infra/Dockerfile.api`](../infra/Dockerfile.api) | Java 25 API build and runtime image |
| [`infra/Dockerfile.web`](../infra/Dockerfile.web) | Frontend test/build and Nginx runtime image |

Release manifests bind a Git SHA to the API and Web image IDs. `up`, `upgrade`, and `rollback` reject a manifest/image mismatch. Runtime operations do not build from the working tree.

## One-time configuration

```bash
cd ~/work/server/labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
chmod 600 .env.production
```

Set every `CHANGE_ME` value. Keep `.env.production` private: no quotes, shell expansion, inline comments, or Git tracking.

```ini
MATERIALS_LAB_PUBLIC_HOST=<public-ipv4>
MATERIALS_LAB_PUBLIC_PORT=13501
MATERIALS_LAB_HTTP_BIND_ADDRESS=0.0.0.0
MATERIALS_LAB_HTTP_BIND_PORT=13501
```

The first installation creates only the internal platform organization, its platform-administrator account, and the permission catalog. It creates zero business organizations, tenant user accounts, projects, experiments, documents, or development fixtures.

The initial login is `MATERIALS_LAB_BOOTSTRAP_PLATFORM_ADMIN_USERNAME`. The generated password is stored in `bootstrap_platform_admin_password` under the configured `MATERIALS_LAB_SECRETS_DIR`; retrieve it securely on the server. Restarting or upgrading does not reset an existing account's database password. After signing in, provision a business organization and its first organization super administrator, then use an organization account for business operations.

## Common commands

Set the actual Compose project name from `.env.production` in every command.

```bash
cd ~/work/server/labAssistant
LABASSISTANT_PROD_ENV="$PWD/.env.production"

# First deployment on an empty database
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" install --confirm materials-lab-production

# Release reviewed source: use a new release tag, build, then deploy it
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" build
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" upgrade --confirm materials-lab-production

# Restart the currently deployed release
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" restart --confirm materials-lab-production

# Optional independent recovery point; writers stay stopped afterwards
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" backup --confirm materials-lab-production
```

`install` is only for an empty database. `upgrade` already stops writers and makes a recovery unit, so do not make an additional manual backup as a routine prerequisite. `backup` is for an explicitly requested independent recovery point, migration, or higher-risk operation; use `up --confirm` to resume after it completes.

## Release, rollback, and recovery

Each release uses a new immutable tag. Review code, migration compatibility, and the write-stop window before `upgrade`. The script stops API/Web and RustFS, creates a consistent PostgreSQL dump plus RustFS archive, then starts the new images. A failed upgrade leaves writers stopped and does not automatically roll back the database.

Do not edit an executed Flyway migration. For a schema-compatible issue, only the application image may be rolled back:

```bash
bash scripts/production.sh --env "$LABASSISTANT_PROD_ENV" rollback <previous-tag> \
  --schema-compatible --confirm materials-lab-production
```

For incompatible schema changes, restore to a new isolated environment or make a forward fix. `restore-new` refuses to overwrite an existing data directory or database.

## Required acceptance

After first install or release, verify the public URL, login/logout, tenant and project authorization, document/attachment operations, ELN lifecycle, firewall exposure, and recovery media. Health checks alone do not prove business acceptance.

For the full Chinese operational detail, including persistent layout and recovery-unit contents, see [生产 Compose 部署](production-deployment.md).

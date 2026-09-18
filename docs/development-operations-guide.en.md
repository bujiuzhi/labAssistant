# Development and Operations Guide

[English](development-operations-guide.en.md) | [简体中文](development-operations-guide.md)

## Environments

| Environment | Entry | Data rule |
| --- | --- | --- |
| Development | `infra/docker-compose.yml` | Uses isolated development fixtures; never connect it to production storage or databases. |
| Production | `infra/compose.production.yml` through `scripts/deploy.sh` | Uses a small private `.env.production`; directories, release tags, and secrets are managed automatically. `production.sh` remains the advanced engine. |

Do not turn development Compose into production by changing a profile variable. The production Compose profile has separate networks, storage, bootstrap safeguards, and release-manifest checks.

## Development commands

```bash
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d

mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
```

Host Maven requires Java 25. The API Dockerfile uses a fixed Java 25 build stage and executes `mvn verify` during a production image build.

## Production paths

Use separate project directories when deploying to a server:

```text
~/work/server/labAssistant/  code and private .env.production
~/work/data/labAssistant/    PostgreSQL, RustFS, backups, and release manifests
```

The compact workflow derives these paths automatically. In advanced full configuration, `MATERIALS_LAB_DATA_ROOT` must be an absolute server path. Never point a new deployment at another project's data directory or use a literal `~` in the environment value.

## Operations boundary

| Command | Intended use | Downtime and data effect |
| --- | --- | --- |
| `install` | First start on an empty database | Initializes the platform control plane only. |
| `build` | Build reviewed source into a new immutable release tag | Does not stop services or change persistent data. |
| `upgrade` | Deploy a new built/imported release | Stops writers and creates a consistent PostgreSQL + RustFS recovery unit. |
| `restart` | Restart current API/Web image | No version change and no backup. |
| `backup` | Create a separate recovery unit | Stops writers; services remain stopped after completion. |
| `restore-new` | Restore a recovery unit into an empty, isolated target | Never overwrites a running or existing production data directory. |

This table describes the underlying engine. The compact `deploy.sh upgrade` also builds automatically; standalone `build` and `restore-new` remain advanced `production.sh` operations.

Use [production deployment](production-deployment.en.md) for exact commands, network exposure, release tags, and recovery preconditions.

## Acceptance boundary

Health checks and a successful build prove process and dependency readiness only. Before a production release is accepted, verify the actual public entry, login/logout, tenant and project authorization, document upload/download, ELN operations, and the recovery media/process appropriate to the deployment.

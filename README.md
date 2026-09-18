# Materials Lab Assistant

[English](README.md) | [简体中文](README.zh-CN.md)

Materials Lab Assistant is a multi-tenant web application for material R&D teams. It combines organization administration, project collaboration, electronic laboratory notebooks (ELN), and controlled document storage in one self-hosted deployment.

> This repository is public, but it is **not licensed for reuse yet**. See [License](#license) before copying, redistributing, or modifying the code.

## Highlights

- **Tenant isolation and RBAC** — a platform administrator provisions organizations; each account belongs to one organization; organization roles grant actions while project membership limits data scope.
- **Project collaboration** — projects, objectives, milestones, members, activity history, and document management.
- **ELN workflow** — experiment plans, participants, process photos, result attachments, status transitions, and completed-record immutability.
- **Private file storage** — PostgreSQL stores business metadata and authorization relationships; RustFS stores document and attachment bodies.
- **Self-hosted operations** — production Docker Compose keeps PostgreSQL, RustFS, API, and Web isolated and provides controlled install, release, recovery, and rollback commands.

Redis and LibreOffice-based document conversion are not part of the current release.

## Architecture

```text
Browser
  │ HTTP (current production profile)
  ▼
Nginx / Web ─────────────► Spring Boot API
                                  ├── PostgreSQL: business data, ELN, RBAC
                                  └── RustFS: project documents and attachments
```

Only the Web entry point is published by production Compose. The API, PostgreSQL, and RustFS remain on internal Docker networks.

## Quick start for development

Development Compose is isolated from production and initializes development fixtures. Never point it at production PostgreSQL, RustFS, credentials, or data directories.

### Prerequisites

- Docker Engine with Docker Compose v2
- JDK 25 only when running Maven directly on the host
- Node.js with Corepack/pnpm only when running frontend commands on the host; use the pnpm version declared in [`frontend/package.json`](frontend/package.json)

```bash
git clone https://github.com/bujiuzhi/labAssistant.git
cd labAssistant

if [ ! -e .env ]; then
  (umask 077; cp .env.example .env)
fi

pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose --env-file .env -f infra/docker-compose.yml ps
```

- Web: `http://127.0.0.1:5173/`
- API liveness: `http://127.0.0.1:8000/api/v1/health/live`
- API readiness: `http://127.0.0.1:8000/api/v1/health/ready`

For environment variables, fixture behavior, data paths, and troubleshooting, read the [development and operations guide](docs/development-operations-guide.en.md).

## Quality checks

```bash
# Backend: requires JDK 25
mvn -f backend/pom.xml verify

# Frontend: requires locked dependencies
pnpm --dir frontend test
pnpm --dir frontend run build

# Production-operation script tests
node --test scripts/tests/production.test.mjs

# Whitespace and patch integrity
git diff --check
```

Passing these checks does not replace production acceptance for login, authorization, upload/download, firewall rules, or recovery.

## Production lifecycle

Production Compose expects code and private configuration in `~/work/server/labAssistant` and persistent data in `~/work/data/labAssistant`. Copy [`infra/.env.production.example`](infra/.env.production.example) to a private `.env.production`; do not commit it.

| Situation | Command | Effect |
| --- | --- | --- |
| First deployment on an empty database | `install` | Creates missing secrets and directories, builds, initializes the empty production database, and starts services. |
| Release a reviewed version | `build` then `upgrade` | Builds or imports immutable images; `upgrade` stops writes, creates a PostgreSQL + RustFS recovery unit, then starts the new version. |
| Safe service restart | `restart` | Restarts API and Web only, then waits for health checks. |
| Independent recovery point | `backup` | Stops writers and creates a recovery unit; services remain stopped until `up` is run. Not a routine online backup. |

Use the full commands and preconditions in the [production deployment guide](docs/production-deployment.en.md). `install` is only for an empty database. `upgrade` never builds from an unreviewed working tree and already creates its own recovery unit; do not run an extra manual `backup` before every ordinary upgrade.

The currently documented minimal deployment profile exposes trusted users through HTTP at `http://<public-ip>:15105`. HTTP sends credentials, cookies, and uploaded content without transport encryption. Restrict `15105/TCP` to trusted sources and move to HTTPS before expanding access or handling higher-sensitivity data.

## Documentation

| English | 中文 |
| --- | --- |
| [Documentation index](docs/README.md) | [文档索引](docs/README.zh-CN.md) |
| [System overview](docs/overview-design.en.md) | [系统概要设计](docs/overview-design.md) |
| [Detailed design](docs/detailed-design.en.md) | [详细设计](docs/detailed-design.md) |
| [Production deployment](docs/production-deployment.en.md) | [生产 Compose 部署](docs/production-deployment.md) |
| [Development and operations](docs/development-operations-guide.en.md) | [开发与运维指南](docs/development-operations-guide.md) |
| [OpenAPI contract](contracts/openapi.yaml) | [OpenAPI 契约](contracts/openapi.yaml) |

Historical sources, decisions, and verification evidence live in [audit/](audit/README.md). They are historical records, not a description of the current runtime behavior.

## Repository layout

```text
labAssistant/
├── backend/       Spring Boot API, MyBatis, Flyway, and JUnit tests
├── frontend/      Vue application, TypeScript types, and frontend tests
├── infra/         Compose files, Dockerfiles, Nginx, and configuration templates
├── scripts/       Production operation script and isolated tests
├── contracts/     OpenAPI contract
├── docs/          Current design and operations documentation
└── audit/         Historical sources, decisions, and verification evidence
```

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull request. Changes affecting authorization, data, storage, configuration, migrations, or production operations require matching tests and documentation.

## Security

Do not report vulnerabilities, credentials, session data, or business data in public issues. See [SECURITY.md](SECURITY.md) for the private reporting expectations and deployment responsibilities.

## License

No open-source license has been granted for this repository. Public visibility does not grant permission to reuse, redistribute, or sublicense its contents. A license must be added by the rights holder before such permissions exist.

<div align="center">
  <h1>🔬 Materials Lab Assistant</h1>

### Project collaboration and electronic lab notebooks for materials R&D

**Keep project plans, experiment records, and research files connected.**

![Java](https://img.shields.io/badge/Java-25-ed8b00) ![Vue](https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169e1?logo=postgresql&logoColor=white) ![Deployment](https://img.shields.io/badge/Docker-Compose-2496ed?logo=docker&logoColor=white) [![GitHub Stars](https://img.shields.io/github/stars/bujiuzhi/labAssistant?style=flat&logo=github&label=Stars)](https://github.com/bujiuzhi/labAssistant/stargazers)

**English** · [简体中文](README.zh-CN.md)

[![License: AGPL-3.0-only](https://img.shields.io/badge/license-AGPL--3.0--only-663399)](LICENSE)

[Quick start](#quick-start) · [Features](#features) · [Accounts and permissions](#accounts-and-permissions) · [Upgrades and maintenance](#upgrades-and-maintenance) · [Local development](#local-development) · [Documentation](#documentation)
</div>

---

Materials Lab Assistant brings organizations, projects, experiment plans, lab records, and documents into one self-hosted workspace. It serves materials R&D teams that need organization-level data isolation, project-based collaboration, and a shared history of experimental work.

| 🧪 Experiment records | 👥 Team collaboration | 📦 Self-hosted deployment |
| :---: | :---: | :---: |
| Plans, process photos, and result attachments | Tenant isolation, roles, and project membership | Docker Compose with data on your own server |

## Quick start

The server needs Docker Engine, Docker Compose **2.24.4+**, Git, Bash, OpenSSL, and standard archive tools. Images provide the Java and frontend build environments.

### 1. Get the code and configuration

```bash
mkdir -p ~/work/server
git clone --branch main https://github.com/bujiuzhi/labAssistant.git ~/work/server/labAssistant
cd ~/work/server/labAssistant
if [ ! -e .env.production ]; then
  (umask 077; cp infra/.env.production.example .env.production)
fi
```

The defaults work as provided; edit only what you need:

```ini
APP_PORT=13501
ADMIN_USERNAME=admin
ADMIN_DISPLAY_NAME=平台管理员
```

No public IP, directory paths, or Git revision needs to be entered. The script fetches dependency images, derives project-specific paths from the current user's home directory, generates missing secrets, and assigns a unique release tag to the current commit.

### 2. Install and open the application

```bash
bash scripts/deploy.sh install
```

Open **`http://server-ip:13501`**. Installation creates only the platform administrator and internal platform organization, with **zero business organizations**, sample projects, or development accounts.

The initial password is stored at `~/work/server/labAssistant/data/production-secrets/bootstrap_platform_admin_password`; the default login is `admin`. Upgrades preserve it. Changing administrator settings does not reset an existing account.

> [!IMPORTANT]
> `install` is for a new deployment. Existing full `MATERIALS_LAB_*` configurations remain supported; keep them instead of overwriting them with the new template. HTTP is unencrypted; restrict the entry port to trusted sources. Automatic access supports IPv4 addresses.

## Features

| Capability | Description |
| --- | --- |
| Project overview | View accessible project cards and project/experiment metrics |
| Project collaboration | Manage objectives, milestones, owners, project members, and archiving |
| Electronic lab notebooks | Record experiment plans, participants, process notes, photos, and result attachments |
| Experiment lifecycle | Track not-started, in-progress, and completed states; completed records are read-only |
| Document management | Categorize, upload, and download authorized documents; preview supported formats in the browser |
| Multi-tenancy and RBAC | Organization isolation, role permissions, and project-membership data scope |
| Account management | Invitation registration, administrator-created users, deactivation, logical deletion, and password management |
| Deployment and recovery | Dedicated Compose services, upgrade backups, application rollback, and recovery into an empty target |

PostgreSQL stores business metadata; RustFS stores new document and attachment bodies. Redis and LibreOffice conversion are not part of the current release. See the [system overview](docs/overview-design.en.md) for scope and limitations.

## Accounts and permissions

The platform administrator provisions an organization and its first super administrator together. Organization administrators then manage their own members and invitations. Each account belongs to one organization.

| Identity | Responsibility and scope |
| --- | --- |
| Platform administrator | List and provision organizations; no access to tenant business data |
| Organization super administrator | Manage users, invitations, and business operations within the organization |
| Project administrator | Manage authorized projects, members, documents, and experiments |
| Researcher | Read assigned projects and work on related experiments where lifecycle rules allow |

Roles grant actions; project membership and other relationships determine accessible data. The backend checks both. See the [authorization design](docs/detailed-design.en.md).

## Upgrades and maintenance

Synchronize reviewed code from `main` in the project directory, then upgrade:

```bash
git pull --ff-only origin main
bash scripts/deploy.sh upgrade
```

An upgrade builds a new release, stops writes, backs up PostgreSQL and RustFS, then starts services. Build failures leave the old services running. Failures after entering the write-stop phase leave writers stopped to prevent an implicit rollback to potentially incompatible old code.

| Operation | Command |
| --- | --- |
| View status | `bash scripts/deploy.sh status` |
| Restart the deployed version | `bash scripts/deploy.sh restart` |
| Check health | `bash scripts/deploy.sh check` |
| Back up and keep writers stopped | `bash scripts/deploy.sh backup` |
| Resume after a backup | `bash scripts/deploy.sh up` |
| Continue starting a failed deployment's target version | `bash scripts/deploy.sh resume` |
| Uninstall services, preserving data, secrets, and images | `bash scripts/deploy.sh uninstall` |

Apply port changes with `upgrade`; `restart` always uses the last successfully deployed configuration and release. See the [deployment guide](docs/production-deployment.en.md) for interrupted initialization, legacy configuration, database recovery, and schema-compatible rollback.

## Technology and layout

| Layer | Technology |
| --- | --- |
| Frontend | Vue 3, TypeScript, Element Plus, Vite |
| Backend | Java 25, Spring Boot, MyBatis |
| Business data | PostgreSQL, Flyway |
| File storage | RustFS |
| Deployment | Docker Compose, Nginx |

```text
labAssistant/
├── backend/       # API, business logic, database migrations, and tests
├── frontend/      # Pages, components, and frontend tests
├── infra/         # Compose, Dockerfiles, Nginx, and configuration templates
├── scripts/       # Production operations and isolated tests
├── contracts/     # OpenAPI contract
├── docs/          # Current design and operations documentation
└── audit/         # Historical sources, changes, and verification records
```

Production code and private configuration live in `~/work/server/labAssistant`; persistent data lives in `~/work/data/labAssistant`. Each project has its own directories.

## Local development

Use `dev` for development. Host backend commands require JDK 25; frontend commands use the project's locked pnpm version. Development Compose initializes fixtures and must use separate databases and storage.

See the [development and operations guide](docs/development-operations-guide.en.md) for setup and startup commands. Run checks appropriate to the changed area:

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
git diff --check
```

Read the [contributing guide](CONTRIBUTING.md) before making changes. Successful tests, builds, and health checks do not replace target-server acceptance for login, permissions, attachments, and recovery.

## Documentation

| Document | Contents |
| --- | --- |
| [Documentation index](docs/README.md) | English and Chinese documentation |
| [Production deployment](docs/production-deployment.en.md) | Installation, upgrades, backups, restore, and rollback |
| [System overview](docs/overview-design.en.md) | Product scope, roles, and architecture |
| [Detailed design](docs/detailed-design.en.md) | Data, API, authorization, and lifecycle rules |
| [Development and operations](docs/development-operations-guide.en.md) | Environment setup, configuration, verification, and troubleshooting |
| [OpenAPI](contracts/openapi.yaml) | HTTP API contract |
| [Security policy](SECURITY.md) | Vulnerability reporting and deployment responsibilities |

## License

Materials Lab Assistant is licensed under the [GNU Affero General Public License v3.0 only](LICENSE) (`AGPL-3.0-only`). Commercial use is permitted under the license. Distribution of covered works carries corresponding-source obligations; modified versions offered over a network must also offer their corresponding source to users interacting with them remotely.

The license includes the contributor patent grant in section 11. Third-party components remain subject to their own licenses. See [LICENSE](LICENSE) for the complete terms, including the warranty disclaimer.

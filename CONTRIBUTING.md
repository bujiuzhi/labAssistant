# Contributing

[English](CONTRIBUTING.md) | [简体中文](CONTRIBUTING.zh-CN.md)

Thank you for improving Materials Lab Assistant. Keep each contribution focused, state its compatibility impact, and provide evidence appropriate to the risk.

## Before you start

- Read the [documentation index](docs/README.md), [security policy](SECURITY.md), and applicable design documents.
- Discuss changes affecting authorization, tenancy, data storage, migrations, deployment, or recovery before implementation.
- Do not use development Compose, fixtures, or local configuration against production databases, RustFS instances, credentials, or data directories.

## Local setup

```bash
pnpm --dir frontend install --frozen-lockfile
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
```

Host Maven requires JDK 25. Use the pnpm version locked by `frontend/package.json` and `frontend/pnpm-lock.yaml`.

## Change requirements

- Do not commit `.env` files, credentials, production connection details, generated artifacts, dependencies, business data, or sensitive logs.
- Add schema changes only through a new Flyway migration. Never rewrite a migration that may already have run.
- Update the matching design document, OpenAPI contract, configuration template, and/or audit record when behavior changes.
- Use existing commit prefixes such as `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, or `chore:`. Keep the Chinese subject concise and accurate.
- Add behavior-focused tests for new or changed authorization, persistence, lifecycle, or UI behavior. Do not weaken existing checks to make a change pass.

## Verification

Run the checks that apply to the changed area:

```bash
mvn -f backend/pom.xml verify
pnpm --dir frontend test
pnpm --dir frontend run build
node --test scripts/tests/production.test.mjs
git diff --check
```

Explain any check that is not applicable or could not be run. Build and unit-test success do not prove production authorization, file upload, network, or recovery acceptance.

## Pull requests

Use `dev` for day-to-day integration. `main` is the release branch. Do not target `main` directly for ordinary development.

Include in every pull request:

- purpose and scope;
- affected API, configuration, migration, authorization, or user behavior;
- verification commands and results;
- deployment, rollback, data-migration, or security impact where applicable.

Maintainers may request a narrower diff, tests, documentation, compatibility notes, or isolated-environment validation before merging.

# Security Policy

[English](SECURITY.md) | [简体中文](SECURITY.zh-CN.md)

## Supported scope

There is no separately supported long-term release line yet. Security fixes target the maintained current code. Deployers must review changes, create a recovery point through the documented release process, and validate in an isolated environment before production rollout.

## Reporting a vulnerability

Do **not** disclose exploit details, credentials, session identifiers, business data, accessible production addresses, screenshots containing sensitive data, or proof-of-concept code in public issues, pull requests, commits, or logs.

This repository does not publish a dedicated security email or response SLA. Use an agreed private channel with the maintainers and include:

- affected commit, release tag, or deployment path;
- minimal reproducible steps and preconditions;
- impact and suggested mitigation;
- sanitized logs, requests, or screenshots only.

Maintainers should confirm scope, coordinate a fix and validation, then agree on disclosure timing. Do not publish details before a fix or mitigation is available.

## Deployment responsibilities

- Use unique, strong secrets and project-isolated data directories.
- Keep PostgreSQL, RustFS, and the API off host-published ports.
- Restrict access to the documented Web entry point.
- Treat the selected `HTTP IP:15105` profile as unencrypted transport; it is only appropriate for trusted, limited-access users who accept that risk.
- Back up PostgreSQL and RustFS as one recovery unit before a release or other planned high-risk operation; test restores in a separate empty environment.

See the [production deployment guide](docs/production-deployment.en.md) for current operational constraints.

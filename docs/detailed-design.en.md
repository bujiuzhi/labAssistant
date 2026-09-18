# Detailed Design

[English](detailed-design.en.md) | [简体中文](detailed-design.md)

This document summarizes the current implementation contract. Source code, Flyway migrations, Compose configuration, and [OpenAPI](../contracts/openapi.yaml) take precedence if a discrepancy is found.

## Components

| Component | Responsibility |
| --- | --- |
| `backend/identity` | Login, sessions, organization isolation, users, roles, invitations, and permission resolution |
| `backend/projects` | Projects, project members, dashboard, project documents, and operation history |
| `backend/experiments` | Experiment plans, ELN records, participants, status transitions, and attachments |
| `frontend` | Vue UI, session state, form validation, API calls, and client-side previews |
| `backend/src/main/resources/db/migration` | The only source for PostgreSQL schema evolution |

## Authorization contract

A protected operation requires all applicable conditions:

1. A valid, active session subject.
2. The target belongs to the subject's organization.
3. A role permission for the action, unless the account is the current organization's super administrator.
4. A matching data-scope relationship for projects, experiments, documents, and attachments.
5. Lifecycle constraints, including completed experiments being immutable.

Project list and experiment list queries include project ownership or `project_member` conditions. Project create and PATCH operations accept `member_ids`; supplied IDs must identify active accounts in the same organization and are synchronized transactionally as `researcher` project members. Omitted `member_ids` in PATCH preserve current ordinary members; an empty array clears ordinary members. The owner and creator-management relationships are not removed by this synchronization.

## Data and storage

| Asset | Authoritative storage | Notes |
| --- | --- | --- |
| Tenants, users, roles, projects, experiments, ELN metadata | PostgreSQL | Includes organization scope and Flyway history |
| New project documents and experiment attachments | RustFS | PostgreSQL stores metadata and object references |
| Historical document/attachment bodies | PostgreSQL compatibility tables | Read compatibility only where present |

The database and RustFS data directory form one recovery unit. Backing up metadata without object data cannot restore uploaded files.

## Lifecycle rules

- Experiment status is ordered: `not_started → in_progress → completed`.
- Completing an experiment records completion time and prevents later edits or transitions.
- Project archive rejects concurrent stale writes through optimistic version checks.
- `If-Match` is required for the guarded project and experiment write paths described by OpenAPI.
- Existing Flyway migrations are immutable. Add a new migration for every released schema change.

## Explicit limitations

- No generic ACL, ABAC, or ReBAC engine exists.
- No account can belong to multiple organizations or switch organizations.
- Document conversion and antivirus/malware scanning are not built into the current release.
- An application rollback does not downgrade database schema; use a compatible previous image, a forward fix, or an isolated recovery according to the deployment guide.

The Chinese [detailed design](detailed-design.md) retains the full field-level and page-level implementation notes for the current release.

# System Overview

[English](overview-design.en.md) | [简体中文](overview-design.md)

Materials Lab Assistant is a self-hosted web application for material R&D teams. An organization is the tenant boundary; projects organize collaboration; experiments and ELN records capture planned, in-progress, and completed work.

## Scope

| Area | Supported boundary |
| --- | --- |
| Identity and tenancy | Local password login, session and CSRF protection, organization provisioning, invitation-based registration, self-service password change, and ordinary-user lifecycle management. |
| Projects | Read, create, update, archive, follow, document, milestone, and member-management flows. |
| ELN | Experiment planning, participants, incremental record updates, status transitions, process images, and result attachments. Completed experiments are read-only. |
| Storage | PostgreSQL for metadata and authorization relationships; RustFS for new document and attachment bodies. |
| Operations | Docker Compose deployment with PostgreSQL, RustFS, API, and Web isolated per project. |

Redis, LibreOffice conversion, generic ACL/ABAC/ReBAC policy engines, cross-organization account membership, and organization switching are not part of the current release.

## Architecture

```text
Browser → Nginx / Vue Web → Spring Boot API → PostgreSQL
                                          └→ RustFS
```

The production Compose profile exposes only Web. API, PostgreSQL, and RustFS are internal services. See [production deployment](production-deployment.en.md) for the public HTTP boundary selected for this release.

## Access model

The implementation uses **tenant-scoped RBAC with project-membership data scope**:

1. An `organization_id` is the tenant boundary. An account belongs to one organization and cannot switch organizations.
2. Organization roles grant action permissions such as `project.read`, `document.upload`, or `experiment.transition`.
3. Project ownership, project membership, experiment ownership, and experiment participation limit which objects an authorized account can access.
4. The platform administrator manages organization provisioning only. It does not read tenant business data.

| Role | Main responsibility | Data boundary |
| --- | --- | --- |
| Platform administrator | Provision and list organizations | Internal platform control plane only |
| Organization super administrator | Manage users, invitations, and tenant-wide business operations | Current organization |
| Project administrator | Manage authorized projects, documents, experiments, and members | Project or direct experiment relationship |
| Researcher | Read assigned projects/documents/experiments and update directly related experiments | Project, experiment-owner, or participant relationship |

Project member selection writes a `researcher` membership relation. It grants data visibility only; it does not elevate an account to project administrator. Project ownership and creator management relationships remain separate.

## Runtime boundaries

- Production bootstrap creates one platform administrator and zero business organizations, projects, experiments, documents, or sample users.
- Every data-facing request is filtered by the current organization; authorization is enforced by backend services and SQL, not by UI visibility alone.
- RustFS objects are accessed through the API authorization path. Do not expose its API or console directly.
- The documented IP-and-port HTTP deployment is intentionally minimal and not transport-encrypted. Restrict access to trusted sources.

For schema, API, lifecycle, and implementation limits, read the [detailed design](detailed-design.en.md) and [OpenAPI contract](../contracts/openapi.yaml).

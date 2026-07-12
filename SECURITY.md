# Security Policy

Sillage stores private records, attachments, login sessions, and encrypted AI API keys. Do not disclose vulnerability details, real data, or secrets in public issues, discussions, logs, or screenshots.

## Reporting a Vulnerability

Please submit vulnerabilities privately through [GitHub Private Vulnerability Reporting](https://github.com/getsillage/sillage/security/advisories/new). Do not disclose vulnerability details, reproduction data, or secrets in public issues, discussions, logs, or screenshots.

A complete report should include:

- the affected version or commit;
- the impact and prerequisites for an attack;
- minimal reproduction steps;
- any known mitigations.

Use synthetic data when reproducing the issue. Maintainers will acknowledge the report within seven calendar days. Remediation progress and the timing of public disclosure will be coordinated in the private report. Security advisories will identify affected versions, mitigations, and upgrade requirements.

## Supported Versions

Security fixes target the latest release and `main`; older versions are not guaranteed to receive separate maintenance. Self-hosted instances should upgrade promptly after taking a complete backup.

## Deployment Responsibilities

- Sillage provides HTTP only. Public access must use an operator-managed HTTPS entry point configured outside this repository.
- The data directory and backups do not have an additional layer of full at-rest encryption. Restrict host permissions and protect data in transit.
- Do not commit `SESSION_SECRET`, `ENCRYPTION_SECRET`, AI API keys, or databases to the repository.
- Read the [Deployment Guide](docs/user/deployment.md) and [Data, Backup, and Recovery](docs/user/data.md) before exposing the port.

Changes to authentication, attachments, secrets, or external requests must also follow the [Security Development Boundaries](docs/development/security.md).

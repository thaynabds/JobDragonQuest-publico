# JobFly

> Language: [Português](README.md) | **English** | [Español](README.es.md)

A platform to manage a career and automate applications to jobs and internships, in Brazil and abroad. JobFly keeps everything that happens in each hiring process in one place, wherever the application came from: LinkedIn, Gupy, a company website, Instagram, a Facebook group, email or a referral.

**Author:** [Thayná Batista da Silva](https://github.com/thaynabds) · **Status:** in development, increment 1 (API core).

## What the solution does

### Working now (increment 1, tested)

- **Secure accounts.** Sign-up and login with Argon2id password hashing, 15-minute sessions renewed by a rotating token that revokes itself if stolen, lockout after failed attempts, and rate limiting.
- **Multiple professional profiles.** For example: "Java Developer", "Development Internship", "International roles". Each profile holds a goal, target titles, skills with level and years of use, CEFR language levels, experience, education, projects, courses, certifications, salary expectation, work modes, cities, availability and restrictions. Profiles never mix.
- **Job records with deduplication.** The same job arrives through different URLs (tracking parameters, another subdomain, another LinkedIn route) and JobFly recognizes it as one. It also matches the same company and title, ignoring "Ltd.", "Inc." and accents.
- **Explainable match analysis.**
  - A 0–100 score with the reason for each requirement: met, partial, not met or "needs human reading".
  - Eliminatory blockers (work mode, city, required language, seniority) are kept separate: a high score never hides a blocker.
  - The system picks the best-fitting profile for each job.
- **Unified application hub.**
  - Records applications made anywhere and prevents duplicates by URL, by company and title, and by idempotency key.
  - Tracks 19 stages: from job found to approved, rejected or closed, through screening, interviews, technical tests and offer.
- **Evidence-based updates.**
  - Every stage change records its source (you, email, portal, agent), the text that justifies it and a confidence level.
  - An automatic acknowledgement never becomes an approval, and silence never becomes a rejection.
  - Uncertain information becomes a suggestion that you accept or dismiss.
  - Automatic sources cannot reopen a closed process. Only you can.
- **Dashboard (API).** Kanban by stage, overview counters, filters by profile, company, country, platform and period, full history of each application, and a to-do center (your action, technical test, upcoming interview, deadline, suggestion to review, process with no update).
- **Audit trail** of every relevant operation, without storing sensitive content.

### Next increments

The detailed plan and the status of each acceptance criterion are in [docs/07-roadmap.md](docs/07-roadmap.md). In short:

1. React web dashboard (Portuguese and English).
2. Automatic job search through the public APIs of Greenhouse, Lever, SmartRecruiters and Ashby, plus a list of Monitored Companies.
3. Gmail and Outlook through OAuth with minimal permissions: message classification, automatic linking to the right application, labels and authorized replies. It never deletes email.
4. AI agents (Claude, OpenAI or a local model): analysis of eliminatory form questions, application review before submission, and tailored documents that use only real profile data.
5. An MCP server to use JobFly tools from Claude Desktop and Claude Code.
6. A "Send to JobFly" browser extension.
7. Versioned résumés, attachments and history import (CSV).

## Principles

- **Truthfulness.** The AI never invents experience, certificates, languages or skills.
- **Platform rules.** Official APIs and permitted paths only. CAPTCHA, MFA and blocks are never bypassed: JobFly tells you what needs your action.
- **You stay in control.** Accepting an offer, signing a contract, making a financial commitment or sharing sensitive data always requires your approval.
- **Privacy (LGPD/GDPR).** Data separated per user, secrets kept out of the code, cascading deletion.

## Architecture

A modular monolith in Java 21 and Spring Boot 4.1, with PostgreSQL and Flyway. Modules are split by responsibility (`identity`, `profiles`, `jobs`, `matching`, `applications`, `dashboard`, `audit`) and ready to become separate services when there is a reason to. Decisions and their rationale are in [docs/02-arquitetura.md](docs/02-arquitetura.md) (Portuguese).

```
JobFly/
├── jobfly-api/              REST API (Spring Boot)
├── docs/                    requirements, architecture, data, integrations, API, security, roadmap
├── auditoria-jobcopilot/    reference study and feature matrix
└── scripts/                 local setup and run
```

## Running it

Requirements: Java 21 and PostgreSQL 14 or newer installed (just the programs, no setup needed). Maven is bundled (`mvnw`).

```bash
# 1. Creates a PostgreSQL of your own in data/postgres (127.0.0.1:5433, no sudo)
#    and writes .env with random passwords and JWT secret
scripts/configurar-local.sh

# 2. Starts the API at http://127.0.0.1:8080 (reachable only from this machine)
scripts/iniciar-api.sh

# 3. In another terminal: runs the full flow against the running API
scripts/testar-api.py

# 4. Automated tests, with coverage in jobfly-api/target/site/jacoco
cd jobfly-api && ./mvnw verify
```

To stop: `Ctrl+C` on the API and `scripts/postgres-local.sh parar` for the database. After a reboot, run `scripts/postgres-local.sh iniciar` before the API. To use the system PostgreSQL (port 5432) instead, run `scripts/configurar-local.sh --sistema` (asks for sudo).

Environment variables are listed in [.env.example](.env.example). The API reference is in [docs/05-api.md](docs/05-api.md).

## Quality and security

- 81 automated tests (unit tests and integration tests against the full application).
- The build fails if line coverage of the critical modules drops below 80% (it is above 92% today).
- Security controls and tests are in [docs/06-seguranca.md](docs/06-seguranca.md): per-user isolation, CSRF, XSS, injection, brute force, token theft and request size.

## License

All rights reserved. Private repository.

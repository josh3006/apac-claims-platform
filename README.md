# Claims platform — full stack assessment

Backend-first claims prototype: Java 21 / Spring Boot 3.5, simple Angular 20 UI, PostgreSQL, and Kafka. One deployable backend keeps the assessment small and easy to explain. Local development can run without Docker using H2 and disabled Kafka publication.

## Start with Docker

From this directory:

```sh
docker compose up --build -d
docker compose logs -f backend
```

Open **http://localhost:4200**. Backend: **http://localhost:8080**. Health: `/actuator/health`.

| Role | Username | Password |
| --- | --- | --- |
| Claimant | claimant | claimant-demo |
| Officer | officer | officer-demo |

Copy `.env.example` to `.env` if you want to change passwords. These are demo accounts; no user registration. The login hint shows default passwords even when environment overrides are used.

PostgreSQL and Kafka data use named volumes. `docker compose down` stops services and preserves data. Do not add `-v` unless you intentionally want to delete the demo data.

**Validation caveat:** Docker engine access is denied in the implementation environment. Compose configuration was checked, but an actual container/PostgreSQL/Kafka run is still required. See [validation](docs/VALIDATION.md).

## Start without Docker

Prerequisites: Java **21**, Maven **3.8+**, Node **22.12+** (22.x), npm. The existing default Java 8 and Node 19 on this machine are too old. Select the right versions in your terminal; no system-wide changes are needed.

Terminal 1, from `backend`:

```sh
mvn -s settings-local.xml spring-boot:run
```

Terminal 2, from `frontend`:

```sh
npm ci
npm start
```

Open http://localhost:4200. Angular's development proxy forwards `/api` to port 8080. H2 stores data in `backend/data`; Kafka publication is disabled but transactional outbox events still accumulate. There is no email notification in this prototype.

For this Windows workspace, Java 21 is at `C:\java\amazon-corretto-21.0.10.7.1-windows-x64-jdk\jdk21.0.10_7`. Before Maven:

```powershell
$env:JAVA_HOME = 'C:\java\amazon-corretto-21.0.10.7.1-windows-x64-jdk\jdk21.0.10_7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

A Node 22 binary was downloaded into the ignored `.tools/package/bin` directory during implementation. If it is still present, before npm:

```powershell
$env:Path = "C:\workspaces\playground\claims-platform\.tools\package\bin;$env:Path"
node --version
```

These machine-specific paths are conveniences, not repository prerequisites. A fresh checkout should use its own Java 21 and Node 22 installations.

On Windows, `npm.cmd` can still select its adjacent old Node binary despite a PATH change. The included helper avoids that. From `frontend`, use:

```powershell
..\scripts\npm.ps1 ci
..\scripts\npm.ps1 start
# Or: ..\scripts\npm.ps1 run build
```

The helper uses the ignored project-local Node 22 if available, otherwise the installed Node 22. It does not install or change system tools.

## What to demonstrate

1. Sign in as claimant, submit a motor or property claim, inspect the reference and history.
2. Open a second browser window and sign in as officer. Pick up the claim.
3. Request information with a reason. The claimant sees the change on refresh or within 10 seconds.
4. Claimant sends information. Officer settles with an amount and reason, or rejects with a reason.
5. Inspect the history and dashboard. Closed claims leave outstanding exposure; settlements appear under their own currency.
6. Explain that estimated exposure is a sum of claimant-entered loss estimates, not an actuarial reserve or verified policy liability.

## Read the code

- [Angular guide](docs/ANGULAR_GUIDE.md): beginner-oriented mapping from Java concepts to Angular; follow the submission flow file by file.
- [Architecture and trade-offs](docs/ARCHITECTURE.md): lifecycle, security, money, Kafka, and deferred work.
- [API contract](docs/API.md): endpoints and examples.
- [Panel walkthrough](docs/WALKTHROUGH.md): demo script and questions to prepare.
- [AI journal](AI_JOURNAL.md): actual prompts/choices and space for your personal review decisions.
- [Validation](docs/VALIDATION.md): what actually ran and what remains unverified.

## Checks

```sh
cd backend
mvn -s settings-local.xml test
# From frontend:
npm ci
npm run build
# From repository root:
docker compose config --quiet
```

`settings-local.xml` routes this standalone assessment to Maven Central and keeps its dependency cache inside ignored `target/maven-cache`, avoiding unrelated global corporate repository settings. You can omit `-s settings-local.xml` when your standard Maven configuration works.

A live REST smoke script is included for Windows. With the backend running, execute `./scripts/smoke.ps1` from the repository root. It creates a real demo claim and exercises the full information-and-settlement workflow using the default demo passwords.

## GitHub

This folder is its own Git repository. Commits record real implementation checkpoints, authored by Codex. Once you choose a GitHub repository:

```sh
git remote add origin <your-repository-url>
git push -u origin HEAD
```

Review every submitted line and update the journal with your own accepted, challenged, and overridden choices. Do not claim generated decisions or unexecuted checks as your personal review.

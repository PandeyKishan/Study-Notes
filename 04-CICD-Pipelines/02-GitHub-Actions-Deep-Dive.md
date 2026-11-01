# GitHub Actions: Architecture, Syntax & Advanced Pipelines

> **Cluster 04 — Module 02**  
> **Level**: Beginner Foundations to Production & FAANG-Level Deep Dive  
> **Key References**: TechWorld with Nana, GitHub Official Documentation

---

## 🌱 0. The Beginner's Mental Model: What is GitHub Actions?

### The Question Every Beginner Asks:
*"I have my code on GitHub. How do I make tests run automatically every time I push, and build a Docker image without doing it manually on my laptop?"*

GitHub Actions is GitHub's built-in automation engine. It allows you to trigger automated tasks directly inside GitHub whenever something happens in your repository.

---

### The Real-World Analogy: The Professional Kitchen
*(TechWorld with Nana Mental Model)*

| GitHub Actions Term | Professional Kitchen Analogy | What It Actually Is |
| :--- | :--- | :--- |
| **Workflow** | **The Complete Dinner Service Plan** | The top-level automation YAML file saved inside `.github/workflows/ci.yml`. |
| **Event (`on:`)** | **The Order Bell Ringing** | The trigger that kicks off the workflow (e.g. `git push`, Pull Request created, or scheduled cron). |
| **Runner** | **The Clean Stainless-Steel Kitchen Counter** | The virtual machine (Ubuntu, Windows, macOS) provided by GitHub to execute your tasks. |
| **Job** | **A Specialized Kitchen Station** | A group of steps (e.g. "Test Station", "Build Station"). Jobs run in **parallel** unless you specify `needs: [job_name]`. |
| **Step** | **An Individual Task on the Recipe** | A command executed sequentially inside a Job (e.g. `run: npm test`). |
| **Action** | **A Pre-packaged Kitchen Appliance** | A reusable community plugin (e.g. `actions/checkout@v4` or `docker/build-push-action`). |

```mermaid
flowchart TD
    Event["🔔 Event Trigger:\ngit push on 'main'"] --> WF["Workflow:\n.github/workflows/deploy.yml"]

    subgraph Runner_VM1["Runner 1 (Ubuntu VM)"]
        J1["Job: test\n• Step 1: Checkout Code\n• Step 2: Setup Java 21\n• Step 3: Run mvn test"]
    end

    subgraph Runner_VM2["Runner 2 (Ubuntu VM)"]
        J2["Job: docker-build-push\n(needs: test)\n• Step 1: Checkout Code\n• Step 2: Build Docker Image\n• Step 3: Push to GHCR"]
    end

    WF --> J1
    J1 -->|Success! Passes Quality Gate| J2

    style Event fill:#fde047,stroke:#b45309,stroke-width:2px,color:#0f172a
    style WF fill:#93c5fd,stroke:#1d4ed8,stroke-width:2px,color:#0f172a
    style Runner_VM1 fill:#f1f5f9,stroke:#64748b,stroke-width:1.5px,color:#0f172a
    style Runner_VM2 fill:#f1f5f9,stroke:#64748b,stroke-width:1.5px,color:#0f172a
    style J1 fill:#86efac,stroke:#15803d,stroke-width:2px,color:#0f172a
    style J2 fill:#93c5fd,stroke:#1d4ed8,stroke-width:2px,color:#0f172a
```

---

## 📖 1. Comprehensive Study Notes: Understanding GitHub Actions from the Ground Up

### What is GitHub Actions? (Straightforward Definition & Context)
**GitHub Actions** is GitHub's native continuous integration, continuous delivery (CI/CD), and workflow automation platform. It empowers software teams to automate every phase of their software engineering lifecycle—from code linting and testing to security scanning, container packaging, and cloud deployment—directly within the GitHub repository.

Before the introduction of GitHub Actions, engineering organizations were forced to provision, operate, and maintain external automation tools such as Jenkins, TeamCity, or Bamboo:
* **The Legacy Friction**: Teams had to deploy dedicated virtual machines, install operating system dependencies, configure external webhooks, manage complex plugin matrices, and manually clean up build artifacts.
* **The GitHub Actions Advantage**: GitHub Actions unifies source code and delivery pipelines under a single roof. Workflows are defined declaratively as YAML code inside the `.github/workflows/` directory, allowing automation configurations to be versioned, branched, reviewed via Pull Requests, and audited exactly like production application code.

---

### What does it actually do? (Core Architecture & Execution Mechanics)
To effectively build and troubleshoot automated pipelines, developers must understand GitHub Actions' structural hierarchy and execution lifecycle:

1. **The Structural Hierarchy (Workflows $\to$ Events $\to$ Jobs $\to$ Steps $\to$ Actions)**:
   * **Workflow**:
     - *Book Definition*: A Workflow is a configurable automated process defined in a YAML file that executes one or more jobs when triggered by an event.
     - A configurable automated process stored in `.github/workflows/*.yml`. A repository can contain multiple workflows (e.g., `ci.yml` for testing PRs, `release.yml` for publishing container images, and `security-scan.yml` for nightly vulnerability audits).
   * **Event (`on:`)**:
     - *Book Definition*: An Event is a specific activity in a repository (such as a pull request, code commit, or schedule) that automatically triggers a workflow execution.
     - A specific trigger that instructs GitHub to execute a workflow. Triggers include code events (`push`, `pull_request`), schedules (POSIX cron expressions), external webhooks (`repository_dispatch`), or manual triggers (`workflow_dispatch`).
   * **Job**:
     - *Book Definition*: A Job is a collection of steps in a workflow that executes sequentially on the same runner instance.
     - A set of sequential steps executed on an assigned compute instance. By default, **all jobs within a workflow run in parallel**. When sequential execution is required, you declare dependencies using the `needs: [job_name]` keyword to construct a Directed Acyclic Graph (DAG).
   * **Runner**:
     - *Book Definition*: A Runner is an application and compute environment (hosted virtual machine or container) that executes the jobs defined in a GitHub Actions workflow.
     - The physical or virtual machine that hosts and executes a job. GitHub provides **GitHub-hosted Runners** (pre-configured, ephemeral Ubuntu, Windows, and macOS virtual machines that are created on-demand and destroyed immediately upon job completion) and **Self-Hosted Runners** (custom virtual machines or Kubernetes pods running inside your private VPC for access to internal databases or specialized hardware).
   * **Step**:
     - *Book Definition*: A Step is an individual task within a job that can run commands or invoke reusable actions sharing the runner's filesystem.
     - An individual task executed sequentially inside a job. Steps within the same job share the runner's local filesystem and environment variables. Steps can either execute raw shell scripts (`run: mvn clean test`) or invoke pre-built actions (`uses: actions/checkout@v4`).
   * **Action**:
     - *Book Definition*: An Action is a reusable, standalone extension or script that performs a complex, frequently repeated task in a GitHub Actions workflow.
     - A standalone, reusable automation module (like a pre-packaged function) published to the GitHub Marketplace or referenced directly from public repositories.

2. **Isolation & Inter-Job Communication**:
   * Because each job executes on a completely distinct, ephemeral runner VM, memory and disk state are strictly isolated between jobs.
   * If `Job A` compiles a binary or generates a coverage report that `Job B` needs, `Job A` must explicitly upload the files using `actions/upload-artifact@v4` to GitHub's managed object storage. `Job B` (configured with `needs: Job A`) then downloads the files using `actions/download-artifact@v4`.

3. **Concurrency & Cost Optimization**:
   * When developers rapidly push multiple commits to an open PR, executing complete pipelines for every intermediate commit wastes compute minutes and clogs queues.
   * The `concurrency` block with `cancel-in-progress: true` automatically cancels older, superseded workflow runs on that branch, dedicating runner capacity strictly to the latest commit.

4. **Modern Secretless Cloud Authentication via OpenID Connect (OIDC)**:
   * *Book Definition (OIDC)*: OpenID Connect (OIDC) is an identity federation protocol that allows GitHub Actions workflows to access cloud resources securely using short-lived cryptographically signed tokens instead of static credentials.
   * Rather than storing permanent cloud access keys (e.g., `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`) in GitHub repository secrets, production pipelines use **OIDC Federation**.
   * During job execution, the runner requests a short-lived, cryptographically signed JSON Web Token (JWT) from GitHub's OIDC Token Provider.
   * The runner presents this token to the cloud provider (AWS STS, Google Cloud Workload Identity, or Azure AD). The cloud provider verifies the token signature and claims (repository path, branch, environment) and returns temporary, scoped credentials that automatically expire in 15 minutes.

---

### Why do we need it & What help does it provide? (Concrete Engineering Benefits)
* **Zero Infrastructure Overhead**: Managed cloud runners eliminate the heavy sysadmin burden of maintaining 24/7 CI servers, OS patches, and disk cleanup scripts.
* **Unified Developer Experience**: Developers no longer switch contexts between code repositories, external CI dashboards, and third-party portals. Pull request statuses, test failures, and build logs are rendered directly on the GitHub PR page.
* **Pristine, Ephemeral Build Environments**: Because every runner VM is freshly provisioned and immediately discarded after completion, builds are 100% reproducible. This completely eliminates "flaky builds" caused by residual background processes, uncleared temp files, or dirty caches from previous runs.
* **Massive Reusable Ecosystem**: The GitHub Marketplace offers tens of thousands of turnkey, community-tested Actions maintained by leading cloud providers (AWS, Google, Microsoft, Docker, HashiCorp), allowing complex pipelines to be assembled in minutes without writing custom integration code.
* **Hardened Security Posture**: OIDC integration eliminates static credentials, dramatically reducing the risk of catastrophic cloud account takeover due to exposed repository secrets.

---

## ⚡ 2. Before vs. After: Jenkins vs. GitHub Actions

| Feature / Aspect | Before (Legacy Self-Hosted Jenkins) | After (Modern GitHub Actions) | Why This Helps (The Impact) |
| :--- | :--- | :--- | :--- |
| **Infrastructure Setup** | Maintaining a dedicated 24/7 Jenkins EC2 server; manual OS updates and Java patches. | **Zero infrastructure maintenance**; fully managed ephemeral cloud runners. | Eliminates sysadmin overhead; saves thousands of dollars in cloud VM costs. |
| **Plugin Hell** | Dozens of fragile Jenkins plugins that crash on updates and create security CVEs. | **First-class OCI and marketplace actions** pinned to explicit commit SHAs. | Reliable, reproducible pipelines with zero breaking dependency surprises. |
| **Configuration Storage** | Configured via point-and-click web UI; hard to version-control or roll back. | **Configuration as Code**: Stored as `.github/workflows/*.yml` directly in Git. | Pipeline changes are peer-reviewed in pull requests just like application code. |
| **Clean Environments** | Shared worker nodes polluted with leftover temp files and orphan processes. | **Pristine Ephemeral VM** spun up per job and destroyed immediately after completion. | Zero flaky test state leakage across builds. |

---

## 🏗️ 3. Essential Workflow Architecture & Syntax

Here is a clean, production-grade Spring Boot CI workflow:

```yaml
name: Continuous Integration

# 1. Triggers: When should this run?
on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

# 2. Concurrency: Cancel older redundant runs on rapid commits!
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # Job 1: Run unit and integration tests
  test:
    runs-on: ubuntu-latest
    steps:
      - name: 1. Checkout repository
        uses: actions/checkout@v4

      - name: 2. Set up Java 21 with Maven caching
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven' # Automatically caches ~/.m2 dependencies!

      - name: 3. Run automated tests
        run: mvn clean test

  # Job 2: Build and push Docker image (only runs if test passes!)
  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' # Only build on main branch!
    steps:
      - name: 1. Checkout repository
        uses: actions/checkout@v4

      - name: 2. Log in to GitHub Container Registry (GHCR)
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 3. Build and Push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ghcr.io/${{ github.repository }}:latest
```

---

## 🔒 4. Security: Why OIDC Eliminates Stored Cloud Passwords

### The Risk of Long-Lived Cloud Keys
Traditionally, developers saved static AWS Access Keys (`AKIAIOSFODNN7EXAMPLE`) into GitHub Secrets. 
- If an attacker compromises your repository secrets, they obtain permanent root-level access to your cloud account!

### The Modern Standard: OpenID Connect (OIDC) Federation
Instead of saving passwords in GitHub, GitHub and AWS/GCP use **cryptographic trust**:

```mermaid
sequenceDiagram
    autonumber
    participant Runner as GitHub Actions Runner
    participant GHA_OIDC as GitHub OIDC Token Service
    participant Cloud as AWS STS / GCP IAM
    participant K8s as Target EKS/GKE Cluster

    Runner->>GHA_OIDC: Request short-lived OIDC JWT
    GHA_OIDC-->>Runner: Returns signed token (specifies repo & branch)
    Runner->>Cloud: Exchange JWT for temporary cloud credentials
    Cloud->>Cloud: Validates token signature & repository claims
    Cloud-->>Runner: Issues temporary (15-minute) AWS role credentials
    Runner->>K8s: Deploys application securely!
```
Zero static passwords stored anywhere! The credentials expire automatically in 15 minutes.

---

## 💼 5. Interview Preparation: High-Yield Questions & Answers

### Q1: "Explain the core components of GitHub Actions."
- **The Answer**:
  - *"A **Workflow** is an automated procedure defined in `.github/workflows/*.yml`.  
  - It is triggered by an **Event** (such as a push, pull request, or schedule).  
  - The workflow contains one or more **Jobs** that execute on independent virtual machines called **Runners**.  
  - Each job consists of sequential **Steps**, which can either run shell commands or execute reusable plugins called **Actions**."*

---

### Q2: "How do you pass data or files between two different jobs in GitHub Actions?"
- **The Answer**:
  - *"Because Jobs execute on completely separate virtual machines, they do not share disk storage or memory.  
  - To pass data from Job A to Job B, Job A must upload the artifact using the `actions/upload-artifact@v4` action to GitHub's managed blob storage.  
  - Job B (which declares `needs: job-a`) then downloads the file into its workspace using `actions/download-artifact@v4`."*

---

### Q3: "What is `concurrency: { cancel-in-progress: true }` and why is it useful?"
- **The Answer**:
  - *"It groups running workflows by branch name. If a developer pushes 3 commits in rapid succession within 2 minutes, GitHub Actions will automatically cancel the first two in-flight CI runs and only execute the pipeline for the latest commit.  
  - This prevents race conditions and saves valuable compute minutes."*

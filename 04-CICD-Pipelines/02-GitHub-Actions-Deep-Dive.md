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
    Event["🔔 Event Trigger: git push on 'main'"] --> WF["Workflow: .github/workflows/deploy.yml"]

    subgraph Runner_VM1["Runner 1 (Ubuntu VM)"]
        J1["Job: test\n• Step 1: Checkout Code\n• Step 2: Setup Java 21\n• Step 3: Run mvn test"]
    end

    subgraph Runner_VM2["Runner 2 (Ubuntu VM)"]
        J2["Job: docker-build-push\n(needs: test)\n• Step 1: Checkout Code\n• Step 2: Build Docker Image\n• Step 3: Push to GHCR"]
    end

    WF --> J1
    J1 -->|Success! Passes Quality Gate| J2

    style Event fill:#fef3c7,stroke:#d97706,stroke-width:2px
    style WF fill:#eff6ff,stroke:#2563eb,stroke-width:2px
    style Runner_VM1 fill:#f8fafc,stroke:#64748b,stroke-width:1px
    style Runner_VM2 fill:#f8fafc,stroke:#64748b,stroke-width:1px
    style J1 fill:#dcfce7,stroke:#16a34a,stroke-width:2px
    style J2 fill:#dbeafe,stroke:#1d4ed8,stroke-width:2px
```

---

## ⚡ 1. Before vs. After: Jenkins vs. GitHub Actions

| Feature / Aspect | Before (Legacy Self-Hosted Jenkins) | After (Modern GitHub Actions) | Why This Helps (The Impact) |
| :--- | :--- | :--- | :--- |
| **Infrastructure Setup** | Maintaining a dedicated 24/7 Jenkins EC2 server; manual OS updates and Java patches. | **Zero infrastructure maintenance**; fully managed ephemeral cloud runners. | Eliminates sysadmin overhead; saves thousands of dollars in cloud VM costs. |
| **Plugin Hell** | Dozens of fragile Jenkins plugins that crash on updates and create security CVEs. | **First-class OCI and marketplace actions** pinned to explicit commit SHAs. | Reliable, reproducible pipelines with zero breaking dependency surprises. |
| **Configuration Storage** | Configured via point-and-click web UI; hard to version-control or roll back. | **Configuration as Code**: Stored as `.github/workflows/*.yml` directly in Git. | Pipeline changes are peer-reviewed in pull requests just like application code. |
| **Clean Environments** | Shared worker nodes polluted with leftover temp files and orphan processes. | **Pristine Ephemeral VM** spun up per job and destroyed immediately after completion. | Zero flaky test state leakage across builds. |

---

## 🏗️ 2. Essential Workflow Architecture & Syntax

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

## 🔒 3. Security: Why OIDC Eliminates Stored Cloud Passwords

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

## 💼 4. Interview Preparation: High-Yield Questions & Answers

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

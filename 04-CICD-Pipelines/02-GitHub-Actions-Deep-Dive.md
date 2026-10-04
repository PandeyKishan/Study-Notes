# GitHub Actions: Architecture, Syntax & Advanced Pipelines

> **Cluster 04 — Module 02**  
> Focus: Workflow DAGs, matrix strategies, dependency caching, OIDC cloud federation, and concurrency controls.

---

## 1. GitHub Actions Execution Hierarchy

```mermaid
flowchart TD
    Workflow["Workflow (.github/workflows/ci.yml)\nTriggered by: on: [push, pull_request]"]
    
    subgraph Jobs["Parallel Jobs (unless 'needs' specified)"]
        J1["Job 1: lint-and-test\nRuns on: ubuntu-latest"]
        J2["Job 2: security-scan\nRuns on: ubuntu-latest"]
        J3["Job 3: docker-build-push\nneeds: [lint-and-test, security-scan]"]
    end

    subgraph Steps["Steps inside Job 3"]
        S1["Step 1: actions/checkout@v4"]
        S2["Step 2: docker/setup-buildx-action@v3"]
        S3["Step 3: docker/build-push-action@v5"]
    end

    Workflow --> J1
    Workflow --> J2
    J1 --> J3
    J2 --> J3
    J3 --> S1 --> S2 --> S3

    style Workflow fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
    style J3 fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style S3 fill:#ecfdf5,stroke:#10b981,stroke-width:1px
```

---

## 2. Advanced Optimization: Dependency Caching & Concurrency

### A. Automatic Concurrency Cancellation
Prevents wasted compute minutes when a developer pushes multiple commits in rapid succession:
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true   # Aborts older in-flight runs of the same branch!
```

### B. Dependency Caching
```yaml
- name: Setup Node.js Environment
  uses: actions/setup-node@v4
  with:
    node-version: 20
    cache: 'npm'   # Automatically hashes package-lock.json and caches ~/.npm
```

---

## 3. Matrix Strategy (Multi-Version & Multi-OS Testing)

Test multiple combinations in parallel without repeating YAML code:

```yaml
jobs:
  test:
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false  # Don't cancel remaining jobs if one fails
      matrix:
        os: [ubuntu-latest, macos-latest]
        node-version: [18.x, 20.x]
    steps:
      - uses: actions/checkout@v4
      - name: Use Node.js ${{ matrix.node-version }}
        uses: actions/setup-node@v4
        with:
          node-version: ${{ matrix.node-version }}
      - run: npm ci
      - run: npm test
```

---

## 4. OIDC (OpenID Connect): Passwordless Cloud Deployment

Storing static, long-lived AWS Access Keys in repository secrets is a severe security risk. If leaked, attackers gain full access to your cloud account.

**OIDC (OpenID Connect)** allows GitHub Actions runners to exchange a short-lived JSON Web Token (JWT) directly with AWS/GCP/Azure IAM to assume a temporary role.

```mermaid
sequenceDiagram
    autonumber
    participant GHA as GitHub Actions Runner
    participant GH_OIDC as GitHub OIDC Provider
    participant AWS as AWS Security Token Service (STS)
    participant K8s as Target EKS Cluster

    GHA->>GH_OIDC: Request OIDC ID Token (JWT with repo/branch claims)
    GH_OIDC-->>GHA: Signed JWT Token
    GHA->>AWS: AssumeRoleWithWebIdentity(RoleARN, JWT)
    AWS->>AWS: Verify JWT signature against GitHub's public keys
    AWS-->>GHA: Return temporary 15-minute AWS credentials!
    GHA->>K8s: Authenticate via AWS IAM & deploy manifests!
```

---

## 5. Official References
- [GitHub Actions Workflow Syntax](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)
- [Configuring OpenID Connect (OIDC) in AWS](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
- [Caching Dependencies to Speed Up Workflows](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)

# CI/CD: Top 20 Interview Questions & Pipeline Troubleshooting

> **Cluster 04 — Module 05**  
> Focus: Senior DevOps interview questions, DevSecOps interview topics, flaky test remediation, and pipeline failure runbooks.

---

## 1. Top 20 CI/CD & DevOps Interview Questions

### Q1: What is the exact difference between Continuous Delivery and Continuous Deployment?
**Answer:** In **Continuous Delivery**, every code change passing automated tests is automatically built, packaged, and verified in staging, ready for production deployment at any moment—but the final release to customers requires manual human approval (e.g. clicking "Approve"). In **Continuous Deployment**, there is zero human intervention: any commit that passes automated pipeline gates is immediately released to production users.

### Q2: What is GitOps and why is it superior to push-based CI/CD for Kubernetes?
**Answer:** GitOps uses Git repositories as the single source of truth for declared infrastructure and application state. An in-cluster operator (like ArgoCD) continuously synchronizes cluster state with Git.  
**Advantages:**
1. Zero cluster admin credentials exposed to external CI servers.
2. Automatic detection and remediation of manual configuration drift.
3. Instantaneous rollbacks via `git revert`.
4. Native auditability through Git commit logs.

### Q3: How do you achieve passwordless authentication in CI/CD pipelines?
**Answer:** Via **OpenID Connect (OIDC)** federated identity. The CI runner requests an ephemeral, cryptographically signed JSON Web Token (JWT) from GitHub's OIDC provider. The runner presents this token to the cloud provider (AWS STS / GCP IAM / Azure AD). The cloud provider validates the signature, verifies repository/branch claims, and issues temporary (15-minute) credentials. No static API keys are ever stored in secrets.

### Q4: How do you perform database schema updates during a zero-downtime rolling update?
**Answer:** Using the **Expand-and-Contract (Parallel Run) Pattern**. Never run destructive schema updates (e.g., dropping or renaming columns) simultaneously with application code deployments. First, expand the database by adding new columns as nullable and write to both. Backfill historical data asynchronously. Next, deploy the new application version reading from the new column. Finally, contract the database by dropping the unused old column in a subsequent release.

### Q5: What is the difference between Blue-Green and Canary deployments?
**Answer:**
- **Blue-Green**: Two identical production environments (Blue = active, Green = idle). The new release is deployed and tested on Green, then traffic is switched 100% all at once. Provides instant rollback, but requires $2\times$ infrastructure resources.
- **Canary**: A single environment where a tiny percentage of live traffic (e.g., 5%) is routed to the new version. Metrics (error rates, latency) are monitored automatically. If healthy, traffic is incrementally increased (10%, 25%, 100%). Confines blast radius to a small subset of users.

### Q6: How do you eliminate "Flaky Tests" in CI?
**Answer:**
1. Isolate test dependencies: Replace shared databases with ephemeral test containers (`testcontainers`).
2. Remove arbitrary sleep timers (`sleep(5)`): Use condition polling with timeouts (`waitForCondition`).
3. Quarantine flaky tests: Track failure rates, tag flaky tests to run in an advisory suite while investigating, and prevent them from blocking the main production gate.
4. Run tests in random order to expose state leakage between tests.

### Q7: What are the trade-offs between GitHub-hosted and Self-hosted runners?
**Answer:**
- **GitHub-hosted**: Clean, ephemeral VM per job (high security, zero maintenance), but limited compute specs and higher cost for massive enterprise workloads.
- **Self-hosted**: Run on your own VPC/Kubernetes (Actions Runner Controller - ARC), custom hardware (GPUs, huge RAM), access to private VPC networks—but requires maintenance, patching, and security isolation between untrusted public PRs.

### Q8: What is the difference between SAST, DAST, and SCA?
**Answer:**
- **SAST (Static Application Security Testing)**: Scans source code without executing it to detect bugs, injection vulnerabilities, and bad coding patterns (e.g. CodeQL, SonarQube).
- **SCA (Software Composition Analysis)**: Analyzes open-source dependencies and third-party packages for known vulnerabilities/CVEs and license compliance (e.g. Snyk, Dependabot).
- **DAST (Dynamic Application Security Testing)**: Scans running web applications from the outside by sending simulated attacks (e.g. OWASP ZAP) to find runtime vulnerabilities.

### Q9: What is Trivy and what is the difference between OS packages and language-specific dependencies?
**Answer:** Trivy is an open-source vulnerability scanner. When scanning an image, it inspects two distinct layers:
1. **OS Packages**: Installed via system package managers (`apt`, `apk`, `yum`), such as `openssl` or `glibc`.
2. **Language Dependencies**: Installed via language package managers, such as `package-lock.json` (npm), `pom.xml` (Maven), or `requirements.txt` (pip).

### Q10: How does Docker BuildKit caching work in GitHub Actions?
**Answer:** BuildKit allows exporting cache layers to external storage (like GitHub Actions cache backend or a container registry) via `--cache-to=type=gha` and `--cache-from=type=gha`. Unchanged layers are downloaded directly from the cache rather than recompiled from scratch.

### Q11: What is an SBOM (Software Bill of Materials)?
**Answer:** A formal machine-readable specification (in SPDX or CycloneDX format) listing all software components, third-party libraries, binaries, and licensing details included in an application artifact. Mandated for federal and enterprise cybersecurity compliance to audit exposure to zero-day vulnerabilities (e.g. Log4Shell).

### Q12: How does container signing with Sigstore Cosign work?
**Answer:** Cosign signs container images using public-key cryptography. In modern "keyless" mode, Cosign utilizes OIDC to verify the identity of the developer or CI runner, requests a short-lived signing certificate from the Fulcio Certificate Authority, and records the signature in the Rekor public transparency log.

### Q13: What is the purpose of `concurrency` in GitHub Actions?
**Answer:** It groups workflow executions by a key (e.g. `workflow_name + branch_ref`). If a developer pushes commit B while commit A is still building in the pipeline, `cancel-in-progress: true` automatically aborts commit A's run, freeing runner resources and preventing out-of-order deployments.

### Q14: What is Trunk-Based Development and why is it preferred over GitFlow?
**Answer:** In Trunk-Based Development, developers merge small, frequent commits into the single `main` branch multiple times a day using short-lived feature branches (< 1 day) and feature flags. This eliminates large, high-conflict merges ("merge hell"), drastically reduces lead time, and accelerates feedback loops.

### Q15: What is the difference between GitHub Actions Caching and Artifacts?
**Answer:**
- **Cache (`actions/cache`)**: Reusable data across different workflow runs to speed up builds (e.g., `~/.npm`, `~/.m2`, compiler caches). Caches may be evicted when storage limits are reached.
- **Artifacts (`actions/upload-artifact`)**: Immutable files produced during a specific workflow run (e.g., compiled binaries, test coverage reports, release tars) intended for auditing or download.

### Q16: How do you debug a failed CI step when logs don't provide enough information?
**Answer:**
1. Re-run the job with **Enable debug logging** checked (sets `ACTIONS_RUNNER_DEBUG=true`).
2. Run an interactive shell in the exact container environment locally using `act` (a tool to run GitHub Actions locally with Docker).
3. Insert debugging inspection steps (e.g. `env`, `df -h`, `pwd`, `ls -la`).

### Q17: What are GitHub Environments and Environment Protection Rules?
**Answer:** Environments represent deployment targets (e.g., `staging`, `production`). Protection rules allow requiring mandatory manual approvers, limiting deployments to specific protected branches (e.g., only `main`), and defining environment-specific secrets.

### Q18: What is Semantic Versioning (SemVer) and how is it automated in CI?
**Answer:** SemVer follows the format `MAJOR.MINOR.PATCH`:
- `MAJOR`: Breaking API changes.
- `MINOR`: Backward-compatible new features.
- `PATCH`: Backward-compatible bug fixes.  
Automated using tools like **Semantic Release**, which parses commit messages adhering to the Conventional Commits specification (`feat:`, `fix:`, `feat!:`) to determine the next version bump and generate changelogs automatically.

### Q19: How do you design an automated rollback mechanism?
**Answer:**
1. In Kubernetes: Run `kubectl rollout undo deployment/<name>` triggered automatically if post-deployment smoke tests fail.
2. In Progressive Delivery (Argo Rollouts / Flagger): Operators monitor Prometheus metrics (HTTP 5xx rate, p99 latency). If metrics violate SLO thresholds during the canary phase, the operator automatically aborts and routes 100% traffic back to the stable replica.

### Q20: How do you prevent secret leakage in CI/CD?
**Answer:**
1. Enforce local pre-commit hooks using `gitleaks` or `trufflehog`.
2. Mask secrets in pipeline logs automatically via the CI platform.
3. Run automated secret scanners on pull requests to block merges if hardcoded keys or tokens are detected.
4. Rotate any compromised secrets immediately and invalidate git commit history if necessary.

---

## 2. Common CI/CD Failures & Triage Runbook

| Failure | Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **No Space Left on Device** | `docker: failed to register layer: no space left` | Runner disk filled with old Docker layers and dangling volumes | Add a cleanup step at the start of the job: `docker system prune -af --volumes` |
| **Permission Denied on Docker Socket** | `Got permission denied while trying to connect to the Docker daemon socket` | Runner user is not a member of the `docker` group | Add runner user to docker group: `sudo usermod -aG docker $USER` or run with rootless Docker |
| **Cache Corruption** | `npm ERR! checksum failure` | Stored cache key corrupted or partial upload | Change the cache key version suffix (e.g. `v1-deps` $\to$ `v2-deps`) to force a clean cache bust |

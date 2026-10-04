# CI/CD: Top 20 Interview Questions & Pipeline Troubleshooting (Pro-Level)

> **Cluster 04 — Module 05**  
> Focus: Senior DevOps and DevSecOps interview topics, flaky test remediation, system architecture, and real-world pipeline failure runbooks.

---

## 1. Top CI/CD & DevOps Interview Questions

### Q1: Continuous Delivery vs. Continuous Deployment?
**Answer:** In **Continuous Delivery**, every change passing automated tests is built, packaged, and deployed to staging, perfectly ready for production—but the final release to customers requires manual human approval (e.g., clicking "Approve" in GitHub Environments). In **Continuous Deployment**, there is zero human intervention: any commit passing automated pipeline gates is immediately released to production users. It demands pristine test coverage and automated rollback observability.

### Q2: Why is GitOps superior to push-based CI/CD for Kubernetes?
**Answer:** GitOps uses Git as the single source of truth for declared infrastructure and application state. An in-cluster operator (like ArgoCD or Flux) continuously pulls and synchronizes cluster state with Git.  
**Advantages:**
1. **Security:** Zero cluster admin credentials exposed to external CI servers.
2. **Self-Healing:** Automatic detection and remediation of manual configuration drift (e.g., someone manually deleting a Pod).
3. **Rollbacks:** Instantaneous rollbacks via a simple `git revert`.
4. **Compliance:** Native auditability through Git commit logs.

### Q3: How do you implement passwordless authentication in CI/CD?
**Answer:** Via **OpenID Connect (OIDC)** federated identity. The CI runner requests an ephemeral, cryptographically signed JSON Web Token (JWT) from GitHub's OIDC provider. The runner presents this token to the cloud provider (AWS STS / GCP IAM). The cloud provider validates the signature, verifies repository/branch claims, and issues temporary (15-minute) credentials. No static API keys are ever stored in secrets, eliminating credential leakage risk.

### Q4: How do you handle database schema updates during a zero-downtime deployment?
**Answer:** By using the **Expand-and-Contract (Parallel Run) Pattern**. Destructive schema updates (e.g., dropping or renaming columns) must *never* run concurrently with application code deployments. 
1. **Expand:** Add new columns as nullable. Deploy code writing to both old and new.
2. **Migrate:** Backfill historical data asynchronously.
3. **Transition:** Deploy new code reading exclusively from the new column.
4. **Contract:** Drop the old column in a later release once old pods are fully terminated.

### Q5: Blue-Green vs. Canary deployments?
**Answer:**
- **Blue-Green**: Two identical production environments (Blue = active, Green = idle). The new release is deployed and tested on Green, then traffic is switched 100% instantly via an Ingress/Service selector. Provides rapid rollback but costs $2\times$ the infrastructure.
- **Canary**: A single environment where a tiny percentage of live traffic (e.g., 5%) is routed to the new version. Metrics (error rates, latency) are monitored automatically via tools like Argo Rollouts + Prometheus. If healthy, traffic increments (10% $\to$ 25% $\to$ 100%). Confines blast radius significantly.

### Q6: How do you systematically eliminate "Flaky Tests" in CI?
**Answer:**
1. **Isolate State**: Replace shared staging databases with ephemeral test containers (`testcontainers`) spun up per test suite.
2. **Eliminate Race Conditions**: Remove arbitrary sleep timers (`sleep(5)`) and replace with condition polling (`waitForCondition`).
3. **Quarantine**: Track failure rates, automatically tag flaky tests, and move them to an advisory suite that doesn't block deployments while they are investigated.
4. **Chaos Testing**: Run tests in random order to expose hidden state leakage between tests.

### Q7: GitHub-hosted vs. Self-hosted runners (ARC)?
**Answer:**
- **GitHub-hosted**: Clean, ephemeral VM per job (high security, zero maintenance). Cons: Limited compute, shared IP spaces (bad for strict firewalls), and higher cost at scale.
- **Self-hosted (ARC - Actions Runner Controller)**: Run inside your own Kubernetes clusters. Pros: Access to private VPC resources, custom hardware (GPUs), and unlimited scale. Cons: Requires maintenance, patching, and rigorous security isolation (using ephemeral pods) to prevent untrusted PRs from escaping the runner.

### Q8: Differentiate SAST, DAST, and SCA.
**Answer:**
- **SAST (Static Application Security Testing)**: White-box testing. Scans source code without executing it for injection vulnerabilities and bad practices (e.g., SonarQube, CodeQL).
- **SCA (Software Composition Analysis)**: Analyzes the supply chain (open-source dependencies) for known vulnerabilities/CVEs and license compliance (e.g., Snyk, Dependabot).
- **DAST (Dynamic Application Security Testing)**: Black-box testing. Scans running applications from the outside by sending simulated malicious payloads (e.g., OWASP ZAP).

### Q9: What is an SBOM and why is it legally critical?
**Answer:** A Software Bill of Materials (SBOM) is a formal, machine-readable specification (in SPDX or CycloneDX format) listing all software components, third-party libraries, binaries, and licensing details included in an artifact. Following executive orders on cybersecurity, it is mandated for enterprise compliance to rapidly audit exposure to zero-day vulnerabilities (e.g., Log4Shell).

### Q10: How does container signing (Cosign) protect the supply chain?
**Answer:** Cosign signs container images using public-key cryptography. In "keyless" mode, it utilizes OIDC to verify the identity of the CI runner, requests a short-lived signing certificate from the Fulcio CA, and records the signature in the Rekor transparency log. Kubernetes Admission Controllers (like Kyverno) verify this signature before allowing a pod to start, preventing compromised images from running.

---

## 2. CI/CD Failures & Triage Runbook (Pro-Tier)

| Error Message / Symptom | Root Cause Diagnosis | Remediation Strategy |
| :--- | :--- | :--- |
| `docker: failed to register layer: no space left on device` | The CI runner disk is saturated with old Docker layers, cache, and dangling volumes. | Prepend a cleanup step to the workflow: `docker system prune -af --volumes` or increase runner EBS volume size. |
| `Got permission denied while trying to connect to the Docker daemon socket` | The CI runner user is not a member of the `docker` group, lacking socket permissions. | Run `sudo usermod -aG docker $USER` during runner bootstrap, or switch to rootless Docker / Podman. |
| `npm ERR! checksum failure` / Build acts unexpectedly | Cache Poisoning or Cache Corruption in `actions/cache`. The restored cache contains corrupted binaries. | Bust the cache manually by modifying the cache key version (e.g., `v1-node-deps` $\to$ `v2-node-deps`) in the YAML. |
| Workflow canceled automatically after 5 minutes | `concurrency` setting with `cancel-in-progress: true` triggered because a newer commit was pushed to the same branch. | Expected behavior. If undesired for specific critical deployments, remove the `concurrency` block for that job. |
| `OIDC provider rejected the request` | The IAM Trust Policy does not correctly match the repository name or branch claims in the JWT. | Verify the `Condition` block in the AWS/GCP role strictly matches `repo:my-org/my-repo:ref:refs/heads/main`. |
| Flaky UI Tests (Cypress/Playwright) timing out | Runner is CPU-starved, causing browser rendering to slow down and hit timeouts before elements appear. | Upgrade to a larger GitHub runner (e.g., `ubuntu-latest-8-cores`) or optimize test parallelization. |

---

## 3. Official References
- [GitHub Actions Runner Controller (ARC)](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners-with-actions-runner-controller)
- [OIDC Federation in CI/CD](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
- [Google DORA Metrics](https://dora.dev/)

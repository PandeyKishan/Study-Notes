# CI/CD: The Complete Interview Question Bank & Pipeline Troubleshooting Playbook

> **Cluster 04 — Module 05**  
> **Level**: Beginner to Senior / FAANG DevOps Engineering  
> **Key References**: TechWorld with Nana, Google DORA, GitHub Actions Engineering

---

## 🎯 How to Ace CI/CD & DevOps Interviews
In modern software interviews, hiring managers test your understanding across three key pillars:
1. **Automation Intuition**: Can you articulate *why* manual deployment is hazardous and how automated pipelines eliminate human error?
2. **Security & Governance (DevSecOps)**: Do you know how to secure the software supply chain without storing static credentials?
3. **Resilience & Zero Downtime**: Can you safely update databases and microservices without dropping active user connections?

---

## 🟢 Part 1: Beginner Interview Questions

### Q1: What is the exact difference between Continuous Integration, Continuous Delivery, and Continuous Deployment?
**The 60-Second Winning Answer**:
- **Continuous Integration (CI)**: Automatically builds, compiles, and tests code every time a developer pushes a commit or creates a pull request. Its goal is to catch bugs and merge conflicts within minutes.
- **Continuous Delivery (CD)**: Automatically packages the tested application into an immutable release artifact (like a Docker image) and deploys it to staging. The application is always 100% release-ready, but deploying to production requires a **manual business approval click**.
- **Continuous Deployment**: The entire process is 100% automated with **zero human intervention**. Any code that passes automated testing and security gates is immediately released to live production users.

---

### Q2: What is the purpose of an Artifact in a CI/CD pipeline?
- **The Answer**:
  - *"An artifact is an immutable compiled package or container image produced during the build phase (such as a `.jar` file, `.tar` bundle, or OCI Docker image).  
  - Artifacts follow the **'Build Once, Deploy Many'** principle: you build and test the exact binary once in CI, and deploy that identical, unmodified artifact across Dev, Staging, and Production environments to guarantee absolute consistency."*

---

### Q3: What is the difference between GitHub-hosted runners and Self-hosted runners?
- **The Answer**:
  - **GitHub-hosted runners**: Clean, ephemeral virtual machines (Ubuntu, Windows, macOS) provisioned and managed by GitHub. They are destroyed after every job, providing maximum security with zero maintenance overhead.
  - **Self-hosted runners**: Virtual machines or Kubernetes pods running inside your own private cloud VPC (e.g., AWS EC2 or Actions Runner Controller). They provide access to internal databases, custom hardware (GPUs), and unlimited execution minutes, but require maintenance and OS patching.

---

### Q4: What is Trunk-Based Development and how does it prevent "Merge Hell"?
- **The Answer**:
  - *"In Trunk-Based Development, developers merge small, frequent commits into the main branch ('trunk') daily, keeping branches alive for less than 24 hours.  
  - Unlike legacy GitFlow where feature branches live for weeks and cause massive merge conflicts, Trunk-Based Development uses **Feature Flags** to deploy partially completed code into production safely, decoupling deployment from user exposure."*

---

## 🟡 Part 2: Intermediate Interview Questions

### Q5: How do you eliminate "Flaky Tests" in a CI pipeline?
**The Answer**:
1. **Isolate Database State**: Never share a single staging database among test runners. Use **Testcontainers** to spin up an ephemeral, clean PostgreSQL/Kafka Docker container per test suite.
2. **Eliminate Arbitrary Sleep Timers**: Replace `Thread.sleep(5000)` with intelligent condition polling (`await().atMost(10, SECONDS).until(...)`).
3. **Quarantine & Chaos Order**: Run test suites in random execution order to expose hidden state leaks between tests. Temporarily isolate flaky tests to an advisory suite that doesn't block critical production pipelines.

---

### Q6: Differentiate between SAST, SCA, and DAST.
**The Answer**:
- **SAST (Static Testing)**: Scans source code without running it (looks for hardcoded secrets, SQL injection, insecure cryptography).
- **SCA (Dependency Audit)**: Scans third-party open-source libraries for known CVE vulnerabilities (like Log4j) and license risks.
- **DAST (Dynamic Testing)**: Attacks the running application from the outside by sending simulated hacker payloads against HTTP endpoints.

---

### Q7: What are the 4 DORA Metrics and why do they matter?
**The Answer**:
- Developed by Google's DevOps Research and Assessment team, they measure software delivery efficiency:
  1. **Deployment Frequency**: How often code is shipped to production (Elite: Multiple times per day).
  2. **Lead Time for Changes**: Time from commit to running in production (Elite: Less than 1 hour).
  3. **Change Failure Rate**: Percentage of deployments requiring hotfixes or rollbacks (Elite: 0% – 5%).
  4. **Time to Restore Service (MTTR)**: Time to recover from an outage (Elite: Less than 1 hour).

---

## 🔴 Part 3: Senior & DevSecOps System Questions

### Q8: Why is Pull-Based GitOps (ArgoCD) superior to Push-Based CI/CD for Kubernetes?
**The System Answer**:
> *"In traditional push-based CI/CD, external CI servers like Jenkins or GitHub Actions require cluster-admin kubeconfig credentials to execute `kubectl apply` over the public internet, creating a critical security risk.  
> In pull-based GitOps (ArgoCD), the controller lives inside the private Kubernetes cluster with zero external firewall ingress. It continuously monitors the Git repository as the single source of truth, detects configuration drift, automatically heals unauthorized changes, and allows instantaneous rollbacks with `git revert`."*

---

### Q9: How does Passwordless Authentication (OIDC) work in GitHub Actions?
**The Security Answer**:
> *"Instead of saving static AWS Secret Keys in GitHub repository settings, GitHub Actions uses OpenID Connect (OIDC) federation.  
> When the workflow runs, the runner requests a cryptographically signed JSON Web Token (JWT) from GitHub's OIDC provider containing claims about the repository and branch. The runner presents this token to AWS STS / GCP IAM. The cloud provider validates the signature against GitHub's public keys and issues temporary (15-minute) credentials. No long-lived static secrets are ever stored in GitHub, eliminating credential leakage risk."*

---

### Q10: How do you achieve Zero-Downtime deployments when changing database schemas?
**The Architecture Answer**:
> *"By implementing the **Expand-and-Contract (Parallel Run) Pattern**:  
> Destructive schema modifications must never run concurrently with code deployment.  
> 1. **Expand**: Add new columns as nullable in the database first. Deploy application code that writes to both the old and new columns.  
> 2. **Backfill**: Asynchronously migrate historical database records to populate the new column.  
> 3. **Migrate Readers**: Deploy the next version of code that reads exclusively from the new column.  
> 4. **Contract**: In a subsequent release, once legacy application pods are completely terminated, run a final database migration to drop the old columns."*

---

## 🛠️ Part 4: Production Pipeline Troubleshooting Runbook

| Error Message / Symptom | Root Cause Diagnosis | Remediation Strategy |
| :--- | :--- | :--- |
| `docker: failed to register layer: no space left on device` | The CI runner disk is filled with old cached Docker layers and dangling volumes. | Prepend a cleanup step: `docker system prune -af --volumes` or configure larger root volumes on self-hosted runners. |
| `Got permission denied while trying to connect to the Docker daemon socket` | The CI runner process user lacks membership in the host `docker` group. | Run `sudo usermod -aG docker $USER` during runner setup, or use rootless Docker. |
| `npm ERR! checksum failure` / Build acts unpredictably | Cache poisoning or corrupted tarball in `actions/cache`. | Increment the cache key version in your YAML (e.g. `v1-deps` $\to$ `v2-deps`) to force a fresh download. |
| `OIDC provider rejected the request: Condition not matched` | The IAM Trust Policy does not match the exact repository name or branch claims in the JWT. | Check the AWS/GCP IAM Role trust policy; ensure `repo:my-org/my-repo:ref:refs/heads/main` matches exactly. |
| Workflow canceled automatically after 2 minutes | A newer commit was pushed to the same branch while `cancel-in-progress: true` is configured. | This is expected behavior designed to save compute resources. Remove the concurrency block if the job must always finish. |

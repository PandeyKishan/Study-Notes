# CI/CD: Fundamentals, GitOps & Pipeline Design

> **Cluster 04 — Module 01**  
> Focus: Continuous Integration vs Continuous Delivery vs Deployment, Trunk-Based Development, Push vs Pull GitOps (ArgoCD), and pipeline stages.

---

## 1. CI vs CD vs Continuous Deployment

```mermaid
flowchart LR
    Dev["Developer Code Commit"] --> CI["1. Continuous Integration (CI)\n• Build & Compile\n• Unit & Integration Tests\n• Code Quality & Linters\n• Security Scans (SAST)"]
    CI --> CD_Deliv["2. Continuous Delivery (CD)\n• Automated Artifact Packaging\n• Deploy to Staging\n• Staging E2E Testing\n• Manual Approval Gate"]
    CD_Deliv --> CD_Deploy["3. Continuous Deployment\n• Automated Production Release\n• Health Checks & Canary Verification\n• Auto-Rollback on Anomaly"]

    style CI fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
    style CD_Deliv fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style CD_Deploy fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

### Definitions:
1. **Continuous Integration (CI)**: Developers merge code changes frequently into a shared trunk. Every merge automatically triggers automated builds and test suites to detect regressions early.
2. **Continuous Delivery (CD)**: Extends CI by ensuring every build is packageable and deployable to production at the click of a button after passing staging environments.
3. **Continuous Deployment**: Fully automated path to production where code passing all automated gates is deployed immediately without human intervention.

---

## 2. Push-Based CI/CD vs Pull-Based GitOps

How does an artifact actually make its way into a Kubernetes cluster?

```mermaid
flowchart TD
    subgraph Push_Model["Traditional Push-Based CI/CD (GitHub Actions / Jenkins)"]
        GHA["CI Runner (GitHub Actions)"] -->|Requires Production Cluster Admin Kubeconfig| API1["Kube-API Server"]
        API1 --> Cluster1["Production Cluster"]
    end

    subgraph Pull_Model["Modern Pull-Based GitOps (ArgoCD / Flux)"]
        GitRepo["Git Config Repo (Desired State Manifests)"]
        Argo["ArgoCD Controller (Inside Cluster)"]
        API2["Internal Kube-API"]
        
        Argo -->|1. Polling / Webhook| GitRepo
        Argo -->|2. Reconcile Diff| API2
        API2 --> Cluster2["Production Cluster"]
    end

    style Push_Model fill:#fef2f2,stroke:#ef4444,stroke-width:1px
    style Pull_Model fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

### Why Pull-Based GitOps Wins in Production

| Aspect | Push-Based (e.g. Jenkins / GitHub Actions) | Pull-Based GitOps (ArgoCD / Flux) |
| :--- | :--- | :--- |
| **Cluster Security** | External CI tool requires full cluster-admin credentials stored as repository secrets (huge attack vector). | Zero cluster credentials exposed externally. Agent runs inside the cluster pulling from Git. |
| **Configuration Drift** | If someone runs `kubectl edit` or deletes a pod manually, the CI server never detects the drift. | ArgoCD constantly compares cluster state to Git and automatically reverts unauthorized manual edits. |
| **Rollbacks** | Requires re-running a pipeline build job. | Instant: Just `git revert` a Git commit. |
| **Audit Trail** | Fragmented across pipeline logs. | Git commit log serves as the single immutable audit log for all changes. |

---

## 3. Branching Strategies: Trunk-Based Development vs GitFlow

```mermaid
gitGraph
    commit id: "main-v1.0"
    branch feat/order-kafka
    checkout feat/order-kafka
    commit id: "add-producer"
    commit id: "add-tests"
    checkout main
    merge feat/order-kafka id: "PR Merge: Short-lived branch (<24 hrs)"
    commit id: "main-v1.1"
```

- **GitFlow (Legacy)**: Heavy branches (`develop`, `feature/*`, `release/*`, `hotfix/*`, `main`). Features take weeks to merge, resulting in painful merge conflicts and delayed feedback loops.
- **Trunk-Based Development (Modern DevOps Standard)**: Developers create short-lived feature branches (< 1 day of work), make small atomic commits, run automated tests, and merge back into `main` frequently behind **Feature Flags**.

---

## 4. Official References
- [Martin Fowler on Continuous Integration](https://martinfowler.com/articles/continuousIntegration.html)
- [OpenGitOps Principles](https://opengitops.dev/)
- [ArgoCD Architecture](https://argo-cd.readthedocs.io/en/stable/core_concepts/)
- [Trunk Based Development Guide](https://trunkbaseddevelopment.com/)

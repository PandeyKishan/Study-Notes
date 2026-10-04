# Deployment Strategies: Rolling, Blue-Green, Canary & A/B

> **Cluster 04 — Module 04**  
> Focus: Zero-downtime rollouts, Blue-Green cutovers, Canary metrics analysis (Argo Rollouts), and backward compatibility.

---

## 1. Deployment Strategies Comparison Matrix

| Strategy | Downtime | Rollback Speed | Resource Cost | Risk Level |
| :--- | :--- | :--- | :--- | :--- |
| **Recreate** | Yes (Minutes) | Slow (Re-pull image) | $1.0\times$ (Low) | High |
| **Rolling Update** | Zero | Moderate (Reverse roll) | $1.25\times$ (Surge buffer) | Medium (v1 & v2 run concurrently) |
| **Blue-Green** | Zero | Instant (< 1 second) | $2.0\times$ (Full duplicate stack) | Low |
| **Canary** | Zero | Instant (< 5 seconds) | $1.1\times$ to $1.2\times$ | Lowest (Blast radius confined to 5% users) |

---

## 2. Visual Architecture of Strategies

### A. Rolling Update (Default Kubernetes)
Pods are progressively terminated and replaced by new versions one-by-one:

```mermaid
flowchart LR
    subgraph Step1["Step 1 (Start)"]
        V1_1["v1.0 (Live)"]
        V1_2["v1.0 (Live)"]
        V1_3["v1.0 (Live)"]
    end
    subgraph Step2["Step 2 (Surge & Drain)"]
        V1_A["v1.0 (Terminating)"]
        V1_B["v1.0 (Live)"]
        V2_A["v2.0 (Healthy)"]
    end
    subgraph Step3["Step 3 (Complete)"]
        V2_1["v2.0 (Live)"]
        V2_2["v2.0 (Live)"]
        V2_3["v2.0 (Live)"]
    end

    Step1 --> Step2 --> Step3
```

---

### B. Blue-Green Deployment
Two identical environments exist simultaneously. Traffic is switched at the router or service selector level:

```mermaid
flowchart TD
    Router["Router / Ingress Controller\n(Traffic Router)"]
    
    subgraph Blue_Environment["Active Environment (Blue)"]
        B1["v1.0 Pod"]
        B2["v1.0 Pod"]
    end

    subgraph Green_Environment["Staging / New Environment (Green)"]
        G1["v2.0 Pod (Tested & Verified)"]
        G2["v2.0 Pod (Tested & Verified)"]
    end

    Router -->|100% Active Production Traffic| Blue_Environment
    Router -.->|Instant Switchover via Service selector change| Green_Environment

    style Blue_Environment fill:#dbeafe,stroke:#2563eb,stroke-width:2px
    style Green_Environment fill:#dcfce7,stroke:#16a34a,stroke-width:2px
```

---

### C. Canary Deployment (Traffic Shifting & Metric Gates)
Exposes the new version to a small subset of live users while monitoring error rates via Prometheus:

```mermaid
flowchart TD
    Ingress["Ingress / Service Mesh (Traefik / Istio / Envoy)"]
    
    Ingress -->|95% Production Traffic| Stable["Stable Fleet (v1.0)\n95% Load"]
    Ingress -->|5% Experimental Traffic| Canary["Canary Fleet (v2.0)\n5% Load"]

    Prometheus["Prometheus Metric Analyzer"]
    Canary -.->|Expose HTTP Error Rate & Latency| Prometheus
    
    Prometheus -->|If Error Rate > 1%| Abort["Auto-Rollback! (Route 100% back to Stable)"]
    Prometheus -->|If Errors == 0%| Promote["Promote! (Increase 5% -> 25% -> 100%)"]

    style Stable fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
    style Canary fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style Abort fill:#fee2e2,stroke:#ef4444,stroke-width:1px
    style Promote fill:#ecfdf5,stroke:#10b981,stroke-width:1px
```

---

## 3. Database Migration Rule for Zero-Downtime Deployments

In both Rolling Updates and Canary releases, **version 1.0 and version 2.0 will access the database at the exact same time**.

> [!CAUTION]
> **Never run destructive database migrations in lock-step with code deployment!** (e.g. Renaming column `email` to `user_email`). Version 1.0 code will crash instantly when reading the renamed column.

### The Expand-and-Contract (Parallel Run) Pattern:
1. **Phase 1 (Expand)**: Add the new column `user_email` as nullable. Deploy code that writes to *both* `email` and `user_email`.
2. **Phase 2 (Backfill)**: Run an asynchronous background script copying historical data from `email` to `user_email`.
3. **Phase 3 (Migrate Readers)**: Deploy version 2.0 code that reads exclusively from `user_email`.
4. **Phase 4 (Contract)**: Drop the legacy `email` column once version 1.0 is completely decommissioned.

---

## 4. Official References
- [Argo Rollouts Documentation](https://argoproj.github.io/argo-rollouts/)
- [Flagger Progressive Delivery Operator](https://flagger.app/)
- [Martin Fowler on Evolutionary Database Design](https://martinfowler.com/articles/evodb.html)

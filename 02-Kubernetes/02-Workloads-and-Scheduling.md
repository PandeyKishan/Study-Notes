# Kubernetes: Workloads, Lifecycle & Advanced Scheduling

> **Cluster 02 — Module 02**  
> Focus: Pod anatomy, the Pause container, StatefulSets vs Deployments, QoS classes, Node Affinity, and Taints/Tolerations.

---

## 1. Inside a Pod: The Pause Container & Shared Namespaces

A **Pod** is the atomic scheduling unit in Kubernetes. It is a collection of one or more tightly coupled containers that share the same storage volumes, network namespace, and IPC namespace.

```mermaid
flowchart TD
    subgraph Pod_Boundary["Pod Isolation Boundary (cgroup + namespaces)"]
        Pause["Pause Container (k8s.gcr.io/pause)\nHolds NET and IPC namespaces open\nOwns IP: 10.244.1.42"]
        App["Application Container\n(Node.js / Go / Java)"]
        Sidecar["Sidecar Container\n(Log Shipper / Envoy Proxy)"]
        SharedVol["Shared Volume (emptyDir / PV)\nMounted at /var/log/app"]

        Pause --- App
        Pause --- Sidecar
        App -->|Writes logs| SharedVol
        Sidecar -->|Reads & streams logs| SharedVol
        App <-->|Localhost loopback 127.0.0.1| Sidecar
    end

    style Pod_Boundary fill:#f8fafc,stroke:#0284c7,stroke-width:2px
    style Pause fill:#f1f5f9,stroke:#64748b,stroke-width:1px
    style App fill:#ecfdf5,stroke:#10b981,stroke-width:2px
    style Sidecar fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
```

- **The Pause Container**: When a pod starts, `kubelet` instructs the runtime to launch the `pause` container first. The pause container requests a network namespace and sits idle. The application and sidecar containers then join this existing network namespace (`--net=container:pause`), enabling them to communicate with each other over `localhost`.

---

## 2. Workload Controllers Compared

```mermaid
classDiagram
    class WorkloadControllers {
        <<abstraction>>
    }
    class Deployment {
        +Stateless applications
        +ReplicaSet management
        +Rolling updates & rollbacks
    }
    class StatefulSet {
        +Stateful databases / Kafka
        +Stable network ID (pod-0, pod-1)
        +Dedicated PVC per replica
        +Ordered scaling & termination
    }
    class DaemonSet {
        +1 replica per cluster node
        +Log collectors (Fluentbit)
        +Monitoring (Node-Exporter)
    }
    class Job_CronJob {
        +Run to completion
        +Batch jobs & migrations
        +Scheduled cron expressions
    }
    WorkloadControllers <|-- Deployment
    WorkloadControllers <|-- StatefulSet
    WorkloadControllers <|-- DaemonSet
    WorkloadControllers <|-- Job_CronJob
```

### Deployments vs StatefulSets (Production Comparison)

| Feature | Deployment | StatefulSet |
| :--- | :--- | :--- |
| **Pod Identity** | Random hashes (`order-app-6848c4bb58-x9p2z`) | Stable, ordinal index (`kafka-0`, `kafka-1`, `kafka-2`) |
| **Storage Binding** | Pods typically share read-only storage or use stateless storage | Each pod receives its own dedicated `PersistentVolume` via `volumeClaimTemplates` |
| **Scaling Order** | Parallel, random order | Strict sequential order ($0 \to 1 \to 2$ on scale up; $2 \to 1 \to 0$ on scale down) |
| **Network Identity** | Ephemeral pod IPs behind a ClusterIP | Headless Service assigns individual DNS A-records per pod |
| **Primary Use Cases** | Stateless APIs, web apps, microservices | Kafka brokers, ZooKeeper, MongoDB, Cassandra, PostgreSQL |

---

## 3. Resource Allocation & Quality of Service (QoS) Classes

When defining Pod specifications, you specify **requests** (for scheduling) and **limits** (for kernel enforcement). Kubernetes classifies Pods into three **QoS classes**, which determine the order in which pods are terminated during host memory pressure.

```mermaid
flowchart TD
    subgraph QoS_Hierarchy["Eviction Priority Under Node Memory Pressure (Last to First)"]
        direction TB
        G["1. Guaranteed QoS\n(Requests == Limits for all CPU & RAM)\nNEVER evicted unless no other option"]
        B["2. Burstable QoS\n(Requests < Limits or Requests defined without Limits)\nEvicted when exceeding requests"]
        BE["3. BestEffort QoS\n(No requests or limits set)\nFIRST to be OOMKilled/evicted!"]
        
        G --> B --> BE
    end

    style G fill:#dcfce7,stroke:#22c55e,stroke-width:2px
    style B fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style BE fill:#fee2e2,stroke:#ef4444,stroke-width:2px
```

```yaml
# Example: Guaranteed QoS Pod
resources:
  requests:
    cpu: "500m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

---

## 4. Advanced Scheduling: Affinity, Anti-Affinity & Taints

### Node Affinity (Directing Pods to Specific Hardware)
```yaml
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
        - matchExpressions:
            - key: topology.kubernetes.io/zone
              operator: In
              values: ["us-east-1a", "us-east-1b"]
```

### Pod Anti-Affinity (High Availability / Fault Tolerance)
Ensures two replicas of the same service **never** land on the same physical node or availability zone:
```yaml
affinity:
  podAntiAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchExpressions:
            - key: app
              operator: In
              values: ["order-service"]
        topologyKey: "kubernetes.io/hostname"
```

### Taints and Tolerations (Node Repulsion)
- **Taint** on a Node: *"I repel pods unless they tolerate me."*
  `kubectl taint nodes node-gpu dedicated=gpu:NoSchedule`
- **Toleration** on a Pod: *"I can tolerate this taint and run here."*
```yaml
tolerations:
  - key: "dedicated"
    operator: "Equal"
    value: "gpu"
    effect: "NoSchedule"
```

---

## 5. Official References
- [Kubernetes Pod Lifecycle](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
- [StatefulSets Deep Dive](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [Pod Quality of Service (QoS) Classes](https://kubernetes.io/docs/tasks/configure-pod-container/quality-service-pod/)

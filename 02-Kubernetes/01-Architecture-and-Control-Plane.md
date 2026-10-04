# Kubernetes: Architecture, Control Plane & Node Internals

> **Cluster 02 — Module 01**  
> Focus: Kubernetes components, the reconciliation loop, etcd consensus, Kubelet internals, CRI/CNI/CSI standards, and request lifecycle.

---

## 1. High-Level Cluster Architecture

Kubernetes is a declarative, distributed container orchestrator. It divides work between a **Control Plane** (the cluster brain) and **Worker Nodes** (where workload containers actually execute).

```mermaid
flowchart TD
    subgraph Control_Plane["Kubernetes Control Plane (Master Node)"]
        API["kube-apiserver\n(REST API Gateway, Auth, Admission Control)"]
        ETCD["etcd\n(Distributed Raft Key-Value Store)"]
        Sched["kube-scheduler\n(Filters & Scores Nodes for Pods)"]
        KCM["kube-controller-manager\n(Reconciliation Loops: Deployment, Node, etc.)"]
        CCM["cloud-controller-manager\n(Cloud Provider API integration)"]

        API <--> ETCD
        API <--> Sched
        API <--> KCM
        API <--> CCM
    end

    subgraph Worker_Node_1["Worker Node 1"]
        Kubelet1["kubelet\n(Node Agent)"]
        Proxy1["kube-proxy\n(iptables/IPVS Service Routing)"]
        Runtime1["Container Runtime\n(containerd / CRI-O)"]
        Pod1["Pod A (App)"]
        Pod2["Pod B (App)"]

        Kubelet1 <--> Runtime1
        Runtime1 --> Pod1
        Runtime1 --> Pod2
        Proxy1 -.-> Pod1
    end

    subgraph Worker_Node_2["Worker Node 2"]
        Kubelet2["kubelet\n(Node Agent)"]
        Proxy2["kube-proxy\n(iptables/IPVS Service Routing)"]
        Runtime2["Container Runtime\n(containerd / CRI-O)"]
        Pod3["Pod C (App)"]

        Kubelet2 <--> Runtime2
        Runtime2 --> Pod3
        Proxy2 -.-> Pod3
    end

    API <==>|mTLS Secure Channel| Kubelet1
    API <==>|mTLS Secure Channel| Kubelet2

    style Control_Plane fill:#f0f9ff,stroke:#0284c7,stroke-width:2px
    style Worker_Node_1 fill:#f8fafc,stroke:#64748b,stroke-width:1px
    style Worker_Node_2 fill:#f8fafc,stroke:#64748b,stroke-width:1px
```

---

## 2. Control Plane Components (The Brain)

### 1. `kube-apiserver`
- The front door to the cluster. All internal components (`scheduler`, `kubelet`, `controller-manager`) and external clients (`kubectl`, CI/CD) interact **only** through the API server via REST over HTTPS.
- **The only component that talks directly to `etcd`**.
- Implements: Authentication $\to$ Authorization (RBAC) $\to$ Admission Control (Mutating and Validating Webhooks) $\to$ Persistence in `etcd`.

### 2. `etcd` (Consistency & State)
- A highly consistent, distributed key-value store using the **Raft consensus algorithm**.
- Stores the entire state of the cluster (desired state, secrets, configs, active pod states).
- Provides a **Watch API**: components subscribe to resource changes without expensive polling.
- Typically run in 3 or 5 node quorums to tolerate $N/2 - 1$ failures ($3$ nodes tolerate $1$ failure, $5$ nodes tolerate $2$).

### 3. `kube-scheduler`
- Assigns newly created Pods that have no assigned node (`nodeName: ""`) to an optimal Worker Node.
- Operates in a two-stage pipeline:
  1. **Filtering (Predicates)**: Eliminates nodes that do not meet requirements (insufficient CPU/memory, taints without tolerations, node selector mismatch).
  2. **Scoring (Priorities)**: Ranks surviving candidate nodes based on resource balance, image locality, and pod affinity rules.

### 4. `kube-controller-manager`
- Runs continuous **reconciliation loops** (the control loop: *Observe $\to$ Compare $\to$ Reconcile*).
- Examples:
  - **DeploymentController**: Ensures current ReplicaSets match desired replicas.
  - **NodeController**: Monitors node heartbeats and marks unreachable nodes as `NotReady`.
  - **EndpointSliceController**: Populates IP addresses of healthy pods backing Kubernetes Services.

---

## 3. Worker Node Components (The Muscle)

### 1. `kubelet`
- The primary node agent registered with the API server.
- Watches for PodSpecs assigned to its node, instructs the container runtime to create/stop containers, mounts storage volumes, and reports node/pod health metrics back to the API server.
- Contains the **PLEG (Pod Lifecycle Event Generator)**: periodically polls container runtimes to detect status changes.

### 2. `kube-proxy`
- Network proxy running on each node that implements the Kubernetes **Service** abstraction.
- Monitors Service and EndpointSlice objects.
- Programs Linux kernel routing tables via:
  - **`iptables` mode**: Default in many installations. Writes DNAT rules for Service Virtual IPs.
  - **`ipvs` mode**: High-performance hash-table based routing for clusters with thousands of services.

### 3. Container Runtime & The 3 Core Interfaces

```mermaid
flowchart LR
    Kubelet["kubelet"] -->|CRI (gRPC)| ContainerRuntime["Container Runtime (containerd)"]
    ContainerRuntime -->|CNI (Plugins)| NetworkPlugin["CNI Plugin (Cilium / Calico / Flannel)"]
    Kubelet -->|CSI (gRPC)| StoragePlugin["CSI Driver (AWS EBS / Ceph / NFS)"]

    style Kubelet fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
    style ContainerRuntime fill:#fdf4ff,stroke:#c084fc,stroke-width:1px
    style NetworkPlugin fill:#ecfdf5,stroke:#34d399,stroke-width:1px
    style StoragePlugin fill:#fffbeb,stroke:#fbbf24,stroke-width:1px
```

1. **CRI (Container Runtime Interface)**: Standardized gRPC interface between `kubelet` and container runtimes (`containerd`, `CRI-O`).
2. **CNI (Container Network Interface)**: Standardized plugins for configuring network interfaces, allocating Pod IP addresses, and managing routing tables.
3. **CSI (Container Storage Interface)**: Standardized interface allowing third-party storage vendors (AWS EBS, GCP PD, Azure Disk, Ceph, Rook) to attach and mount block/file storage into pods.

---

## 4. End-to-End Lifecycle of `kubectl apply -f deployment.yaml`

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / CI/CD
    participant API as kube-apiserver
    participant ETCD as etcd
    participant DeployCtrl as Deployment Controller
    participant ReplicaCtrl as ReplicaSet Controller
    participant Sched as kube-scheduler
    participant Kubelet as Worker Node kubelet
    participant CRI as containerd

    Dev->>API: kubectl apply -f deployment.yaml
    API->>API: Authenticate, Authorize (RBAC), Validate
    API->>ETCD: Save Deployment object
    DeployCtrl->>API: Watch: notices new Deployment
    DeployCtrl->>API: Creates matching ReplicaSet
    ReplicaCtrl->>API: Watch: notices new ReplicaSet
    ReplicaCtrl->>API: Creates Pods with nodeName = ""
    API->>ETCD: Persist unassigned Pods
    Sched->>API: Watch: sees unassigned Pods
    Sched->>Sched: Run Filtering & Scoring algorithms
    Sched->>API: Bind Pod to selected Node (node-01)
    API->>ETCD: Update Pod nodeName = node-01
    Kubelet->>API: Watch: sees Pod assigned to node-01
    Kubelet->>CRI: Create sandboxed container (Pause)
    Kubelet->>CRI: Pull application image & start containers
    Kubelet->>API: Report Pod status: Running
    API->>ETCD: Persist active status
```

---

## 5. Official References & Documentation
- [Kubernetes Architectural Concepts](https://kubernetes.io/docs/concepts/overview/components/)
- [etcd Distributed Raft Consensus](https://etcd.io/docs/)
- [Kube-Scheduler Internals & Plugins](https://kubernetes.io/docs/concepts/scheduling-eviction/kube-scheduler/)
- [Kubernetes CRI Specification](https://github.com/kubernetes/cri-api)

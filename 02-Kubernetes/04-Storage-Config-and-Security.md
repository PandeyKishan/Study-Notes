# Kubernetes: Storage, Configs, Secrets & RBAC Security

> **Cluster 02 — Module 04**  
> Focus: PV/PVC binding lifecycle, StorageClasses, ConfigMaps, Secrets, RBAC authorization, and Pod Security Standards.

---

## 1. Kubernetes Storage Architecture: PV, PVC & StorageClasses

Because container filesystems are ephemeral, persistent state requires decoupled storage volumes managed by the Container Storage Interface (CSI).

```mermaid
flowchart LR
    Dev["Developer (Application Team)"] -->|Declares need for 50Gi| PVC["PersistentVolumeClaim (PVC)\n'I need 50Gi ReadWriteOnce'"]
    SC["StorageClass\n(e.g., gp3-csi, local-path)\nProvisioner: ebs.csi.aws.com"] -->|Dynamic Provisioning| PV["PersistentVolume (PV)\nRepresents actual cloud disk / EBS volume"]
    PVC <-->|Bound 1-to-1| PV
    Pod["Application Pod"] -->|Mounts PVC as Volume| PVC

    style PVC fill:#e0f2fe,stroke:#0284c7,stroke-width:2px
    style PV fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style SC fill:#f3e8ff,stroke:#9333ea,stroke-width:2px
    style Pod fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

### Storage Access Modes
- **`ReadWriteOnce` (RWO)**: Volume can be mounted as read-write by a **single node**. (Default for AWS EBS, Azure Disk).
- **`ReadOnlyMany` (ROX)**: Volume can be mounted read-only by **many nodes** simultaneously.
- **`ReadWriteMany` (RWX)**: Volume can be mounted read-write by **many nodes** simultaneously (e.g. NFS, AWS EFS, CephFS).
- **`ReadWriteOncePod` (RWOP)**: Mountable as read-write by a single Pod only.

### Reclaim Policies
- **`Delete` (Default)**: When PVC is deleted, the underlying storage backend (e.g., AWS EBS volume) is permanently deleted.
- **`Retain`**: When PVC is deleted, the PV remains in `Released` state. Data is preserved for manual recovery.

---

## 2. ConfigMaps and Secrets

Configuration must be decoupled from application image artifacts according to 12-factor application guidelines.

```mermaid
flowchart TD
    subgraph K8s_Configs["Cluster Configuration Objects"]
        CM["ConfigMap\nNon-sensitive configurations\n(PORT, LOG_LEVEL, TOPIC_NAME)"]
        Sec["Secret\nSensitive credentials\n(DB_PASSWORD, KAFKA_SASL_KEY)"]
    end

    subgraph Pod_Injection["Pod Injection Modes"]
        Env["1. Environment Variables (valueFrom)\nEvaluated at container startup"]
        Vol["2. Projected Volume Mounts\nMounted as files in directory"]
    end

    CM --> Env
    CM --> Vol
    Sec --> Env
    Sec --> Vol

    style CM fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style Sec fill:#fef2f2,stroke:#ef4444,stroke-width:1px
```

> [!WARNING]
> **Base64 is NOT Encryption!**  
> Kubernetes Secrets are stored in `etcd` as Base64-encoded strings by default. Anyone with API access to read secrets can decode them via `echo "cGFzc3dvcmQ=" | base64 -d`.  
> **Production Best Practice**: Enable etcd encryption-at-rest (`EncryptionConfiguration`) and use the **External Secrets Operator (ESO)** to sync secrets securely from AWS Secrets Manager, HashiCorp Vault, or Azure Key Vault.

---

## 3. RBAC (Role-Based Access Control)

Kubernetes uses RBAC to determine whether an entity (User or ServiceAccount) can perform a specific **verb** (`get`, `list`, `create`, `delete`) on a **resource** (`pods`, `services`, `secrets`).

```mermaid
flowchart TD
    subgraph Identity["1. Subject (Who?)"]
        SA["ServiceAccount: order-deployer-sa\n(Namespace: production)"]
    end

    subgraph Permissions["2. Role / ClusterRole (What?)"]
        R["Role: pod-manager\napiGroups: ['']\nresources: ['pods']\nverbs: ['get', 'list', 'watch', 'create']"]
    end

    subgraph Binding["3. RoleBinding (Bridge)"]
        RB["RoleBinding: bind-order-deployer\nConnects Subject to Role"]
    end

    SA --> RB
    RB --> R

    style SA fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
    style R fill:#ecfdf5,stroke:#10b981,stroke-width:2px
    style RB fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
```

### Namespace Scoped vs Cluster Scoped
- **`Role` + `RoleBinding`**: Confined strictly to a single Kubernetes namespace.
- **`ClusterRole` + `ClusterRoleBinding`**: Cluster-wide permissions (Nodes, PersistentVolumes, Namespaces, or resources across all namespaces).

---

## 4. Hardening Workloads with SecurityContext

Production workloads should enforce the **Restricted** Pod Security Standard:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hardened-app
spec:
  replicas: 2
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: app
          image: myapp:1.0.0
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
          volumeMounts:
            - name: writable-temp
              mountPath: /tmp
      volumes:
        - name: writable-temp
          emptyDir: {}
```

---

## 5. Official References
- [Kubernetes Storage Overview](https://kubernetes.io/docs/concepts/storage/)
- [Configuring Pods with Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Using RBAC Authorization](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)
- [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/)

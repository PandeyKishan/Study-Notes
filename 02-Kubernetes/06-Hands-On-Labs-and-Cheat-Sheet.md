# Kubernetes: CLI Cheat Sheet & Practical Hands-On Labs

> **Cluster 02 — Module 06**  
> Focus: Essential `kubectl` operational cheat sheet, local cluster setup (Kind/Minikube), and production workload deployment.

---

## 1. Production `kubectl` Cheat Sheet

### Context & Configuration
```bash
# Display current context and cluster
kubectl config current-context

# Switch default namespace for all future commands
kubectl config set-context --current --namespace=production

# View API resources and their short names
kubectl api-resources
```

### Workload Operations & Rollouts
```bash
# Apply declarative configuration
kubectl apply -f deployment.yaml

# Perform a zero-downtime rolling restart of all pods in a deployment
kubectl rollout restart deployment/order-service

# Watch status of an in-progress rolling update
kubectl rollout status deployment/order-service

# Roll back to the previous deployment revision immediately
kubectl rollout undo deployment/order-service

# Scale deployment replicas
kubectl scale deployment/order-service --replicas=5
```

### Debugging & Live Triage
```bash
# Stream live logs from all pods matching a label selector
kubectl logs -l app=order-service -f --tail=100

# Forward local port 8080 to Pod or Service port 3000
kubectl port-forward service/order-service 8080:3000

# Open interactive shell in a pod
kubectl exec -it <pod_name> -- /bin/sh

# Launch an ephemeral debug container inside the target pod
kubectl debug -it <pod_name> --image=nicolaka/netshoot --target=order-service
```

### JSONPath & Resource Filtering Power Tricks
```bash
# Get all failing pods across ALL namespaces
kubectl get pods -A --field-selector status.phase!=Running,status.phase!=Succeeded

# Print only Pod names and their IP addresses using jsonpath
kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.podIP}{"\n"}{end}'

# Find which node a specific pod is running on
kubectl get pod <pod_name> -o jsonpath='{.spec.nodeName}'
```

---

## 2. Hands-On Lab 1: Local Cluster Setup (Kind / Minikube)

### Option A: Using `kind` (Kubernetes in Docker - Recommended)
1. Install `kind`:
   ```bash
   # Windows (via Chocolatey or Scoop)
   choco install kind
   # Or download binary from GitHub releases
   ```
2. Create multi-node cluster config (`kind-config.yaml`):
   ```yaml
   kind: Cluster
   apiVersion: kind.x-k8s.io/v1alpha4
   nodes:
     - role: control-plane
     - role: worker
     - role: worker
   ```
3. Spin up cluster:
   ```bash
   kind create cluster --name lab-cluster --config kind-config.yaml
   kubectl cluster-info --context kind-lab-cluster
   kubectl get nodes
   ```

---

## 3. Hands-On Lab 2: Self-Healing Workload with Health Probes

Create a file named `resilient-app.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  APP_ENV: "production"
  LOG_LEVEL: "info"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilient-demo
  labels:
    app: resilient-demo
spec:
  replicas: 3
  selector:
    matchLabels:
      app: resilient-demo
  template:
    metadata:
      labels:
        app: resilient-demo
    spec:
      containers:
        - name: web
          image: nginx:alpine
          ports:
            - containerPort: 80
          envFrom:
            - configMapRef:
                name: app-config
          resources:
            requests:
              cpu: "100m"
              memory: "64Mi"
            limits:
              cpu: "250m"
              memory: "128Mi"
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 3
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: resilient-service
spec:
  type: ClusterIP
  selector:
    app: resilient-demo
  ports:
    - port: 80
      targetPort: 80
```

### Verification & Testing
```bash
# 1. Apply the manifests
kubectl apply -f resilient-app.yaml

# 2. Verify all 3 pods reach 1/1 Ready status
kubectl get pods -l app=resilient-demo

# 3. Simulate failure: Kill the Nginx process in one pod
POD_NAME=$(kubectl get pods -l app=resilient-demo -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POD_NAME -- nginx -s stop

# 4. Observe self-healing: Kubelet detects liveness probe failure and restarts the container!
kubectl get pods -l app=resilient-demo -w
```

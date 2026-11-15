# Capstone Project: Enterprise Spring Boot 3, Kafka & Kubernetes Microservices

> **Uniting Java 21, Spring Boot 3, Apache Kafka, Docker, Kubernetes, and CI/CD into a production-grade distributed architecture.**  
> **Level**: Beginner-Friendly Walkthrough to Senior Architectural Demonstration

---

## 🌱 0. The Beginner's Intuition: What Are We Building & Why?

### The Real-World Scenario
When you click **"Buy Now"** on Amazon:
1. The **Order Service** needs to charge your payment and create an order immediately.
2. The **Notification Service** needs to send an email and SMS confirmation to your phone.
3. The **Inventory Service** needs to decrement warehouse stock.

### The Problem with Direct Synchronous REST Calls:
If the Order Service made an HTTP call directly to the Notification Service:
- If the email provider is slow, your checkout spinner hangs for 15 seconds.
- If the Notification Service crashes, the customer's order is rejected with an error!

### The Solution: Event-Driven Architecture with Apache Kafka
- The Order Service creates the order in 2 milliseconds, drops a message into **Apache Kafka** (`OrderPlacedEvent`), and immediately returns a green success response to the customer.
- The Notification Service listens to Kafka independently. If it was offline for 10 minutes for maintenance, it catches up on messages the moment it comes back online. **Zero orders are ever lost or delayed!**

```mermaid
flowchart TD
    Client["User Browser / Mobile App\n(cURL / Postman)"] -->|1. POST /api/v1/orders\n{ customerId, item, amount }| API["order-service (Producer)\n[Spring Boot 3 - Java 21]\nKafkaTemplate with String/JsonSerializer"]

    subgraph K8s_Cluster["Kubernetes Cluster (Namespace: cloudnative-lab)"]
        direction TB
        Ingress["Ingress Controller (NGINX)\nHost: api.orders.local"] -->|Route HTTP Traffic| SvcOrder["Service: order-service (ClusterIP)"]
        SvcOrder --> API

        API -->|2. Keyed Message: key=customerId\nPayload=OrderEvent(UUID, item, amount)| KafkaBroker["Apache Kafka (KRaft Mode)\n[StatefulSet + Headless Service]\nTopic: orders.v1 (3 Partitions)"]

        KafkaBroker -->|3. Consumer Group: notification-group\n@KafkaListener + ErrorHandlingDeserializer| Worker["notification-service (Consumer)\n[Spring Boot 3 - Java 21]\nConcurrent Listener (3 Threads)"]

        Worker -->|4. Simulated Business Action| Action["Dispatches Customer Email & SMS"]
    end

    style K8s_Cluster fill:#f1f5f9,stroke:#64748b,stroke-width:2px,color:#0f172a
    style Client fill:#e2e8f0,stroke:#334155,stroke-width:2px,color:#0f172a
    style Ingress fill:#93c5fd,stroke:#1d4ed8,stroke-width:2px,color:#0f172a
    style SvcOrder fill:#fde047,stroke:#b45309,stroke-width:2px,color:#0f172a
    style API fill:#86efac,stroke:#15803d,stroke-width:2px,color:#0f172a
    style KafkaBroker fill:#fde047,stroke:#b45309,stroke-width:2px,color:#0f172a
    style Worker fill:#93c5fd,stroke:#1d4ed8,stroke-width:2px,color:#0f172a
    style Action fill:#86efac,stroke:#15803d,stroke-width:2px,color:#0f172a
```

---

## ⚡ 1. Before vs. After: Architecture Comparison

| Aspect | Before (Monolithic Synchronous REST) | After (Event-Driven Cloud-Native) | Why This Helps (The Impact) |
| :--- | :--- | :--- | :--- |
| **System Coupling** | Order Service directly dependent on Notification Service IP. | Completely decoupled; communicating via asynchronous Kafka topic. | Deploy or upgrade notification service anytime without touching order service. |
| **Failure Blast Radius** | Notification crash causes checkout to fail for customers. | Notification crash buffers events safely in Kafka; user checkout succeeds in 5ms. | 100% resilient customer checkout experience. |
| **Deployment Model** | Manual jar execution on individual VMs; downtime during updates. | **Docker multi-stage containers** running on **Kubernetes Deployments**. | Automated self-healing, scaling, and zero-downtime rolling updates. |
| **Data Integrity** | Corrupted payload crashes consumer thread, freezing processing. | **`ErrorHandlingDeserializer` + Dead Letter Topic (DLT)** isolates poison pills. | Bad records are safely quarantined; healthy orders keep flowing. |

---

## 🛠️ 2. The Microservice Stack Overview

| Microservice | Technology Stack | Key Responsibilities | Port |
| :--- | :--- | :--- | :--- |
| **`order-service`** | Spring Boot 3.3, Java 21, Spring Kafka, Actuator, Maven | Exposes `POST /api/v1/orders`, serializes JSON payloads, auto-provisions Kafka topic `orders.v1` with 3 partitions, publishes keyed events via `KafkaTemplate`. | `8081` (Host) / `8080` (Internal) |
| **`notification-service`** | Spring Boot 3.3, Java 21, Spring Kafka, Actuator, Maven | Listens to topic `orders.v1` with 3 concurrent worker threads, handles poison pills via `ErrorHandlingDeserializer`, dispatches simulated customer notifications. | `8082` (Host) / `8081` (Internal) |
| **`kafka`** | Apache Kafka 3.7 (KRaft mode) | Distributed event log quorum (Zero ZooKeeper dependency). | `9092` |

---

## 🚀 3. Phase 1: Local Quickstart with Docker Compose

You can boot up the entire distributed system (Kafka in KRaft mode, Order Producer, and Notification Consumer) with a single command.

### Step 1: Build and Start the Full Stack
Navigate to this directory and run:
```bash
docker compose up --build -d
```

### Step 2: Verify Container Health & Spring Actuator
```bash
docker compose ps
```
*Verify Spring Boot Actuator Liveness:*
- Order Service: `curl http://localhost:8081/actuator/health/liveness` $\to$ `{"status":"UP"}`
- Notification Service: `curl http://localhost:8082/actuator/health/liveness` $\to$ `{"status":"UP"}`

### Step 3: Send Test Orders

**Using PowerShell:**
```powershell
$body = @{
    customerId = "CUST-9021"
    item = "MacBook Pro M3 Max"
    amount = 3499.00
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8081/api/v1/orders -Method Post -Body $body -ContentType "application/json"
```

**Using cURL:**
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-9021","item":"MacBook Pro M3 Max","amount":3499.00}'
```

*Expected JSON Response:*
```json
{
  "message": "Order successfully created and published to Kafka",
  "order": {
    "orderId": "ORD-B4A1D9C2",
    "customerId": "CUST-9021",
    "item": "MacBook Pro M3 Max",
    "amount": 3499.00,
    "status": "CREATED",
    "timestamp": "2026-10-13T10:45:00.000Z"
  },
  "kafkaMetadata": {
    "topic": "orders.v1",
    "partition": 1,
    "offset": 0
  }
}
```

### Step 4: Watch Real-Time Consumer Processing
Open live streaming logs to see the Spring Boot `@KafkaListener` pick up the event:
```bash
docker logs -f notification-service
```
*Console Output:*
```text
[NotificationService] 📩 Received Event from Kafka:
  ├─ Partition: 1
  ├─ Offset:    0
  ├─ Key:       CUST-9021
  └─ Order ID:  ORD-B4A1D9C2
[NotificationService] 🚀 [EMAIL SENT] Confirmation sent to Customer 'CUST-9021' for item 'MacBook Pro M3 Max' (Total: $3499.00)
```

---

## ☸️ 4. Phase 2: Deploy to Kubernetes (Kind / Minikube)

### Step 1: Build Docker Images Locally
```bash
docker build -t order-service:latest ./order-service
docker build -t notification-service:latest ./notification-service

# If using Kind, load local images directly into the cluster:
kind load docker-image order-service:latest --name lab-cluster
kind load docker-image notification-service:latest --name lab-cluster
```

### Step 2: Apply Kubernetes Manifests in Sequence
```bash
# 1. Create Dedicated Namespace
kubectl apply -f k8s/00-namespace.yaml

# 2. Deploy Kafka StatefulSet & Headless Service
kubectl apply -f k8s/01-kafka-statefulset.yaml

# 3. Wait for Kafka to become Ready
kubectl wait --namespace cloudnative-lab --for=condition=ready pod --selector=app=kafka --timeout=120s

# 4. Deploy Spring Boot Microservices + Ingress
kubectl apply -f k8s/02-order-service.yaml
kubectl apply -f k8s/03-notification-service.yaml
kubectl apply -f k8s/04-ingress.yaml
```

### Step 3: Verify Health Probes
```bash
kubectl get pods -n cloudnative-lab
```
Both `order-service` and `notification-service` will report `1/1 Running` as Kubernetes monitors `/actuator/health/readiness` and `/actuator/health/liveness` automatically.

---

## 🧪 5. Phase 3: Production Failure Drills & Incident Scenarios

### Drill 1: Poison Pill Resilience (`ErrorHandlingDeserializer`)
Produce a non-JSON or corrupted byte string to the topic:
```bash
docker exec -it kafka /opt/bitnami/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.v1
> {CORRUPTED_NON_JSON}
```
**Observation**: Look at `notification-service` logs. Notice how `ErrorHandlingDeserializer` catches the deserialization exception, passes it to the `DefaultErrorHandler` which logs the error, **without crashing the container into CrashLoopBackOff and without blocking subsequent orders!**

### Drill 2: Zero-Downtime Rolling Update & Traffic Draining
1. Continuously query the service in PowerShell:
   ```powershell
   while ($true) {
       Invoke-RestMethod -Uri http://localhost:8081/actuator/health/liveness -Method Get
       Start-Sleep -Milliseconds 200
   }
   ```
2. Trigger a rolling restart:
   ```bash
   kubectl rollout restart deployment/order-service -n cloudnative-lab
   ```
3. **Observation**: Notice **zero 502/503 errors**. The `preStop` hook sleeps for 5 seconds allowing Ingress traffic to drain cleanly before Spring Boot gracefully shuts down.

---

## 💼 6. Interview Preparation: How to Talk About This Project

### The 60-Second Elevator Pitch
> *"I built a distributed, event-driven order processing and notification system using Java 21 and Spring Boot 3, containerized with multi-stage Docker builds and orchestrated on Kubernetes.  
> 
> The Order Service acts as a producer that pushes events to Apache Kafka running in KRaft consensus mode. To guarantee per-customer chronological ordering, events are keyed by Customer ID, ensuring all updates for a customer route to the same partition.  
> 
> On the consumer side, the Notification Service runs concurrent listeners with `ErrorHandlingDeserializer` to safely quarantine poison pills and corrupted payloads without halting partition consumption.  
> 
> In Kubernetes, the stack utilizes Spring Boot Actuator liveness/readiness probes, pod `preStop` lifecycle hooks, and JVM container-aware memory limits (`-XX:MaxRAMPercentage=75.0`), enabling zero-downtime rolling updates and preventing cgroup OOMKilled events."*

---

### Top Interview Questions About This Project

#### Q1: "Why did you use Kafka instead of a direct REST call between Order Service and Notification Service?"
- **The Answer**:
  - *"Using synchronous REST creates tight temporal coupling. If the Notification Service slows down or crashes, the Order Service's checkout call would fail for the customer.  
  - By using Kafka, checkout completes in milliseconds; the event is persisted to an append-only commit log. The Notification Service consumes messages asynchronously at its own pace without impacting user transactions."*

#### Q2: "How did you prevent duplicate message processing?"
- **The Answer**:
  - *"We configured the Kafka producer with `enable.idempotence=true` and `acks=all`, which assigns a Producer ID and sequence number to deduplicate network retries at the broker level.  
  - On the consumer side, we track processed `orderId`s in the database with a unique index, ensuring idempotent processing even under consumer rebalances."*

#### Q3: "Why did you deploy Kafka as a StatefulSet instead of a Deployment in Kubernetes?"
- **The Answer**:
  - *"Kafka brokers maintain persistent commit logs on disk and require stable, predictable network identities.  
  - Deployments assign random pod names and IPs, which break distributed broker discovery.  
  - A StatefulSet provides stable network names (`kafka-0`), ordinal scaling, and dedicated `volumeClaimTemplates` that ensure persistent storage volumes remain attached to the same broker across restarts."*

# Capstone Project: Enterprise Spring Boot 3, Kafka & Kubernetes Microservices

> **Uniting Java 21, Spring Boot 3, Apache Kafka, Docker, Kubernetes, and CI/CD into a production-grade distributed architecture.**

---

## 🏛️ System Architecture & Event Flow

This project implements an **Event-Driven Order Processing & Notification System** built with **Java 21** and **Spring Boot 3**. It showcases how modern enterprise systems achieve high throughput, resilience against poison pills, zero-downtime rolling updates, and automated delivery pipelines.

```mermaid
flowchart TD
    Client["Client (cURL / Postman / Frontend)"] -->|POST /api/v1/orders\n{ customerId, item, amount }| API["order-service (Producer)\n[Spring Boot 3 - Java 21]\nKafkaTemplate with String/JsonSerializer"]
    
    subgraph K8s_Cluster["Kubernetes Cluster (Namespace: cloudnative-lab)"]
        direction TB
        Ingress["Ingress Controller (NGINX)\nHost: api.orders.local"] -->|Route HTTP Traffic| SvcOrder["Service: order-service (ClusterIP)"]
        SvcOrder --> API
        
        API -->|Keyed Message: key=customerId\nPayload=OrderEvent(UUID, item, amount)| KafkaBroker["Apache Kafka (KRaft Mode)\n[StatefulSet + Headless Service]\nTopic: orders.v1 (3 Partitions)"]
        
        KafkaBroker -->|Consumer Group: notification-group\n@KafkaListener + ErrorHandlingDeserializer| Worker["notification-service (Consumer)\n[Spring Boot 3 - Java 21]\nConcurrent Listener (3 Threads)"]
        
        Worker -->|Simulated Business Action| Action["Dispatches Customer Email & SMS"]
    end

    style K8s_Cluster fill:#f8fafc,stroke:#0284c7,stroke-width:2px
    style API fill:#ecfdf5,stroke:#10b981,stroke-width:2px
    style KafkaBroker fill:#fffbeb,stroke:#f59e0b,stroke-width:2px
    style Worker fill:#eff6ff,stroke:#3b82f6,stroke-width:2px
```

---

## 🛠️ Microservice Stack Details

| Microservice | Technology Stack | Key Responsibilities | Port |
| :--- | :--- | :--- | :--- |
| **`order-service`** | Spring Boot 3.3, Java 21, Spring Kafka, Spring Actuator, Maven | Exposes `POST /api/v1/orders`, serializes JSON payloads, auto-provisions Kafka topic `orders.v1` with 3 partitions, publishes keyed events via `KafkaTemplate`. | `8080` |
| **`notification-service`** | Spring Boot 3.3, Java 21, Spring Kafka, Spring Actuator, Maven | Listens to topic `orders.v1` with 3 concurrent worker threads, handles poison pills via `ErrorHandlingDeserializer`, dispatches simulated customer notifications. | `8081` |
| **`kafka`** | Bitnami Kafka 3.7 (KRaft mode) | Distributed event log quorum (Zero ZooKeeper dependency). | `9092` |

---

## 🚀 Phase 1: Local Quickstart with Docker Compose

You can boot up the entire distributed system (Kafka in KRaft mode, Order Producer, and Notification Consumer) with a single command.

### 1. Build and Start the Stack
Navigate to this directory and run:
```bash
docker compose up --build -d
```

### 2. Verify Container Health & Spring Actuator
```bash
docker compose ps
```
*Verify Spring Boot Actuator Liveness:*
- Order Service: `curl http://localhost:8080/actuator/health/liveness` $\to$ `{"status":"UP"}`
- Notification Service: `curl http://localhost:8081/actuator/health/liveness` $\to$ `{"status":"UP"}`

### 3. Send Test Orders
Publish order events using your preferred terminal:

**Using PowerShell:**
```powershell
$body = @{
    customerId = "CUST-9021"
    item = "MacBook Pro M3 Max"
    amount = 3499.00
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/api/v1/orders -Method Post -Body $body -ContentType "application/json"
```

**Using cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-9021","item":"MacBook Pro M3 Max","amount":3499.00}'
```

*Response Received:*
```json
{
  "message": "Order successfully created and published to Kafka",
  "order": {
    "orderId": "ORD-B4A1D9C2",
    "customerId": "CUST-9021",
    "item": "MacBook Pro M3 Max",
    "amount": 3499.00,
    "status": "CREATED",
    "timestamp": "2026-10-04T10:45:00.000Z"
  },
  "kafkaMetadata": {
    "topic": "orders.v1",
    "partition": 1,
    "offset": 0
  }
}
```

### 4. Watch Real-Time Consumer Processing
Open live streaming logs to see the Spring Boot `@KafkaListener` pick up the event:
```bash
docker logs -f capstone-notification-service
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

## ☸️ Phase 2: Deploy to Kubernetes (Kind / Minikube)

### 1. Build Docker Images Locally
```bash
# Build the Spring Boot container images
docker build -t order-service:latest ./order-service
docker build -t notification-service:latest ./notification-service

# If using Kind, load local images directly into the cluster
kind load docker-image order-service:latest --name lab-cluster
kind load docker-image notification-service:latest --name lab-cluster
```

### 2. Apply Kubernetes Manifests in Sequence
```bash
# 1. Create Namespace
kubectl apply -f k8s/00-namespace.yaml

# 2. Deploy Kafka StatefulSet & Headless Service
kubectl apply -f k8s/01-kafka-statefulset.yaml

# 3. Wait for Kafka to become Ready
kubectl wait --namespace cloudnative-lab \
  --for=condition=ready pod \
  --selector=app=kafka \
  --timeout=120s

# 4. Deploy Spring Boot Microservices + Ingress
kubectl apply -f k8s/02-order-service.yaml
kubectl apply -f k8s/03-notification-service.yaml
kubectl apply -f k8s/04-ingress.yaml
```

### 3. Verify Health Probes
```bash
kubectl get pods -n cloudnative-lab
```
Both `order-service` and `notification-service` will report `1/1 Running` as Kubernetes monitors `/actuator/health/readiness` and `/actuator/health/liveness` automatically.

---

## 🧪 Phase 3: Senior Interview Scenarios & Failure Drills

### Drill 1: Poison Pill Resilience (`ErrorHandlingDeserializer`)
Produce a non-JSON or corrupted byte string to the topic:
```bash
docker exec -it capstone-kafka /opt/bitnami/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic orders.v1
> {CORRUPTED_NON_JSON}
```
**Observation**: Look at `notification-service` logs. Notice how `ErrorHandlingDeserializer` catches the deserialization exception, passes it to the `DefaultErrorHandler` which retries with backoff and logs the error, **without crashing the container into CrashLoopBackOff and without blocking subsequent orders!**

### Drill 2: Zero-Downtime Rolling Update & Traffic Draining
1. Continuously query the service in PowerShell:
   ```powershell
   while ($true) {
       Invoke-RestMethod -Uri http://localhost:8080/actuator/health/liveness -Method Get
       Start-Sleep -Milliseconds 200
   }
   ```
2. Trigger a rolling restart:
   ```bash
   kubectl rollout restart deployment/order-service -n cloudnative-lab
   ```
3. **Observation**: Notice **zero 502/503 errors**. The `preStop` hook sleeps for 5 seconds allowing Ingress traffic to drain cleanly before Spring Boot gracefully shuts down.

---

## 💼 How to Pitch This Project in Interviews

> **Sample 60-Second Interview Response:**  
> *"I designed an enterprise event-driven order processing microservices architecture built with Java 21 and Spring Boot 3, containerized with multi-stage Docker builds and orchestrated on Kubernetes.  
> 
> The Order API uses `KafkaTemplate` with idempotent producing enabled (`enable.idempotence=true`) and `acks=all`, keying events by customer ID to guarantee strict partition-level chronological ordering across Kafka brokers running in KRaft consensus mode.  
> 
> To safeguard consumers against poison pills, the Notification Service implements `ErrorHandlingDeserializer` with a `DefaultErrorHandler` and Dead Letter publishing, preventing serialization crashes from halting the partition thread.  
> 
> On Kubernetes, the services leverage Spring Boot Actuator's native liveness and readiness probes, coupled with pod `preStop` hooks and JVM container memory settings (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`), ensuring zero-downtime rolling updates and preventing cgroup OOMKilled events."*

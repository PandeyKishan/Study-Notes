# Docker: Networking, Storage Volumes & Compose Orchestration

> **Cluster 01 — Module 03**  
> **Level**: Beginner-Friendly Foundations to Production Architecture  
> **Key References**: TechWorld with Nana, Docker Networking & Storage Docs

---

## 🌱 0. The Beginner's Mental Model: The Three Big Challenges

Once you learn how to run a single container, you immediately hit three real-world problems:
1. **The Networking Problem**: How does my web app talk to my database container?
2. **The Data Loss Problem**: If my database container crashes or restarts, does all my data disappear?
3. **The Multi-Container Problem**: Do I really have to type 5 complex `docker run` commands in the terminal every time I start my project?

```mermaid
flowchart TD
    subgraph Problems["The 3 Real-World Challenges"]
        P1["1. Networking: How do containers find and talk to each other?"]
        P2["2. Storage: Containers are ephemeral! How do we prevent data loss?"]
        P3["3. Orchestration: Managing multiple containers manually is tedious and error-prone."]
    end

    subgraph Solutions["The Docker Solutions"]
        S1["Docker Networks & Built-in DNS"]
        S2["Docker Volumes & Bind Mounts"]
        S3["Docker Compose (docker-compose.yml)"]
    end

    P1 ==> S1
    P2 ==> S2
    P3 ==> S3

    style Solutions fill:#dcfce7,stroke:#16a34a,stroke-width:2px
```

---

## ⚡ 1. Before vs. After: Networking, Storage & Compose

| Feature | Before (The Manual Nightmare) | After (Docker Native Solution) | Why This Helps (The Impact) |
| :--- | :--- | :--- | :--- |
| **Container Communication** | Hardcoding container IP addresses (`172.17.0.3`). If a container restarts, its IP changes and the app breaks! | User-defined bridge network with **automatic DNS**. Just use the service name: `http://postgres:5432`. | Resilient, dynamic microservice communication with zero manual IP management. |
| **Port Exposure** | Port conflicts on the host machine. You can't run two web servers on port 80. | **Port Mapping (`-p 8080:80`)**. Host port routes cleanly to isolated container port. | Run multiple instances of the same service on a single laptop or server without collisions. |
| **Data Persistence** | When a container is deleted (`docker rm`), all database rows and uploads are **permanently wiped out**. | **Docker Volumes (`-v db_data:/var/lib/mysql`)**. Data lives safely on the host disk outside the container. | You can destroy, recreate, and upgrade containers without losing a single byte of data. |
| **Multi-Service Startup** | Writing 50-line shell scripts with chained `docker run` commands in the exact right order. | **Docker Compose (`docker compose up -d`)**. Declarative configuration for the entire multi-service stack. | One single command boots up the frontend, backend, database, and cache with automated wiring. |

---

## 🌐 2. Docker Networking: How Containers Talk

### The Apartment Intercom Analogy
*(Popularized by TechWorld with Nana)*

- Imagine an apartment building with several units (containers).
- By default, Unit 101 cannot hear Unit 102 through the soundproof walls (namespace isolation).
- When you connect containers to the same **Docker Network**, you give them an internal intercom system:
  - Unit 101 can call Unit 102 simply by speaking its name: *"Hey, Database!"*
  - The outside world (the internet) cannot access the intercom directly unless the front desk receptionist assigns an external doorbell (**Port Mapping: `-p 8080:80`**).

```mermaid
flowchart TD
    subgraph Outside["The Outside World (Internet / Host Laptop)"]
        Browser["User Browser: http://localhost:3000"]
    end

    subgraph DockerHost["Docker Host (Bridge Network: app-net)"]
        direction TB
        Browser -->|Port Map: -p 3000:80| Web["Frontend Nginx Container\nInternal IP: 172.20.0.2"]
        Web -->|HTTP Call: http://api:8080\n(Docker DNS resolves 'api'!)| API["Backend Spring Boot / Node Container\nInternal IP: 172.20.0.3"]
        API -->|TCP Call: postgres:5432\n(Docker DNS resolves 'postgres'!)| DB[("PostgreSQL Database\nInternal IP: 172.20.0.4\n*Port 5432 NOT exposed to Internet!*")]
    end

    style Outside fill:#eff6ff,stroke:#1d4ed8,stroke-width:1px
    style DockerHost fill:#f0fdf4,stroke:#16a34a,stroke-width:2px
    style DB fill:#fef3c7,stroke:#d97706,stroke-width:2px
```

### Docker Network Drivers Explained:
1. **Bridge (Default)**:
   - Creates a virtual private software bridge inside the host.
   - Containers get private IPs (e.g., `172.18.0.x`).
   - *CRITICAL INTERVIEW FACT*: The **default bridge** (`docker0`) does **not** provide automatic DNS name resolution. You **must** create a **user-defined bridge network** (`docker network create my-net`) to get automatic DNS resolution!
2. **Host Network (`--network host`)**:
   - Skips network isolation entirely. The container binds directly to the host's network interfaces.
   - Offers maximum performance (zero NAT overhead), but port conflicts can occur.
3. **None (`--network none`)**:
   - Completely air-gapped with no network access except local loopback. Used for secure offline batch jobs.
4. **Overlay Network**:
   - Connects multiple Docker daemons across different physical machines (used in Docker Swarm and Kubernetes).

---

## 💾 3. Docker Storage: Volumes vs. Bind Mounts

### The Hotel Room Safe Analogy
Containers have an ephemeral, disposable writable layer. If you check out of a hotel room, the cleaning staff throws away everything left behind in the room. 
If you want your valuables (database data, customer uploads) to survive, you place them inside a **permanent storage locker (Docker Volume)** located on the host.

```mermaid
flowchart LR
    subgraph Host_Storage["Host Machine Physical Storage"]
        Vol["Docker Managed Volume\n(/var/lib/docker/volumes/my_data)\n*Managed purely by Docker Engine*"]
        Bind["Bind Mount\n(C:/Users/name/projects/my-app/src)\n*Existing folder on host filesystem*"]
        Tmp["tmpfs Mount\n(RAM Memory Only)\n*Never touches host disk*"]
    end

    subgraph Running_Container["Running Container Namespace"]
        App["Container Process"]
    end

    Vol ==>|1. Best for DBs & Production Persistence| App
    Bind ==>|2. Best for Local Development & Hot-Reload| App
    Tmp ==>|3. Best for Sensitive In-Memory Secrets| App

    style Vol fill:#dcfce7,stroke:#16a34a,stroke-width:2px
    style Bind fill:#eff6ff,stroke:#2563eb,stroke-width:2px
    style Tmp fill:#fee2e2,stroke:#ef4444,stroke-width:2px
```

### Storage Decision Matrix:

| Type | How It Works | Best Use Case | Command Example |
| :--- | :--- | :--- | :--- |
| **Named Volume** | Docker creates and manages a folder inside `/var/lib/docker/volumes/`. High performance, completely independent of host directory structure. | **Production Databases (PostgreSQL, MySQL, MongoDB), Kafka logs.** | `docker run -v pg_data:/var/lib/postgresql/data postgres:15` |
| **Bind Mount** | Directly mounts an exact file or folder from your computer into the container. | **Local Development**: Mount your local source code into the container so you can edit code and see hot reload without rebuilding images! | `docker run -v $(pwd)/src:/app/src node:20` |
| **`tmpfs` Mount** | Stores data purely in the host machine's RAM memory. Never writes to disk. | **Sensitive tokens, passwords, private keys** that must vanish the second the container stops. | `docker run --tmpfs /tmp/secrets my-app` |

---

## 🎼 4. Docker Compose: The Conductor of Multi-Container Apps

### What is Docker Compose?
**Docker Compose** is a tool for defining and running multi-container Docker applications using a single declarative YAML file (`compose.yaml` or `docker-compose.yml`).

### Production-Ready Example: Frontend + Spring Boot Backend + PostgreSQL + Kafka
*(Aligned with our Hands-On Capstone Project)*

```yaml
version: "3.8"

services:
  # 1. Database Service
  postgres:
    image: postgres:15-alpine
    container_name: postgres-db
    environment:
      POSTGRES_DB: orders_db
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password123
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - cloudnative-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d orders_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  # 2. Apache Kafka Message Broker
  kafka:
    image: apache/kafka:3.7.0
    container_name: kafka-broker
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    networks:
      - cloudnative-net

  # 3. Order Microservice (Spring Boot)
  order-service:
    build:
      context: ./order-service
      dockerfile: Dockerfile
    container_name: order-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orders_db
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: password123
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres:
        condition: service_healthy # Waits until Postgres is actually READY to accept connections!
      kafka:
        condition: service_started
    networks:
      - cloudnative-net

volumes:
  postgres-data:

networks:
  cloudnative-net:
    driver: bridge
```

### Essential Docker Compose Commands:
- `docker compose up -d`: Builds images (if needed), creates networks/volumes, and starts all containers in the background.
- `docker compose ps`: Shows the status and health of all services in the stack.
- `docker compose logs -f <service>`: Streams real-time logs from a specific container.
- `docker compose down`: Gracefully stops and removes containers and internal networks.
- `docker compose down -v`: Stops containers AND deletes the named volumes (clean slate!).

---

## 💼 5. Interview Preparation: High-Yield Questions & Answers

### Q1: "Why should you NOT use the default bridge network in production?"
- **The Answer**:
  - The default bridge network (`bridge` / `docker0`) lacks **automatic DNS resolution between containers**.
  - Containers on the default bridge can only reach each other via IP addresses or manual `--link` flags (which are deprecated).
  - On a **user-defined bridge network**, Docker automatically spins up an embedded DNS server (`127.0.0.11`) that resolves container and service names automatically.

---

### Q2: "What is the difference between a Volume and a Bind Mount?"
- **The Answer**:
  - **Volumes** are completely managed by Docker and stored in Docker's dedicated directory on the host (`/var/lib/docker/volumes/`). They are isolated from host file permissions, deliver optimal performance, and are the standard for production stateful workloads.
  - **Bind Mounts** point to an arbitrary path on the host filesystem (e.g. `./src`). They are tightly coupled to the host OS directory structure and permissions, making them ideal for local development hot-reloading.

---

### Q3: "What is the common issue with `depends_on` in Docker Compose, and how do you solve it?"
- **The Answer**:
  - By default, `depends_on` only checks if the container has **started**, not whether the application inside is ready to accept traffic.
  - For example, PostgreSQL starts the container in 1 second, but takes 10 seconds to initialize schemas and open port 5432. The backend app crashes immediately because the DB is not ready!
  - *The Solution*: Use Docker Compose **`healthcheck`** on the database and configure `depends_on: { postgres: { condition: service_healthy } }`.

---

### Q4: "How does Port Mapping work under the hood?"
- **The Answer**:
  - When you specify `-p 8080:80`, Docker interacts with the Linux kernel's **Netfilter / iptables** subsystem.
  - It creates a **Destination NAT (DNAT)** rule in the `PREROUTING` chain.
  - Any packet arriving at host network interface `eth0` on port `8080` has its destination IP rewritten to the container's private IP (`172.20.0.2`) and port `80`.

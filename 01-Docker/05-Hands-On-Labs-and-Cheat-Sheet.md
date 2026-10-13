# Docker: Hands-On Labs & Essential Command Cheat Sheet

> **Cluster 01 — Module 05**  
> **Level**: Beginner Step-by-Step Walkthrough to Advanced Kernel Labs  
> **Key References**: TechWorld with Nana, Docker CLI Official Reference

---

## 📋 1. The Everyday Docker Command Cheat Sheet

### Container Management
```bash
# Run a container in detached mode with port mapping and a custom name
docker run -d --name web-server -p 8080:80 nginx:alpine

# List active containers
docker ps

# List ALL containers (including stopped ones)
docker ps -a

# View real-time container resource usage (CPU, RAM, Network I/O)
docker stats

# Stream logs in real-time
docker logs -f web-server

# Open an interactive shell inside a running container
docker exec -it web-server sh

# Stop and gracefully terminate a container
docker stop web-server

# Force kill and remove a container
docker rm -f web-server
```

### Image Management
```bash
# Build an image from Dockerfile in current directory
docker build -t my-app:1.0 .

# List local images
docker images

# Tag an image for Docker Hub or GitHub Container Registry (GHCR)
docker tag my-app:1.0 username/my-app:1.0

# Push image to registry
docker push username/my-app:1.0

# Remove an image
docker rmi my-app:1.0

# View history of layers and their sizes
docker history my-app:1.0
```

### Networks & Volumes
```bash
# Create a custom bridge network (with built-in DNS)
docker network create app-network

# List networks
docker network ls

# Create a persistent volume
docker volume create db-data

# Inspect volume details (reveals host mount path)
docker volume inspect db-data

# Nuclear cleanup: Remove all stopped containers, unused networks, and dangling images
docker system prune -af --volumes
```

---

## 🧪 2. Beginner Lab 1: Your First Container with Persistence & Networking

### Objective
Deploy a PostgreSQL database and a lightweight Node/Python app, connect them using a custom Docker network, and verify data persists across container restarts.

```bash
# Step 1: Create a dedicated network
docker network create test-net

# Step 2: Create a named volume for the database
docker volume create test-pgdata

# Step 3: Run PostgreSQL on the custom network with the volume mounted
docker run -d \
  --name postgres-db \
  --network test-net \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=mydb \
  -v test-pgdata:/var/lib/postgresql/data \
  postgres:15-alpine

# Step 4: Verify the database is running
docker ps

# Step 5: Connect to the DB from a temporary client container using the hostname 'postgres-db'
docker run --rm -it --network test-net postgres:15-alpine \
  psql -h postgres-db -U postgres -d mydb -c "CREATE TABLE users (id SERIAL, name TEXT); INSERT INTO users (name) VALUES ('Alice');"

# Step 6: Test persistence: Kill and destroy the database container!
docker rm -f postgres-db

# Step 7: Spin up a brand new container using the same volume
docker run -d \
  --name postgres-db-new \
  --network test-net \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=mydb \
  -v test-pgdata:/var/lib/postgresql/data \
  postgres:15-alpine

# Step 8: Query the database. Your data is still there!
docker run --rm -it --network test-net postgres:15-alpine \
  psql -h postgres-db-new -U postgres -d mydb -c "SELECT * FROM users;"
```

---

## 🧪 3. Beginner Lab 2: Multi-Container Orchestration with Docker Compose

Create a `docker-compose.yml` file:
```yaml
version: "3.8"

services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    volumes:
      - ./html:/usr/share/nginx/html
    restart: always

  redis:
    image: redis:alpine
    volumes:
      - redis-cache:/data

volumes:
  redis-cache:
```

### Run and Test:
```bash
# 1. Start the entire application stack in the background
docker compose up -d

# 2. Check running services
docker compose ps

# 3. View live logs
docker compose logs -f

# 4. Tear down everything cleanly
docker compose down -v
```

---

## 🔬 4. Advanced Lab: Building a "Container" Manually with Linux Primitives

*(For Senior Interview Preparation)*

### Objective
Understand that containers are not magic by creating an isolated environment using standard Linux utilities without Docker!

```bash
# Run inside Linux terminal or WSL2:
mkdir -p /tmp/mini-rootfs && cd /tmp/mini-rootfs

# 1. Download Alpine minirootfs
wget https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/x86_64/alpine-minirootfs-3.19.1-x86_64.tar.gz
tar xf alpine-minirootfs-*.tar.gz && rm alpine-minirootfs-*.tar.gz

# 2. Unshare PID, Mount, and UTS namespaces
sudo unshare --mount --pid --uts --fork /bin/sh

# 3. Change isolated hostname
hostname my-diy-container

# 4. Pivot root filesystem
mkdir -p put_old
pivot_root . put_old
cd /
umount -l put_old && rmdir put_old

# 5. Mount proc filesystem for process tree isolation
mount -t proc proc /proc

# 6. Check processes. You are PID 1 inside your custom container!
ps aux
```

# Docker: CLI Cheat Sheet & Practical Hands-On Labs

> **Cluster 01 — Module 05**  
> Focus: Essential day-to-day command reference, system cleanup, and step-by-step hands-on exercises.

---

## 1. Production Docker CLI Cheat Sheet

### Container Lifecycle
```bash
# Run container detached with port mapping, custom name, and restart policy
docker run -d --name web-app -p 8080:80 --restart unless-stopped nginx:alpine

# Run container with resource limits and non-root user
docker run -d --name secure-app \
  --memory="512m" \
  --cpus="1.0" \
  --pids-limit 100 \
  --user 10001:10001 \
  --read-only \
  --tmpfs /tmp \
  my-image:latest

# Graceful stop with custom timeout (default is 10s)
docker stop -t 30 <container_id>

# Force terminate immediately
docker kill <container_id>

# Remove container (add -f to force remove running container)
docker rm -f <container_id>
```

### Diagnostics & Inspection
```bash
# Stream logs with timestamps and limit to last 200 lines
docker logs -f --tail 200 -t <container_id>

# Open an interactive shell inside a running container
docker exec -it <container_id> /bin/sh

# Show live streaming CPU, memory, and network usage
docker stats

# Inspect specific JSON properties (e.g. IP address)
docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container_id>

# View filesystem changes made in the container's writable layer
docker diff <container_id>
```

### Cleanup & Maintenance
```bash
# Clean stopped containers, dangling images, and unused networks
docker system prune -f

# Nuclear option: remove ALL unused containers, networks, images, and volumes
docker system prune -a --volumes -f

# Remove all dangling (untagged <none>) images
docker image prune -f
```

---

## 2. Hands-On Lab 1: Multi-Stage Image Optimization

### Goal
Experience the drastic reduction in image size, security vulnerabilities, and attack surface by migrating a naive build into a multi-stage build.

### Step 1: Create a sample Node.js application
Create a directory `lab-app` and add:

```json
// package.json
{
  "name": "lab-demo",
  "version": "1.0.0",
  "main": "server.js",
  "dependencies": {
    "express": "^4.19.2"
  },
  "devDependencies": {
    "typescript": "^5.4.5"
  }
}
```

```javascript
// server.js
const express = require('express');
const app = express();
const PORT = process.env.PORT || 3000;

app.get('/', (req, res) => res.json({ status: "healthy", timestamp: Date.now() }));
app.listen(PORT, () => console.log(`Listening on port ${PORT}`));
```

### Step 2: Compare Naive vs Multi-Stage Build
**Naive Dockerfile (`Dockerfile.naive`)**:
```dockerfile
FROM node:20
WORKDIR /app
COPY . .
RUN npm install
CMD ["node", "server.js"]
```
*Resulting image size:* **~1.1 GB** (includes full Debian OS, build utilities, TypeScript compiler, npm cache).

**Optimized Multi-Stage Dockerfile (`Dockerfile.optimized`)**:
```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/node_modules ./node_modules
COPY . .
USER appuser
EXPOSE 3000
CMD ["node", "server.js"]
```
*Resulting image size:* **~120 MB** (Over **89% reduction** in size and 0 known critical CVEs).

---

## 3. Hands-On Lab 2: Network Isolation & DNS Discovery

### Goal
Verify that containers on a user-defined bridge network can communicate via hostname, while remaining isolated from other networks.

```bash
# 1. Create two isolated networks
docker network create frontend-net
docker network create backend-net

# 2. Start an isolated database on backend-net
docker run -d --name secure-redis --network backend-net redis:alpine

# 3. Start a container on frontend-net
docker run -d --name web-client --network frontend-net alpine sleep 3600

# 4. Attempt to ping redis from web-client (Will FAIL due to network isolation)
docker exec -it web-client ping -c 2 secure-redis
# Output: ping: bad address 'secure-redis'

# 5. Connect web-client to backend-net
docker network connect backend-net web-client

# 6. Retry ping (SUCCEEDS via Docker's embedded DNS)
docker exec -it web-client ping -c 2 secure-redis
# Output: 64 bytes from secure-redis (172.x.x.x): seq=1 ttl=64 time=0.082 ms
```

---

## 4. Hands-On Lab 3: Inspecting Linux Namespaces & cgroups

For users on Linux or WSL2, you can verify container isolation directly using host kernel tools:

```bash
# Find the host PID of your container
CONTAINER_PID=$(docker inspect --format '{{.State.Pid}}' <container_id>)
echo "Host PID is: $CONTAINER_PID"

# Inspect namespaces assigned to this process
ls -l /proc/$CONTAINER_PID/ns/

# Inspect memory limits enforced by cgroups (cgroups v2)
cat /sys/fs/cgroup/system.slice/docker-<container_id>.scope/memory.max
```

# Dockerfile Mastery: Deep Dive, BuildKit & Security (Pro-Level)

> **Cluster 01 — Module 02**  
> Focus: Advanced BuildKit features, layer caching mechanics, multi-stage builds, PID 1 signaling, seccomp/AppArmor, and reproducible builds.

---

## 1. Advanced Layer Caching & Instruction Mechanics

Each command that mutates the filesystem (`RUN`, `COPY`, `ADD`) generates a new layer, represented by a cryptographic SHA256 digest of the layer's tarball.

### The BuildKit Engine
Modern Docker uses **BuildKit** as the backend engine. BuildKit builds concurrent dependency graphs, skipping unused stages and parallelizing independent instructions.

```mermaid
flowchart TD
    subgraph BuildCache["Docker Build Cache Invalidation Rules"]
        L1["Step 1: FROM node:20-alpine\n(Cached)"] --> L2["Step 2: WORKDIR /app\n(Cached)"]
        L2 --> L3["Step 3: COPY package*.json ./\n(Cached)"]
        L3 --> L4["Step 4: RUN npm ci\n(Cached)"]
        L4 --> L5["Step 5: COPY . .\n(CACHE MISS! File modified)"]
        L5 --> L6["Step 6: RUN npm run build\n(FORCED EXECUTION)"]
    end
```

### BuildKit Cache Mounts (Advanced)
Instead of relying purely on layer caching for package managers (like `npm` or `pip`), use BuildKit cache mounts. This keeps a persistent cache folder across builds, speeding up cache-miss scenarios.
```dockerfile
# syntax=docker/dockerfile:1.4
RUN --mount=type=cache,target=/root/.npm npm ci
```

---

## 2. Process Management: The PID 1 Trap

The container's main process runs as PID 1. In Linux, PID 1 has special responsibilities: reaping zombies and forwarding signals.

```mermaid
sequenceDiagram
    participant Docker as Docker Daemon
    participant Shell as PID 1: /bin/sh -c
    participant App as PID 2: node server.js

    Note over Docker,App: Anti-Pattern: CMD node server.js (Shell Form)
    Docker->>Shell: Send SIGTERM (graceful shutdown request)
    Note over Shell: /bin/sh ignores SIGTERM (does not forward)
    App-->>App: Continues running
    Note over Docker: 10s Timeout reaches!
    Docker->>Shell: Send SIGKILL (kill -9)
    Note over App: Hard Crash! Corruption possible.
```

**Solution:** Always use the `Exec` form (`CMD ["node", "server.js"]`) so the app becomes PID 1. Better yet, use a dedicated init system like `tini`:
```dockerfile
RUN apk add --no-cache tini
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
```

---

## 3. Multi-Stage Builds & Distroless / Scratch

To minimize attack surface, use multi-stage builds. To take it to the extreme, use Google's `distroless` images or the special `scratch` image.

```mermaid
flowchart LR
    subgraph Stage1["Builder Stage (Heavy)"]
        B_Base["FROM golang:1.21 AS builder"]
        B_Compile["CGO_ENABLED=0 go build -o myapp"]
    end

    subgraph Stage2["Production Stage (Empty)"]
        R_Base["FROM scratch"]
        R_Copy["COPY --from=builder /myapp /myapp"]
        R_CMD["ENTRYPOINT ['/myapp']"]
    end

    B_Compile -.-> R_Copy
```
The `scratch` image has literally nothing in it (no `ls`, no `sh`, no `libc`). Only statically compiled binaries (like Go or Rust) can run here. This makes container escape and remote code execution virtually impossible.

---

## 4. Pro-Grade Dockerfile Example

```dockerfile
# syntax=docker/dockerfile:1.4
# Stage 1: Build
FROM python:3.11-slim AS builder
WORKDIR /app
# Use BuildKit cache mounts and bind mounts to avoid copying unnecessary files
RUN --mount=type=cache,target=/root/.cache/pip \
    --mount=type=bind,source=requirements.txt,target=requirements.txt \
    pip wheel --no-deps --wheel-dir /app/wheels -r requirements.txt

# Stage 2: Runtime
FROM python:3.11-slim AS runtime
WORKDIR /app
# Run as non-root
RUN useradd --create-home appuser
USER appuser
# Install wheels built in previous stage
COPY --from=builder /app/wheels /wheels
RUN pip install --no-cache /wheels/*
COPY --chown=appuser:appuser src/ .

# Security profiles and healthchecks
HEALTHCHECK --interval=30s --timeout=5s CMD curl -f http://localhost:8000/health || exit 1
ENTRYPOINT ["python", "-m", "uvicorn", "main:app"]
```

---

## 5. Kernel Security Hardening

| Feature | Concept | Implementation |
| :--- | :--- | :--- |
| **Capabilities** | Granular Linux root privileges. | `docker run --cap-drop=ALL --cap-add=NET_BIND_SERVICE` |
| **Seccomp** | Restricts which Linux system calls the container can make. | `docker run --security-opt seccomp=custom.json` |
| **AppArmor/SELinux** | Mandatory Access Control (MAC) profiles to restrict file and network access. | `docker run --security-opt apparmor=docker-default` |
| **Read-Only FS** | Prevents modifying the root filesystem. | `docker run --read-only --tmpfs /tmp` |

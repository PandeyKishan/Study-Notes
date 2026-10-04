# Docker: Advanced CLI, eBPF Tracing & Kernel Hands-On Labs

> **Cluster 01 — Module 05**  
> Focus: Low-level system exploration, extracting namespaces, interacting with cgroups directly, and container escape forensics.

---

## 1. Advanced CLI & Forensics Cheat Sheet

### Image & Container Forensics
```bash
# Extract an entire container's filesystem to a tarball (even if stopped)
docker export <container_id> > container_filesystem.tar

# Inspect the underlying overlay2 layers of an image
docker inspect --format '{{json .GraphDriver.Data}}' my_image | jq .

# Find which container process is hogging disk I/O natively
sudo iotop -a -p $(docker inspect -f '{{.State.Pid}}' <container_id>)
```

### Network Diagnostics
```bash
# View the iptables rules injected by Docker natively on the host
sudo iptables -t nat -L DOCKER -n -v

# Analyze traffic inside a container's network namespace from the host
PID=$(docker inspect --format '{{.State.Pid}}' <container_id>)
sudo nsenter -t $PID -n tcpdump -i eth0 -w capture.pcap
```

---

## 2. Hands-On Lab 1: Manual Namespace Creation (Building a Container from Scratch)

### Goal
Understand how runc and containerd work by manually creating namespaces using standard Linux utilities, effectively building a "container" without Docker.

### Steps (Run on a Linux Host or WSL2)

```bash
# 1. Download a minimal Alpine root filesystem
mkdir alpine-rootfs && cd alpine-rootfs
wget https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/x86_64/alpine-minirootfs-3.19.1-x86_64.tar.gz
tar xf alpine-minirootfs-*.tar.gz
rm alpine-minirootfs-*.tar.gz

# 2. Use unshare to create new Mount, PID, and UTS namespaces
# Note: Requires root privileges
sudo unshare --mount --pid --uts --fork

# 3. Inside the new namespace, change the hostname (UTS namespace isolated)
hostname my-manual-container

# 4. Use pivot_root to safely isolate the mount namespace
# (Chroot is not used here because pivot_root swaps the actual mount root)
mount --bind . .  # Satisfy pivot_root requirement
mkdir -p put_old
pivot_root . put_old
cd /
umount -l put_old
rmdir put_old

# 5. Mount the proc filesystem (PID namespace requirement)
mount -t proc proc /proc

# 6. Test your isolation!
ps aux
# Notice you only see processes running in this shell, and YOU are PID 1!
```

---

## 3. Hands-On Lab 2: Directly Throttling CPU with cgroups v2

### Goal
Bypass Docker entirely and manually restrict a process's CPU usage using the Linux kernel's cgroup v2 hierarchy.

### Steps

```bash
# 1. Start a CPU-intensive background process (like a simple while loop)
cat << 'EOF' > spin.sh
while true; do :; done
EOF
bash spin.sh &
SPIN_PID=$!

# 2. Monitor it using top or htop - it will consume 100% of a core
top -p $SPIN_PID

# 3. Create a new cgroup in the unified hierarchy
sudo mkdir /sys/fs/cgroup/my_throttle_group

# 4. Move the process into the cgroup
echo $SPIN_PID | sudo tee /sys/fs/cgroup/my_throttle_group/cgroup.procs

# 5. Limit the CPU quota to 20% of a single core (20000 microseconds per 100000 microsecond period)
echo "20000 100000" | sudo tee /sys/fs/cgroup/my_throttle_group/cpu.max

# 6. Re-check top/htop. The process is now hard-throttled to 20.0% CPU!
top -p $SPIN_PID

# 7. Clean up
kill $SPIN_PID
sudo rmdir /sys/fs/cgroup/my_throttle_group
```

---

## 4. Hands-On Lab 3: Privilege Escalation (Container Breakout Simulation)

### Goal
Understand why running a container with `--privileged` or mounting the Docker socket is catastrophically dangerous.

**WARNING: Do this on a disposable VM or Sandbox only!**

```bash
# 1. Launch a highly privileged container, mounting the host's root filesystem
docker run --rm -it --privileged --pid=host -v /:/host_root alpine sh

# 2. You are now inside the container. Attempt to use nsenter to break out into the host's PID 1 (systemd)
nsenter -t 1 -m -u -n -i sh

# 3. You are now effectively root on the host machine, bypassing the container boundary entirely.
# Prove it by viewing host-specific files that the container shouldn't see
cat /etc/shadow
```
*Takeaway:* Never use `--privileged` unless building specialized tooling like DinD (Docker-in-Docker) in heavily isolated CI environments.

# Apache Kafka: Architecture, Storage & Commit Log Internals

> **Cluster 03 — Module 01**
> Focus: Distributed commit logs, OS page cache, Zero-Copy data transfer, partitions, ISR, High Watermark, Leader Epochs, and broker internals.

---

## 1. Event Store vs. Traditional Queues: A Paradigm Shift

While traditional message brokers (e.g., RabbitMQ, ActiveMQ) typically implement transient queues where messages are aggressively discarded upon consumer acknowledgment, **Apache Kafka** introduces a fundamentally different model: the **distributed, append-only, immutable commit log**.

In traditional systems, the broker's primary goal is to route messages and keep queues empty. In Kafka, the broker acts as a specialized, high-performance **event store**. Messages are persisted to disk and retained based on time or size limits (e.g., retain for 7 days, or 100GB). This decoupling allows multiple independent consumer groups to read the same data at entirely different paces, and critically, permits historical event replay.

```mermaid
flowchart LR
    subgraph Traditional_Queue["RabbitMQ (Queue Model)"]
        P1["Producer"] --> Q1["Queue (Transient RAM)"]
        Q1 -->|Consume & Delete| C1["Consumer A"]
    end

    subgraph Kafka_Log["Apache Kafka (Distributed Commit Log)"]
        P2["Producer"] -->|Append-Only| T1["Partition 0 (Immutable Disk Log)"]
        T1 -->|Read Offset 0..N| CG1["Analytics Consumer Group"]
        T1 -->|Read Offset 0..N| CG2["Notification Consumer Group"]
        T1 -.->|Replay Any Historical Offset| CG3["Audit Consumer Group"]
    end

    style Traditional_Queue fill:#fef2f2,stroke:#ef4444,stroke-width:1px
    style Kafka_Log fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

---

## 2. The Distributed Commit Log: Hardware Symbiosis

A fundamental question for newcomers is: *"How can Kafka persist everything to spinning HDDs or SSDs and still process gigabytes of data per second?"*

Kafka achieves this by working in extreme harmony with the underlying Linux operating system rather than relying on JVM heap management.

### A. Sequential Disk I/O & OS Page Cache
Kafka’s workload is strictly sequential append-only writes and sequential reads. Modern disks (even 7200 RPM HDDs) can achieve up to 300–600 MB/s for sequential I/O, matching or exceeding random RAM access speeds.
Furthermore, Kafka bypasses application-level caching. Instead, it relies on the Linux **Page Cache**. Data is written directly to OS memory buffers (dirty pages), which the kernel flushes to disk asynchronously. When a consumer reads data, if it's a real-time consumer (tailing the log), the data is served directly from the kernel's Page Cache—without a single disk read or touching the JVM heap!

### B. Linux Zero-Copy (`sendfile()` and Scatter-Gather)
In traditional I/O, serving data over the network requires context switches and redundant memory copies between kernel and user space. Kafka leverages the `sendfile()` system call (alongside NIC scatter-gather capabilities) to execute a **Zero-Copy** transfer.

```mermaid
sequenceDiagram
    autonumber
    Note over Disk,Socket: Traditional Read/Write (4 Context Switches, 4 Data Copies)
    Disk->>OS Page Cache: 1. DMA Copy
    OS Page Cache->>JVM User Space: 2. CPU Copy
    JVM User Space->>Socket Buffer: 3. CPU Copy
    Socket Buffer->>NIC (Network Card): 4. DMA Copy

    Note over Disk,Socket: Kafka Zero-Copy: sendfile() (2 Context Switches, 2 DMA Copies, 0 CPU Copies)
    Disk->>OS Page Cache: 1. DMA Copy
    OS Page Cache->>NIC (Network Card): 2. DMA Copy directly to NIC via sendfile()
```

By eliminating user-space buffering, Kafka eradicates CPU memory copies and cuts context switches in half, freeing the CPU to handle millions of connections and metadata operations.

---

## 3. Log Anatomy: Segments, Indexes, and Epochs

A Kafka **Topic** is a logical concept divided into **Partitions** for horizontal scalability. Physically, a partition is a directory on a broker's filesystem, composed of rolling **Log Segments**.

```
/var/lib/kafka/data/orders.v1-0/
├── 00000000000000000000.log         <-- Raw record batches
├── 00000000000000000000.index       <-- Maps logical Offset to physical Byte Position
├── 00000000000000000000.timeindex   <-- Maps Timestamp to logical Offset
├── 00000000000000104523.log         <-- Active rolling segment (append mode)
├── 00000000000000104523.index
└── leader-epoch-checkpoint          <-- Mitigates data loss during failover
```

```mermaid
flowchart TD
    subgraph Log_Segment["Log Segment Architecture"]
        TimeIndex["00000.timeindex\nTimestamp -> Offset"]
        OffsetIndex["00000.index (Sparse Index)\nOffset 0 -> Byte 0\nOffset 100 -> Byte 4096\nOffset 200 -> Byte 8192"]
        LogFile["00000.log (Data File)\n[Byte 0..4095: RecordBatch 0..99]\n[Byte 4096..8191: RecordBatch 100..199]\n[Byte 8192..N: RecordBatch 200..]"]

        TimeIndex -->|1. Find Offset| OffsetIndex
        OffsetIndex -->|2. Binary Search Byte Range| LogFile
    end

    style TimeIndex fill:#fdf4ff,stroke:#d946ef,stroke-width:1px
    style OffsetIndex fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style LogFile fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

### Sparse Indexing
Kafka uses a **Sparse Index**. It does not map every single offset. Instead, it creates an index entry every `index.interval.bytes` (default 4KB). When a consumer requests offset `142`, Kafka binary searches the `.index` to find the nearest lower offset (e.g., `100` at byte `4096`), jumps to that byte in the `.log` file, and sequentially scans forward. This keeps the index small enough to fit entirely in memory.

---

## 4. Replication, High Watermark, and Leader Epochs

Kafka achieves high availability through replication. Every partition has one **Leader** and zero or more **Followers**.

- **Leader**: Absorbs all writes and reads.
- **Followers**: Continuously issue `Fetch` requests to the leader to mirror the log.

```mermaid
flowchart TD
    subgraph Partition_Replication["Replication State (Topic: orders, Partition: 0)"]
        Leader["Broker 1 (Leader)\nLog: [0, 1, 2, 3, 4, 5, 6, 7] (LEO = 8)\nHigh Watermark (HW) = 5"]
        Follower1["Broker 2 (Follower - In-Sync)\nLog: [0, 1, 2, 3, 4, 5] (LEO = 6)"]
        Follower2["Broker 3 (Follower - Out of Sync)\nLog: [0, 1, 2] (LEO = 3)"]

        Leader -.->|Replication Fetch| Follower1
        Leader -.->|Replication Fetch| Follower2
    end

    style Leader fill:#dcfce7,stroke:#22c55e,stroke-width:2px
    style Follower1 fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style Follower2 fill:#fee2e2,stroke:#ef4444,stroke-width:1px
```

### The Anatomy of Consistency: LEO, ISR, and HW
- **LEO (Log End Offset)**: The offset of the *next* message to be written. The leader and each follower maintain their own LEO.
- **ISR (In-Sync Replicas)**: The dynamic subset of replicas fully caught up with the leader (within `replica.lag.time.max.ms`). If a broker stalls, it is evicted from the ISR.
- **HW (High Watermark)**: The highest offset successfully replicated to **all** nodes currently in the ISR. 

> [!IMPORTANT]
> **Visibility Guarantee**: Consumers can **only** read up to the High Watermark. Messages between the HW and LEO are uncommitted and invisible. This ensures that if the leader crashes, any visible message is guaranteed to exist on the new leader.

### Producer Guarantees
Durability is governed by the producer's `acks` setting combined with the topic's `min.insync.replicas`:
- `acks=0`: Fire and forget.
- `acks=1`: Leader acknowledges once written to its local log.
- `acks=all`: Leader acknowledges only after the HW advances (all ISR members have replicated it). Combined with `min.insync.replicas=2`, this guarantees zero data loss if a single node fails.

### Leader Epochs (Avoiding Data Divergence)
To handle complex network partitions and split-brain scenarios where HW propagation is delayed, Kafka uses the `leader-epoch-checkpoint` file. An epoch is a monotonically increasing counter tracking leadership changes. Replicas use this to accurately truncate divergent logs upon reconnecting, preventing data loss or corruption anomalies that existed in older Kafka versions relying purely on HW.

---

## 5. Log Compaction

Kafka also supports **Log Compaction**, a policy where Kafka retains only the *latest* known value for each key, rather than dropping old data by time. This is heavily used for materializing state (e.g., a table of user balances).
Deleting a key is achieved by writing a **Tombstone** message (a record with the key and a `null` payload), which the compactor eventually recognizes and uses to wipe the key entirely from the log.

---

## References & Pro-Reading
- [KIP-101: Alter Replication Protocol to use Leader Epochs](https://cwiki.apache.org/confluence/display/KAFKA/KIP-101+-+Alter+Replication+Protocol+to+use+Leader+Epochs)
- [Kafka Log Compaction Internals](https://kafka.apache.org/documentation/#compaction)
- [Linux Zero-Copy Architecture Paper](https://www.linuxjournal.com/article/6345)
- [Optimizing Kafka for Cloud Storage (Tiered Storage KIP-405)](https://cwiki.apache.org/confluence/display/KAFKA/KIP-405%3A+Kafka+Tiered+Storage)

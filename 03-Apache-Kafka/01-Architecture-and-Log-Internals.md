# Apache Kafka: Architecture, Storage & Commit Log Internals

> **Cluster 03 — Module 01**  
> Focus: Distributed commit logs, OS page cache, Zero-Copy data transfer, partitions, ISR, High Watermark, and broker internals.

---

## 1. What is Kafka? (Event Store vs Traditional Queues)

Traditional message brokers (e.g. RabbitMQ, ActiveMQ) maintain message queues where messages are discarded once acknowledged by consumers. **Apache Kafka** is a distributed, append-only, immutable **commit log**. Messages are written sequentially to disk and retained according to time or size policies, allowing multiple independent consumer groups to read at their own pace and replay history.

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

## 2. The Distributed Commit Log & Disk Performance Secrets

A common question: *"How can Kafka write to spinning disks or SSDs and still process millions of events per second?"*

Kafka achieves incredible speed through two operating system level mechanisms:

### A. Sequential Disk I/O & OS Page Cache
- Sequential disk access is fast—often rivaling random memory access throughput (up to 600 MB/s on modern drives).
- Kafka avoids allocating giant JVM memory heaps (which trigger painful garbage collection pauses). Instead, it writes directly to the OS **Page Cache** (RAM managed by the Linux kernel). If a consumer is caught up, data is read directly from kernel memory without touching the physical disk!

### B. Linux Zero-Copy (`sendfile()` Syscall)
In traditional network transfers, data is copied 4 times across kernel and user space boundaries:

```mermaid
sequenceDiagram
    autonumber
    Note over Disk,Socket: Traditional Read/Write (4 Context Switches, 4 Data Copies)
    Disk->>OS Page Cache: 1. DMA Copy from disk
    OS Page Cache->>JVM User Space: 2. CPU Copy to application buffer
    JVM User Space->>Socket Buffer: 3. CPU Copy to socket buffer
    Socket Buffer->>NIC (Network Card): 4. DMA Copy to network interface

    Note over Disk,Socket: Kafka Zero-Copy: sendfile() (2 Context Switches, 2 DMA Copies, 0 CPU Copies)
    Disk->>OS Page Cache: 1. DMA Copy to OS Page Cache
    OS Page Cache->>NIC (Network Card): 2. DMA Copy directly via sendfile()
```

By bypassing user space completely, Kafka eliminates CPU copy cycles and reduces context switches by 50%.

---

## 3. Log Anatomy: Segments, Indexes & Offsets

A Kafka **Topic** is divided into one or more **Partitions**. A partition is physically represented as a directory of log segment files on the broker's filesystem:

```
/var/lib/kafka/data/orders.v1-0/
├── 00000000000000000000.log         <-- Actual raw message records
├── 00000000000000000000.index       <-- Maps Logical Offset -> Physical Byte Position
├── 00000000000000000000.timeindex   <-- Maps Timestamp -> Logical Offset
├── 00000000000000104523.log         <-- Active rolling segment
├── 00000000000000104523.index
└── leader-epoch-checkpoint
```

```mermaid
flowchart TD
    subgraph Log_Segment["Inside a Log Segment (.log + .index)"]
        OffsetIndex["00000.index (Sparse Index)\nOffset 0 -> Byte 0\nOffset 100 -> Byte 4096\nOffset 200 -> Byte 8192"]
        LogFile["00000.log (Data File)\n[Byte 0..4095: Records 0..99]\n[Byte 4096..8191: Records 100..199]\n[Byte 8192..N: Records 200..]"]

        OffsetIndex -->|Binary Search to find byte range| LogFile
    end

    style OffsetIndex fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style LogFile fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

Kafka uses a **Sparse Index**: rather than indexing every single message, it indexes every $N$ bytes (default 4KB). To find message offset `142`, it binary-searches the `.index` file to find offset `100` at byte `4096`, then scans sequentially in the `.log` file until offset `142` is located.

---

## 4. Replication: Leader, Followers, ISR, and High Watermark

Every partition has one **Leader** broker and zero or more **Follower** replica brokers.
- **Leader**: Handles all read and write requests from clients.
- **Followers**: Fetch messages sequentially from the Leader to stay synchronized.

```mermaid
flowchart TD
    subgraph Partition_Replication["Replication State (Topic: orders, Partition: 0)"]
        Leader["Broker 1 (Leader)\nLog: [0, 1, 2, 3, 4, 5, 6, 7] (LEO = 8)\nHigh Watermark (HW) = 5"]
        Follower1["Broker 2 (Follower - In ISR)\nLog: [0, 1, 2, 3, 4, 5] (LEO = 6)"]
        Follower2["Broker 3 (Follower - Out of ISR / Slow)\nLog: [0, 1, 2] (LEO = 3)"]

        Leader -.->|Replication Fetch| Follower1
        Leader -.->|Replication Fetch| Follower2
    end

    style Leader fill:#dcfce7,stroke:#22c55e,stroke-width:2px
    style Follower1 fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style Follower2 fill:#fee2e2,stroke:#ef4444,stroke-width:1px
```

### Critical Terms:
- **LEO (Log End Offset)**: The offset of the next record to be written in the log.
- **ISR (In-Sync Replicas)**: The subset of replica brokers that are actively caught up with the leader within `replica.lag.time.max.ms` (default 30 seconds).
- **HW (High Watermark)**: The highest offset that has been replicated to **all** in-sync replicas in the ISR.
  > [!IMPORTANT]
  > **Consumers can ONLY read up to the High Watermark (HW)**. Uncommitted records beyond the HW are invisible to consumers to guarantee consistency during leader failover.

---

## 5. Official References
- [Apache Kafka Documentation: Design Principles](https://kafka.apache.org/documentation/#design)
- [Kafka Log Compaction & Storage Internals](https://kafka.apache.org/documentation/#compaction)
- [Linux Zero-Copy Architecture Paper](https://www.linuxjournal.com/article/6345)

# Kafka: KRaft Consensus, Schema Registry & The Kafka Ecosystem

> **Cluster 03 — Module 04**  
> Focus: KRaft vs ZooKeeper, Schema Registry wire format, schema evolution, log compaction, and Kafka Connect/Streams.

---

## 1. The KRaft Revolution: Retiring Apache ZooKeeper (KIP-500)

Historically, Kafka relied on Apache ZooKeeper to manage cluster metadata, leader elections, and broker discovery. ZooKeeper introduced architectural bottlenecks that are now resolved by **KRaft (Kafka Raft Metadata Mode)**.

```mermaid
flowchart TD
    subgraph Legacy_ZooKeeper["Legacy Architecture (External ZooKeeper Dependency)"]
        ZK["Apache ZooKeeper Quorum\n(External consensus cluster)"]
        KC["Active Controller Broker"]
        B1["Broker 1"]
        B2["Broker 2"]
        
        ZK <-->|Metadata synchronization| KC
        KC -->|Async RPC updates| B1
        KC -->|Async RPC updates| B2
    end

    subgraph Modern_KRaft["Modern KRaft Architecture (Native Raft Consensus)"]
        subgraph KRaft_Quorum["Metadata Quorum (Active Controller + Voters)"]
            C1["Controller Node 1 (Active Controller Leader)"]
            C2["Controller Node 2 (Follower)"]
            C3["Controller Node 3 (Follower)"]
            C1 <-->|Raft Consensus| C2
            C1 <-->|Raft Consensus| C3
        end
        MetaLog["Internal @metadata Topic\n(Event-driven metadata log)"]
        KRaft_Quorum --> MetaLog
        B_K1["Broker 1"] -.->|Consumes metadata log| MetaLog
        B_K2["Broker 2"] -.->|Consumes metadata log| MetaLog
    end

    style Legacy_ZooKeeper fill:#fef2f2,stroke:#ef4444,stroke-width:1px
    style Modern_KRaft fill:#ecfdf5,stroke:#10b981,stroke-width:2px
```

### Why KRaft Outperforms ZooKeeper

| Metric | Legacy ZooKeeper Architecture | Modern KRaft Architecture |
| :--- | :--- | :--- |
| **Max Partitions per Cluster** | $\approx 200,000$ (ZK synchronization bottleneck) | Millions of partitions |
| **Controller Failover Time** | Minutes (controller had to reload full ZK state into memory) | Sub-second (Follower controllers already have metadata preloaded in memory) |
| **Operational Simplicity** | Two separate distributed systems to monitor, secure, and patch | Single unified binary (`kafka-server-start.sh`) |
| **Metadata Consistency** | Vulnerable to desynchronization between ZooKeeper and Controller | Strictly linearized Raft log (`@metadata`) |

---

## 2. Schema Registry & The Avro Wire Format

In microservice environments, producers and consumers evolve independently. If a producer removes or alters a field without notice, consumer services crash with deserialization errors (**poison pills**).

The **Confluent Schema Registry** serves as the central source of truth for message schemas (using Apache Avro, Protobuf, or JSON Schema).

```mermaid
sequenceDiagram
    autonumber
    participant Prod as Order Producer
    participant SR as Schema Registry
    participant Kafka as Kafka Broker
    participant Cons as Notification Consumer

    Note over Prod,SR: Step 1: Producer checks/registers schema
    Prod->>SR: POST /subjects/orders-value/versions (Avro schema)
    SR-->>Prod: Schema ID: 42
    
    Note over Prod,Kafka: Step 2: Producer serializes record with Schema ID
    Prod->>Kafka: Publish Record (Magic Byte 0x00 + ID 42 + Binary Payload)

    Note over Cons,SR: Step 3: Consumer reads record & fetches schema once
    Kafka->>Cons: Consume Record (Header contains Schema ID: 42)
    Cons->>SR: GET /schemas/ids/42 (Cached after first fetch)
    SR-->>Cons: Returns Avro Schema
    Cons->>Cons: Successfully deserializes payload into typed object!
```

### The 5-Byte Wire Format Header
When using Schema Registry serializers, the message payload is formatted as:
```
[Byte 0: Magic Byte (0x00)] [Bytes 1-4: 32-bit big-endian Schema ID] [Bytes 5..N: Raw Avro Binary]
```

### Schema Evolution Rules
- **BACKWARD (Default)**: Consumers using the new schema can read records written by the old schema (e.g. adding an optional field with a default value).
- **FORWARD**: Consumers using the old schema can read records written by the new schema (e.g. removing an optional field).
- **FULL**: Backward and Forward compatible simultaneously.

---

## 3. Log Retention vs Log Compaction

Kafka partitions support two distinct cleanup policies:

### A. Delete Policy (`cleanup.policy=delete`)
Records are purged once they exceed a time threshold (`retention.ms`, default 7 days) or a size threshold (`retention.bytes`).

### B. Compact Policy (`cleanup.policy=compact`)
Kafka retains the **latest known value for every message key**. Older values sharing the same key are deleted during background cleaner thread runs.

```mermaid
flowchart LR
    subgraph Dirty_Log["Uncompacted Log Segment"]
        M1["Key: K1, Val: A (Offset 0)"]
        M2["Key: K2, Val: B (Offset 1)"]
        M3["Key: K1, Val: C (Offset 2)"]
        M4["Key: K3, Val: D (Offset 3)"]
        M5["Key: K2, Val: E (Offset 4)"]
        M1 --> M2 --> M3 --> M4 --> M5
    end

    subgraph Cleaned_Log["Log After Background Compaction"]
        C1["Key: K1, Val: C (Offset 2)"]
        C2["Key: K3, Val: D (Offset 3)"]
        C3["Key: K2, Val: E (Offset 4)"]
        C1 --> C2 --> C3
    end

    Dirty_Log -.->|Background Cleaner Thread| Cleaned_Log

    style Dirty_Log fill:#fee2e2,stroke:#ef4444,stroke-width:1px
    style Cleaned_Log fill:#dcfce7,stroke:#22c55e,stroke-width:2px
```

- **Tombstone Record**: To completely delete a key from a compacted topic, the producer sends a record with the key and a **`null` value**. The cleaner eventually purges the key entirely.

---

## 4. Kafka Ecosystem: Connect & Streams

```mermaid
flowchart LR
    DB[(PostgreSQL)] -->|CDC via Debezium| KC_In["Kafka Connect (Source)"]
    KC_In --> K1["Raw Topic: db.customers"]
    K1 --> KS["Kafka Streams / ksqlDB\n(Enrichment, Windowing, Aggregation)"]
    KS --> K2["Enriched Topic: customers.vip"]
    K2 --> KC_Out["Kafka Connect (Sink)"]
    KC_Out --> Elastic[(Elasticsearch / S3 Data Lake)]

    style KS fill:#fef3c7,stroke:#f59e0b,stroke-width:2px
    style KC_In fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
    style KC_Out fill:#eff6ff,stroke:#3b82f6,stroke-width:1px
```

- **Kafka Connect**: Ready-to-run declarative framework for streaming data between datastores (PostgreSQL, MySQL, S3, Snowflake) and Kafka without writing boilerplate integration code.
- **Kafka Streams**: A lightweight client library for Java/Scala that enables real-time stream processing, event-time windowing, session aggregations, and stream-table joins (`KStream`, `KTable`).

---

## 5. Official References
- [KIP-500: Replace ZooKeeper with a Self-Managed Metadata Quorum](https://cwiki.apache.org/confluence/display/KAFKA/KIP-500%3A+Replace+ZooKeeper+with+a+Self-Managed+Metadata+Quorum)
- [Confluent Schema Registry Guide](https://docs.confluent.io/platform/current/schema-registry/index.html)
- [Kafka Connect Architecture](https://kafka.apache.org/documentation/#connect)
- [Kafka Streams Developer Guide](https://kafka.apache.org/documentation/streams/)

# Kafka: CLI Cheat Sheet & Practical Hands-On Labs

> **Cluster 03 — Module 06**  
> Focus: Command-line reference, running Kafka in KRaft mode via Docker, and key-based partitioning labs.

---

## 1. Production Kafka CLI Cheat Sheet

### Topic Management
```bash
# Create a topic with 3 partitions and replication factor 1
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic orders.v1 \
  --partitions 3 \
  --replication-factor 1

# List all active topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Inspect partition count, leader IDs, replicas, and ISR
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders.v1

# Increase topic partitions from 3 to 6
kafka-topics.sh --bootstrap-server localhost:9092 --alter \
  --topic orders.v1 \
  --partitions 6
```

### Producer CLI Operations
```bash
# Produce simple unkeyed messages
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.v1

# Produce messages with KEY and VALUE separated by colon (':')
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.v1 \
  --property "parse.key=true" \
  --property "key.separator=:"

# Example input lines:
# ORD-100:{"item":"Laptop","price":1200}
# ORD-101:{"item":"Keyboard","price":80}
```

### Consumer CLI Operations
```bash
# Consume messages from the beginning printing Key, Value, Partition, and Offset
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.v1 \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true \
  --property print.offset=true \
  --property print.timestamp=true

# Join an explicit consumer group
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.v1 \
  --group order-processors
```

### Consumer Group Inspection & Lag
```bash
# Inspect lag, current offset, and client IDs for a consumer group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group order-processors

# Reset consumer group offset to earliest (must be inactive/stopped)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-processors \
  --reset-offsets --to-earliest \
  --execute --topic orders.v1

# Shift offsets by -5 (replay last 5 messages)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-processors \
  --reset-offsets --shift-by -5 \
  --execute --topic orders.v1
```

---

## 2. Hands-On Lab 1: Spin Up Kafka in KRaft Mode via Docker

Save the following as `docker-compose-kafka.yml`:

```yaml
version: "3.8"
services:
  kafka:
    image: bitnami/kafka:3.7.0
    container_name: kafka-kraft-lab
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      - KAFKA_CFG_NODE_ID=0
      - KAFKA_CFG_PROCESS_ROLES=controller,broker
      - KAFKA_CFG_CONTROLLERS=0@kafka:9093
      - KAFKA_CFG_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      - KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092
      - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      - KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_CFG_INTER_BROKER_LISTENER_NAME=PLAINTEXT
```

Run the container:
```bash
docker compose -f docker-compose-kafka.yml up -d
docker logs -f kafka-kraft-lab
```

---

## 3. Hands-On Lab 2: Verifying Partition Hashing & Determinism

### Step 1: Create a 3-Partition Topic
```bash
docker exec -it kafka-kraft-lab /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic test.partitioning --partitions 3 --replication-factor 1
```

### Step 2: Send Keyed Messages
```bash
docker exec -it kafka-kraft-lab /opt/bitnami/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic test.partitioning \
  --property "parse.key=true" \
  --property "key.separator=:"
```
Type these three lines:
```
USER_A:Message 1
USER_B:Message 2
USER_A:Message 3
```

### Step 3: Consume and Verify Partition Pinning
```bash
docker exec -it kafka-kraft-lab /opt/bitnami/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic test.partitioning \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true \
  --property print.offset=true
```
*Notice:* Both `USER_A:Message 1` and `USER_A:Message 3` landed in the **exact same partition**, proving strict key hash determinism!

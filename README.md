# motion-event-producer

![Project](docs/images/project.jpg)

### JavaFX + dyn4j simulation that now emits runtime events for future streaming integration.

## Full Article:
### ⭐ [How I Built a Java Physics Simulation That Publishes Real-Time Kafka Events](https://medium.com/itnext/how-i-built-a-java-physics-simulation-that-publishes-real-time-kafka-events-2ec3f9d71156)
_A practical guide to producing motion and collision events from a Java physics simulation into Kafka._

## Running locally with Kafka + AKHQ

AKHQ is a lightweight web UI for Kafka. You can use it to inspect topics, browse messages, check partitions/offsets, and monitor consumer groups while the simulation is running.

### Prerequisites

- Docker running locally
- Java 17
- Maven 3.9+
- Ports available: `9092` (Kafka) and `8081` (AKHQ)

### Get the project

```bash
git clone git@github.com:wagnerjfr/motion-event-producer.git
cd motion-event-producer
```

### Path A (recommended): App on host + Kafka/AKHQ in Docker

#### 1) Create a Docker network (once)

```bash
docker network create kafka-net
```

#### 2) Start Kafka (dual listeners)

```bash
docker run -d --name kafka --network kafka-net -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT_HOST://:9092,PLAINTEXT_DOCKER://:29092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT_HOST://localhost:9092,PLAINTEXT_DOCKER://kafka:29092 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT_DOCKER \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,PLAINTEXT_DOCKER:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  apache/kafka:3.8.0
```

#### 3) Start AKHQ

```bash
docker run -d --name akhq --network kafka-net -p 8081:8080 \
  -e AKHQ_CONFIGURATION='akhq:
  connections:
    local:
      properties:
        bootstrap.servers: "kafka:29092"' \
  tchiotludo/akhq
```

Open AKHQ at:

- `http://localhost:8081`

#### 4) Create topics

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --create --topic motion-position --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --create --topic motion-collision --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

#### 5) Compile and run the producer app

```bash
mvn -DskipTests compile
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 mvn javafx:run
```

![Simulation UI](docs/images/simulation-ui.png)

#### 6) Verify events

- In AKHQ, open `motion-position` and `motion-collision` topics and inspect incoming records.
![AKHQ Topics](docs/images/akhq-topics.png)

- Optional CLI verification:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --topic motion-position --bootstrap-server localhost:9092 --from-beginning
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --topic motion-collision --bootstrap-server localhost:9092 --from-beginning
```

### Path B: Logs-only mode (no Kafka)

Use this mode if Kafka is unavailable or you just want to test simulation + event generation quickly:

```bash
APP_KAFKA_ENABLED=false mvn javafx:run
```

### Path C: Custom topics / broker

Override defaults with env vars:

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
KAFKA_TOPIC_POSITION=my-position-topic \
KAFKA_TOPIC_COLLISION=my-collision-topic \
KAFKA_CLIENT_ID=motion-event-producer-dev \
mvn javafx:run
```

### Bootstrap address quick reference

- Producer app on host machine: `localhost:9092`
- Clients/containers on `kafka-net`: `kafka:29092`

### Troubleshooting

- **Port conflict**: if `9092` or `8081` is busy, stop conflicting services or remap ports.
- **No messages in AKHQ**: confirm app is running with Kafka enabled and using `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.
- **Cannot connect from container**: inside Docker network, use `kafka:29092` (not `localhost:9092`).
- **Topic errors**: create topics manually (commands above) before running consumers.

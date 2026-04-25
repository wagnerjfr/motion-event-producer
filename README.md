# motion-event-producer

JavaFX + dyn4j simulation that now emits runtime events for future streaming integration.

## Architecture

- **JavaFX** handles visualization and simulation loop.
- **Spring Boot** now boots alongside JavaFX and provides dependency injection/configuration.
- `MotionEventProducerApplication` defines infrastructure beans (currently `EventEmitter`).
- `CircleCollisionsApp` starts Spring context on app startup and closes it on app shutdown.

This prepares the project for future Kafka integration while keeping current behavior unchanged.

## Current emitted events

- **PositionEvent** (throttled, every ~100ms):
  - `ballId`, `timestampMs`, `x`, `y`, `vx`, `vy`
- **CollisionEvent** (on new circle-circle contact):
  - `ballAId`, `ballBId`, `timestampMs`, `x`, `y`, `relativeSpeed`

Events are emitted through an abstraction:

- `EventEmitter` interface
- `LoggingEventEmitter` implementation (prints JSON-like lines to stdout)

This keeps the app **Kafka-ready**: next phase can add a `KafkaEventEmitter` implementing the same interface.

## Kafka integration

Kafka is now integrated through `KafkaEventEmitter`.

- Position events are published to topic: `motion-position`
- Collision events are published to topic: `motion-collision`
- If Kafka publish fails, the app falls back to `LoggingEventEmitter`.

### Config (env overrides)

- `APP_KAFKA_ENABLED` (default `true`)
- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`)
- `KAFKA_TOPIC_POSITION` (default `motion-position`)
- `KAFKA_TOPIC_COLLISION` (default `motion-collision`)
- `KAFKA_CLIENT_ID` (default `motion-event-producer`)

If `APP_KAFKA_ENABLED=false`, the app bypasses Kafka and emits only logs (`LoggingEventEmitter`).

### Docker Kafka (recommended: shared network with AKHQ)

Create a dedicated Docker network (once):

```bash
docker network create kafka-net
```

Run Kafka on that network (dual listeners: one for host apps, one for Docker clients):

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

Run **AKHQ** on the same network:

```bash
docker run -d --name akhq --network kafka-net -p 8081:8080 \
  -e AKHQ_CONFIGURATION='akhq:
  connections:
    local:
      properties:
        bootstrap.servers: "kafka:29092"' \
  tchiotludo/akhq
```

Open UI:

- `http://localhost:8081`

Create topics:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --create --topic motion-position --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh --create --topic motion-collision --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

Consume to verify:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --topic motion-position --bootstrap-server localhost:9092 --from-beginning
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --topic motion-collision --bootstrap-server localhost:9092 --from-beginning
```

> If your producer app runs on your host machine (not inside Docker), use `localhost:9092`.
> If a client runs inside Docker on `kafka-net`, use `kafka:29092`.

## Run

### 1) Compile

```bash
mvn -DskipTests compile
```

### 2) Run with Kafka (host app)

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 mvn javafx:run
```

### 3) Run logs-only (Kafka disabled)

```bash
APP_KAFKA_ENABLED=false mvn javafx:run
```

### 4) Bootstrap quick reference

- If your JavaFX app runs on the host, use `localhost:9092`
- If a client runs inside Docker on `kafka-net`, use `kafka:29092`

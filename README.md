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

## Run

```bash
mvn -DskipTests compile
mvn javafx:run
```

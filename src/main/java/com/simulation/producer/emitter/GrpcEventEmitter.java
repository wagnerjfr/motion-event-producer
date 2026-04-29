package com.simulation.producer.emitter;

import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;
import com.simulation.producer.grpc.CollisionEventMessage;
import com.simulation.producer.grpc.EventEnvelope;
import com.simulation.producer.grpc.MotionEventIngestServiceGrpc;
import com.simulation.producer.grpc.PositionEventMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class GrpcEventEmitter implements EventEmitter {
    private final EventEmitter fallbackEmitter;
    private final ManagedChannel channel;
    private final MotionEventIngestServiceGrpc.MotionEventIngestServiceBlockingStub blockingStub;
    private final long deadlineMs;

    public GrpcEventEmitter(
            String target,
            long deadlineMs,
            EventEmitter fallbackEmitter
    ) {
        this.fallbackEmitter = fallbackEmitter;
        this.deadlineMs = Math.max(100, deadlineMs);
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        this.blockingStub = MotionEventIngestServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void emitPosition(PositionEvent event) {
        try {
            EventEnvelope envelope = EventEnvelope.newBuilder()
                    .setPosition(PositionEventMessage.newBuilder()
                            .setSessionId(event.sessionId())
                            .setBallId(event.ballId())
                            .setTimestampMs(event.timestampMs())
                            .setX(event.x())
                            .setY(event.y())
                            .setVx(event.vx())
                            .setVy(event.vy())
                            .build())
                    .build();
            blockingStub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .publish(envelope);
        } catch (RuntimeException ex) {
            System.err.println("gRPC publish failed for position event, using fallback logger: " + ex.getMessage());
            fallbackEmitter.emitPosition(event);
        }
    }

    @Override
    public void emitCollision(CollisionEvent event) {
        try {
            EventEnvelope envelope = EventEnvelope.newBuilder()
                    .setCollision(CollisionEventMessage.newBuilder()
                            .setSessionId(event.sessionId())
                            .setBallAId(event.ballAId())
                            .setBallBId(event.ballBId())
                            .setTimestampMs(event.timestampMs())
                            .setX(event.x())
                            .setY(event.y())
                            .setRelativeSpeed(event.relativeSpeed())
                            .build())
                    .build();
            blockingStub
                    .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .publish(envelope);
        } catch (RuntimeException ex) {
            System.err.println("gRPC publish failed for collision event, using fallback logger: " + ex.getMessage());
            fallbackEmitter.emitCollision(event);
        }
    }

    public void shutdown() {
        channel.shutdown();
    }
}
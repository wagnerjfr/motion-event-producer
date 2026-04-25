package com.simulation.producer.emitter;

import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;

public class LoggingEventEmitter implements EventEmitter {
    @Override
    public void emitPosition(PositionEvent event) {
        System.out.printf(
                "{\"type\":\"position\",\"ballId\":\"%s\",\"timestampMs\":%d,\"x\":%.4f,\"y\":%.4f,\"vx\":%.4f,\"vy\":%.4f}%n",
                event.ballId(),
                event.timestampMs(),
                event.x(),
                event.y(),
                event.vx(),
                event.vy()
        );
    }

    @Override
    public void emitCollision(CollisionEvent event) {
        System.out.printf(
                "{\"type\":\"collision\",\"ballAId\":\"%s\",\"ballBId\":\"%s\",\"timestampMs\":%d,\"x\":%.4f,\"y\":%.4f,\"relativeSpeed\":%.4f}%n",
                event.ballAId(),
                event.ballBId(),
                event.timestampMs(),
                event.x(),
                event.y(),
                event.relativeSpeed()
        );
    }
}

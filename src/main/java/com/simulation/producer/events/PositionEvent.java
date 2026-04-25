package com.simulation.producer.events;

public record PositionEvent(
        String ballId,
        long timestampMs,
        double x,
        double y,
        double vx,
        double vy
) {
}

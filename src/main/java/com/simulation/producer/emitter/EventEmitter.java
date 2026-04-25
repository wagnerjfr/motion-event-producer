package com.simulation.producer.emitter;

import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;

public interface EventEmitter {
    void emitPosition(PositionEvent event);

    void emitCollision(CollisionEvent event);
}

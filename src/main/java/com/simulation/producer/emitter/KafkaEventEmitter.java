package com.simulation.producer.emitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaEventEmitter implements EventEmitter {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String positionTopic;
    private final String collisionTopic;
    private final EventEmitter fallbackEmitter;

    public KafkaEventEmitter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String positionTopic,
            String collisionTopic,
            EventEmitter fallbackEmitter
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.positionTopic = positionTopic;
        this.collisionTopic = collisionTopic;
        this.fallbackEmitter = fallbackEmitter;
    }

    @Override
    public void emitPosition(PositionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(positionTopic, event.ballId(), payload);
        } catch (JsonProcessingException | RuntimeException ex) {
            System.err.println("Kafka publish failed for position event, using fallback logger: " + ex.getMessage());
            fallbackEmitter.emitPosition(event);
        }
    }

    @Override
    public void emitCollision(CollisionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(collisionTopic, event.ballAId(), payload);
        } catch (JsonProcessingException | RuntimeException ex) {
            System.err.println("Kafka publish failed for collision event, using fallback logger: " + ex.getMessage());
            fallbackEmitter.emitCollision(event);
        }
    }
}

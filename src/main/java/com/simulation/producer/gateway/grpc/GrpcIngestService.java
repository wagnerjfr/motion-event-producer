package com.simulation.producer.gateway.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.producer.grpc.CollisionEventMessage;
import com.simulation.producer.grpc.EventEnvelope;
import com.simulation.producer.grpc.MotionEventIngestServiceGrpc;
import com.simulation.producer.grpc.PositionEventMessage;
import com.simulation.producer.grpc.PublishResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GrpcIngestService extends MotionEventIngestServiceGrpc.MotionEventIngestServiceImplBase {
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String positionTopic;
    private final String collisionTopic;

    public GrpcIngestService(
            ObjectMapper objectMapper,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.position}") String positionTopic,
            @Value("${app.kafka.topics.collision}") String collisionTopic
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.positionTopic = positionTopic;
        this.collisionTopic = collisionTopic;
    }

    @Override
    public void publish(EventEnvelope request, StreamObserver<PublishResponse> responseObserver) {
        try {
            switch (request.getEventCase()) {
                case POSITION -> publishPosition(request.getPosition());
                case COLLISION -> publishCollision(request.getCollision());
                case EVENT_NOT_SET -> {
                    responseObserver.onNext(PublishResponse.newBuilder()
                            .setAccepted(false)
                            .setMessage("event not set")
                            .build());
                    responseObserver.onCompleted();
                    return;
                }
            }

            responseObserver.onNext(PublishResponse.newBuilder()
                    .setAccepted(true)
                    .setMessage("ok")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onNext(PublishResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage("publish failure")
                    .build());
            responseObserver.onCompleted();
            System.err.println("gRPC ingest error: " + ex.getMessage());
        }
    }

    private void publishPosition(PositionEventMessage event) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ballId", event.getBallId(),
                "timestampMs", event.getTimestampMs(),
                "x", event.getX(),
                "y", event.getY(),
                "vx", event.getVx(),
                "vy", event.getVy()
        ));
        kafkaTemplate.send(positionTopic, event.getBallId(), payload);
    }

    private void publishCollision(CollisionEventMessage event) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "ballAId", event.getBallAId(),
                "ballBId", event.getBallBId(),
                "timestampMs", event.getTimestampMs(),
                "x", event.getX(),
                "y", event.getY(),
                "relativeSpeed", event.getRelativeSpeed()
        ));
        kafkaTemplate.send(collisionTopic, event.getBallAId(), payload);
    }
}
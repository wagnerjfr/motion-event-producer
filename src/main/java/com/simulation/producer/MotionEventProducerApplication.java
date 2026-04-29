package com.simulation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.producer.emitter.EventEmitter;
import com.simulation.producer.emitter.GrpcEventEmitter;
import com.simulation.producer.emitter.KafkaEventEmitter;
import com.simulation.producer.emitter.LoggingEventEmitter;
import com.simulation.producer.emitter.WebSocketEventEmitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootApplication
public class MotionEventProducerApplication {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public LoggingEventEmitter loggingEventEmitter() {
        return new LoggingEventEmitter();
    }

    @Bean
    public KafkaEventEmitter kafkaEventEmitter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.position}") String positionTopic,
            @Value("${app.kafka.topics.collision}") String collisionTopic,
            LoggingEventEmitter loggingEventEmitter
    ) {
        return new KafkaEventEmitter(
                kafkaTemplate,
                objectMapper,
                positionTopic,
                collisionTopic,
                loggingEventEmitter
        );
    }

    @Bean
    public WebSocketEventEmitter websocketEventEmitter(
            ObjectMapper objectMapper,
            @Value("${app.websocket.url}") String websocketUrl,
            @Value("${app.websocket.reconnect-ms}") long reconnectMs,
            LoggingEventEmitter loggingEventEmitter
    ) {
        return new WebSocketEventEmitter(
                objectMapper,
                websocketUrl,
                reconnectMs,
                loggingEventEmitter
        );
    }

    @Bean(destroyMethod = "shutdown")
    public GrpcEventEmitter grpcEventEmitter(
            @Value("${app.grpc.target}") String grpcTarget,
            @Value("${app.grpc.deadline-ms}") long deadlineMs,
            LoggingEventEmitter loggingEventEmitter
    ) {
        return new GrpcEventEmitter(
                grpcTarget,
                deadlineMs,
                loggingEventEmitter
        );
    }

    @Bean(name = "eventEmitter")
    public EventEmitter eventEmitter(
            @Value("${app.transport.mode:kafka}") String transportMode,
            KafkaEventEmitter kafkaEventEmitter,
            WebSocketEventEmitter websocketEventEmitter,
            GrpcEventEmitter grpcEventEmitter,
            LoggingEventEmitter loggingEventEmitter
    ) {
        return switch (transportMode.toLowerCase()) {
            case "websocket", "ws" -> websocketEventEmitter;
            case "grpc" -> grpcEventEmitter;
            case "logs", "log", "logging" -> loggingEventEmitter;
            case "kafka" -> kafkaEventEmitter;
            default -> {
                System.err.println("Unknown app.transport.mode='" + transportMode + "', falling back to logs mode");
                yield loggingEventEmitter;
            }
        };
    }
}

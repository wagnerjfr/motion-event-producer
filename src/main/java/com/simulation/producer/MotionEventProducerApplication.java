package com.simulation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.producer.emitter.EventEmitter;
import com.simulation.producer.emitter.KafkaEventEmitter;
import com.simulation.producer.emitter.LoggingEventEmitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
    @Primary
    public EventEmitter kafkaEventEmitter(
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

    @Bean(name = "eventEmitter")
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
    public EventEmitter loggingOnlyEventEmitter(LoggingEventEmitter loggingEventEmitter) {
        return loggingEventEmitter;
    }

    @Bean(name = "eventEmitter")
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public EventEmitter primaryEventEmitterAlias(EventEmitter kafkaEventEmitter) {
        return kafkaEventEmitter;
    }
}

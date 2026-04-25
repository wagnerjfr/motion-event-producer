package com.simulation.producer;

import com.simulation.producer.emitter.EventEmitter;
import com.simulation.producer.emitter.LoggingEventEmitter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MotionEventProducerApplication {

    @Bean
    public EventEmitter eventEmitter() {
        return new LoggingEventEmitter();
    }
}

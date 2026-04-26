package com.simulation.producer.gateway.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketIngestConfig implements WebSocketConfigurer {
    private final EventIngestWebSocketHandler handler;
    private final String websocketPath;

    public WebSocketIngestConfig(
            EventIngestWebSocketHandler handler,
            @Value("${app.gateway.websocket.path:/ws/events}") String websocketPath
    ) {
        this.handler = handler;
        this.websocketPath = websocketPath;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, websocketPath).setAllowedOrigins("*");
    }
}
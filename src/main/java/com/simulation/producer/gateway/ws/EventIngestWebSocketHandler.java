package com.simulation.producer.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class EventIngestWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String positionTopic;
    private final String collisionTopic;

    public EventIngestWebSocketHandler(
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
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WS client connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            JsonNode payloadNode = root.path("payload");

            if (type.isBlank() || payloadNode.isMissingNode()) {
                session.sendMessage(new TextMessage("{\"status\":\"error\",\"reason\":\"invalid envelope\"}"));
                return;
            }

            String payload = objectMapper.writeValueAsString(payloadNode);

            switch (type) {
                case "position" -> {
                    String key = payloadNode.path("ballId").asText("unknown");
                    kafkaTemplate.send(positionTopic, key, payload);
                }
                case "collision" -> {
                    String key = payloadNode.path("ballAId").asText("unknown");
                    kafkaTemplate.send(collisionTopic, key, payload);
                }
                default -> {
                    session.sendMessage(new TextMessage("{\"status\":\"error\",\"reason\":\"unknown type\"}"));
                    return;
                }
            }

            session.sendMessage(new TextMessage("{\"status\":\"ok\"}"));
        } catch (Exception ex) {
            try {
                session.sendMessage(new TextMessage("{\"status\":\"error\",\"reason\":\"parse/publish failure\"}"));
            } catch (Exception ignored) {
                // ignore secondary send error
            }
            System.err.println("WS ingest error: " + ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WS client disconnected: " + session.getId() + " status=" + status);
    }
}
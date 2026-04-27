package com.simulation.producer.emitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.producer.events.CollisionEvent;
import com.simulation.producer.events.PositionEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class WebSocketEventEmitter implements EventEmitter {
    private final ObjectMapper objectMapper;
    private final EventEmitter fallbackEmitter;
    private final HttpClient httpClient;
    private final URI websocketUri;
    private final long reconnectMs;

    private volatile WebSocket webSocket;
    private volatile long lastConnectAttemptMs = 0;

    public WebSocketEventEmitter(
            ObjectMapper objectMapper,
            String websocketUrl,
            long reconnectMs,
            EventEmitter fallbackEmitter
    ) {
        this.objectMapper = objectMapper;
        this.fallbackEmitter = fallbackEmitter;
        this.websocketUri = URI.create(websocketUrl);
        this.reconnectMs = Math.max(250, reconnectMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void emitPosition(PositionEvent event) {
        if (!sendEnvelope("position", event)) {
            fallbackEmitter.emitPosition(event);
        }
    }

    @Override
    public void emitCollision(CollisionEvent event) {
        if (!sendEnvelope("collision", event)) {
            fallbackEmitter.emitCollision(event);
        }
    }

    private boolean sendEnvelope(String type, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "payload", payload
            ));

            WebSocket socket = ensureConnected();
            if (socket == null) {
                return false;
            }

            socket.sendText(message, true).join();
            return true;
        } catch (JsonProcessingException ex) {
            System.err.println("Failed to serialize WebSocket event, using fallback logger: " + ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            System.err.println("Failed to send event via WebSocket, using fallback logger: " + ex.getMessage());
            webSocket = null;
            return false;
        }
    }

    private synchronized WebSocket ensureConnected() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            return webSocket;
        }

        long now = System.currentTimeMillis();
        if (now - lastConnectAttemptMs < reconnectMs) {
            return null;
        }
        lastConnectAttemptMs = now;

        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(websocketUri, new SimpleListener())
                    .join();
            return webSocket;
        } catch (RuntimeException ex) {
            System.err.println("WebSocket connect failed, using fallback logger until reconnect: " + ex.getMessage());
            webSocket = null;
            return null;
        }
    }

    private static class SimpleListener implements WebSocket.Listener {
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}
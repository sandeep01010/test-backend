package com.examplatform.testengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for topics
        // In production: use Redis pub/sub as broker (for multi-pod WebSocket support)
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/exam")
                .setAllowedOriginPatterns("*")  // restrict in production via Cloudflare
                .withSockJS()
                    .setHeartbeatTime(25000)
                    .setDisconnectDelay(5000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
            .setMessageSizeLimit(128 * 1024)     // 128 KB max message
            .setSendBufferSizeLimit(512 * 1024)  // 512 KB send buffer
            .setSendTimeLimit(15000);             // 15s timeout
    }
}

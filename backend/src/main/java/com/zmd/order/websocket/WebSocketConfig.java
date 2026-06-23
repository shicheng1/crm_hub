package com.zmd.order.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置（STOMP 协议）
 *
 * 使用 STOMP over WebSocket，前端通过 SockJS 连接
 * 客户端订阅 /user/topic/notifications 接收个人通知
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，客户端订阅以 /topic 开发的目的地
        config.enableSimpleBroker("/topic");
        // 客户端发送消息的目的地前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户目的地前缀（点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，允许 SockJS 降级
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}

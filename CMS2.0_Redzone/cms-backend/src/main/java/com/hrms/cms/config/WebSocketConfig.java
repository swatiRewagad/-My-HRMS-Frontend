package com.hrms.cms.config;

import com.hrms.cms.security.StompIdentityChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * STOMP-over-websocket broker for the notification bell.
 *
 * <p>The inbound channel carries {@link StompIdentityChannelInterceptor}, which is what makes the
 * user destinations actually work: without a Principal on the session Spring cannot expand
 * {@code /user/queue/notifications}, so every {@code convertAndSendToUser} call was silently dropped
 * and the bell only updated on a page load. See that class for how the principal is derived.
 *
 * <p>Origins are read from the same {@code cms.cors.allowed-origins} property the HTTP chains use
 * rather than left as {@code *}. A websocket now carries an identity, so allowing any origin to open
 * one would let a hostile page ride the browser's session.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompIdentityChannelInterceptor stompIdentityChannelInterceptor;

    @Value("${cms.cors.allowed-origins:http://localhost:4200,http://localhost:4201,http://localhost:4202,http://localhost:4300}")
    private String allowedOrigins;

    public WebSocketConfig(StompIdentityChannelInterceptor stompIdentityChannelInterceptor) {
        this.stompIdentityChannelInterceptor = stompIdentityChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);

        // Registered twice on purpose. The SockJS-only registration served /ws/notifications/**
        // (info, xhr, the websocket sub-path) but nothing at /ws/notifications itself, so a plain
        // STOMP client could not connect at all. The bare registration below lets @stomp/stompjs
        // talk native websocket with no sockjs-client shim, while the SockJS handlers stay in place
        // for environments where a proxy will not upgrade the connection.
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns(origins);

        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompIdentityChannelInterceptor);
    }
}

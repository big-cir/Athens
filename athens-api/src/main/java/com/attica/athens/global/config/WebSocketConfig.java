package com.attica.athens.global.config;

import com.attica.athens.global.decorator.CustomWebSocketHandlerDecorator;
import com.attica.athens.global.handler.HeaderInfo;
import com.attica.athens.global.handler.MessageHandler.MessageProcessor;
import com.attica.athens.global.handler.MessageHandler.MessageProcessorFactory;
import com.attica.athens.global.handler.StompErrorHandler;
import com.attica.athens.global.interceptor.JwtChannelInterceptor;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws";
    private static final String TOPIC = "/topic";
    private static final String QUEUE = "/queue";
    private static final String PUBLISH = "/app";
    public static final int POOL_SIZE = 10;
    public static final int TIME_LIMIT = 15 * 1000;
    public static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;
    public static final int MESSAGE_SIZE_LIMIT = 128 * 1024;
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final StompErrorHandler stompErrorHandler;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(allowedOrigins);
        registry.setErrorHandler(stompErrorHandler);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(POOL_SIZE);
        taskScheduler.initialize();
        return taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(PUBLISH);
        registry.enableStompBrokerRelay(TOPIC, "/exchange")
                .setTaskScheduler(taskScheduler())
                .setRelayHost("localhost")
                .setVirtualHost("/")
                .setRelayPort(61613)
                .setSystemLogin("guest")
                .setSystemPasscode("guest")
                .setClientLogin("guest")
                .setClientPasscode("guest");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor, new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Principal authentication = accessor.getUser();
                    String authorization = accessor.getFirstNativeHeader("Authorization").substring(BEARER_PREFIX.length());
                    String agoraId = accessor.getFirstNativeHeader("AgoraId");
                    accessor.setUser(new HeaderInfo((Authentication) authentication, authorization, Long.parseLong(agoraId)));
                }
                return message;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(TIME_LIMIT)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT);
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT);
        registration.addDecoratorFactory(this::customDecorator);
    }

    @Bean
    public WebSocketHandlerDecorator customDecorator(
            @Qualifier("subProtocolWebSocketHandler") WebSocketHandler webSocketHandler) {
        return new CustomWebSocketHandlerDecorator(webSocketHandler);
    }
}

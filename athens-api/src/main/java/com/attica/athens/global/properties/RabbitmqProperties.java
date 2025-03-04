package com.attica.athens.global.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class RabbitmqProperties {

    private final String host;
    private final String username;
    private final int port;
    private final String password;
}

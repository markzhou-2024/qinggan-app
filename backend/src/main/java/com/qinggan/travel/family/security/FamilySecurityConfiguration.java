package com.qinggan.travel.family.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FamilySecurityProperties.class)
public class FamilySecurityConfiguration {

    @Bean
    TokenHashingService tokenHashingService() {
        return new TokenHashingService();
    }

    @Bean
    DeviceTokenService deviceTokenService(FamilySecurityProperties properties) {
        return new DeviceTokenService(properties.deviceTokenSigningSecret());
    }
}

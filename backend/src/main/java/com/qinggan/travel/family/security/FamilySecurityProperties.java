package com.qinggan.travel.family.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qinggan.family")
public record FamilySecurityProperties(String joinToken, String deviceTokenSigningSecret) {

    public FamilySecurityProperties {
        joinToken = joinToken == null ? "" : joinToken;
        deviceTokenSigningSecret = deviceTokenSigningSecret == null ? "" : deviceTokenSigningSecret;
    }
}

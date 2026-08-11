package com.qinggan.travel.family.security;

import com.qinggan.travel.family.application.FamilyBindingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class FamilyJoinTokenVerifier {

    private final byte[] expectedToken;

    public FamilyJoinTokenVerifier(FamilySecurityProperties properties) {
        this.expectedToken = properties.joinToken().getBytes(StandardCharsets.UTF_8);
    }

    public void requireValid(String authorizationHeader) {
        byte[] supplied = bearerToken(authorizationHeader).getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, supplied)) {
            throw invalidJoinToken();
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw invalidJoinToken();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty() || token.contains(" ")) {
            throw invalidJoinToken();
        }
        return token;
    }

    private FamilyBindingException invalidJoinToken() {
        return new FamilyBindingException(
            HttpStatus.UNAUTHORIZED, "INVALID_JOIN_TOKEN", "Family Join Token is invalid");
    }
}

package com.qinggan.travel.family.security;

import com.qinggan.travel.family.api.FamilyApiException;
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

    public void requireValid(String suppliedToken) {
        byte[] supplied = suppliedToken == null
            ? new byte[0]
            : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || supplied.length == 0 || !MessageDigest.isEqual(expectedToken, supplied)) {
            throw new FamilyApiException(HttpStatus.UNAUTHORIZED, "INVALID_JOIN_TOKEN", "Family Join Token is invalid");
        }
    }
}

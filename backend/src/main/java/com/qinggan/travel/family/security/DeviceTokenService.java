package com.qinggan.travel.family.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DeviceTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String signingSecret;

    public DeviceTokenService(String signingSecret) {
        this.signingSecret = signingSecret == null ? "" : signingSecret;
    }

    public String issue(String tripCode, String deviceId, long bindingVersion, String requestId) {
        byte[] secretBytes = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("Device token signing secret must be at least 32 UTF-8 bytes");
        }

        String payload = Objects.requireNonNull(tripCode, "tripCode") + "\n"
            + Objects.requireNonNull(deviceId, "deviceId") + "\n"
            + bindingVersion + "\n"
            + Objects.requireNonNull(requestId, "requestId");

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}

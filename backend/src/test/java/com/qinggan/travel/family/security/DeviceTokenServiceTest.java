package com.qinggan.travel.family.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceTokenServiceTest {

    @Test
    void identicalBindingInputsReproduceTheSameOpaqueDeviceToken() {
        DeviceTokenService service = new DeviceTokenService("0123456789abcdef0123456789abcdef");
        String a = service.issue("qinggan-2026-family", "device-a", 3L, "req-1");
        String b = service.issue("qinggan-2026-family", "device-a", 3L, "req-1");

        assertThat(a).isEqualTo(b);
        assertThat(a).doesNotContain("qinggan-2026-family").doesNotContain("device-a");
    }

    @Test
    void tokenHashIsStableAndDifferentInputsChangeTheToken() {
        DeviceTokenService service = new DeviceTokenService("0123456789abcdef0123456789abcdef");
        String first = service.issue("qinggan-2026-family", "device-a", 3L, "req-1");
        String second = service.issue("qinggan-2026-family", "device-a", 4L, "req-2");

        assertThat(first).isNotEqualTo(second);
        assertThat(new TokenHashingService().sha256Hex(first)).hasSize(64);
    }
}

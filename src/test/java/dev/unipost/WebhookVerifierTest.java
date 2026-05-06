package dev.unipost;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WebhookVerifierTest {
    @Test
    void verifiesValidSignature() throws Exception {
        String secret = "whsec_test";
        String payload = "{\"id\":\"evt_123\"}";
        String signature = "sha256=" + hmacHex(secret, payload);
        assertTrue(WebhookVerifier.verifySignature(secret, payload, signature));
    }

    @Test
    void rejectsInvalidSignature() {
        assertFalse(WebhookVerifier.verifySignature("whsec_test", "{\"id\":\"evt_123\"}", "sha256=deadbeef"));
    }

    private static String hmacHex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}

package dev.unipost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UniPostTest {
    @Test
    void requiresApiKeyWhenEnvMissing() {
        String original = System.getenv("UNIPOST_API_KEY");
        try {
            if (original != null && !original.isBlank()) {
                // constructor with explicit empty key still exercises validation
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new UniPost(""));
                assertTrue(error.getMessage().contains("API key is required"));
            } else {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, UniPost::new);
                assertTrue(error.getMessage().contains("API key is required"));
            }
        } finally {
            // no-op; env not mutated in-process
        }
    }

    @Test
    void acceptsExplicitApiKeyAndInitializesResources() {
        UniPost client = new UniPost("up_test_xxx");
        assertNotNull(client.posts());
        assertNotNull(client.accounts());
        assertNotNull(client.media());
        assertNotNull(client.analytics());
        assertNotNull(client.connect());
        assertNotNull(client.users());
        assertNotNull(client.workspace());
        assertNotNull(client.apiKeys());
        assertNotNull(client.webhooks());
        assertNotNull(client.platformCredentials());
        assertNotNull(client.deliveryJobs());
    }
}

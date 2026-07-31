package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XAccountReadErrorTest {
    @Test
    void preservesExistingConstructorAndAddsStructuredMetadata() {
        APIError legacy = new APIError(400, "legacy_code", "Legacy", "req_legacy", "{}");
        assertEquals("legacy_code", legacy.getCode());

        APIError detailed = new APIError(
                409,
                "READ_IN_PROGRESS",
                "Still running",
                "req_error_1",
                "{}",
                JsonNodeFactoryHolder.details(),
                true,
                7
        );
        assertEquals("xread_1", detailed.getDetails().path("operation_id").asText());
        assertTrue(detailed.isRetriable());
        assertEquals(7, detailed.getRetryAfterSeconds());
    }

    @Test
    void accountReadErrorsUseStableCodeAndHeaderRetryAfter() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setResponseCode(409)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Retry-After", "7")
                    .setBody("{\"error\":{\"code\":\"READ_IN_PROGRESS\","
                            + "\"message\":\"Still running\","
                            + "\"details\":{\"operation_id\":\"xread_1\"},"
                            + "\"is_retriable\":true,\"retry_after\":3},"
                            + "\"request_id\":\"req_error_1\"}"));
            UniPost client = UniPost.builder()
                    .apiKey("up_test_xxx")
                    .baseUrl(server.url("/").toString())
                    .build();

            APIError error = assertThrows(APIError.class, () -> client.accounts().profile(
                    "sa_x_123",
                    Map.of(
                            "external_user_id", "user_42",
                            "idempotency_key", "profile-user-42"
                    )
            ));

            assertEquals(409, error.getStatusCode());
            assertEquals("READ_IN_PROGRESS", error.getCode());
            assertEquals("req_error_1", error.getRequestId());
            assertEquals("xread_1", error.getDetails().path("operation_id").asText());
            assertTrue(error.isRetriable());
            assertEquals(7, error.getRetryAfterSeconds());
        }
    }

    private static final class JsonNodeFactoryHolder {
        private static JsonNode details() {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("operation_id", "xread_1");
        }
    }
}

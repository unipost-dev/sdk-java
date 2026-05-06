package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceRequestTest {
    private MockWebServer server;
    private UniPost client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = UniPost.builder()
                .apiKey("up_test_xxx")
                .baseUrl(server.url("/").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void listsAccountsAtV1Accounts() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":\"sa_1\",\"platform\":\"twitter\"}]}"));

        Page<JsonNode> result = client.accounts().list();
        assertEquals(1, result.getData().size());
        assertEquals("twitter", result.getData().get(0).path("platform").asText());

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/accounts", request.getPath());
        assertEquals("Bearer up_test_xxx", request.getHeader("Authorization"));
    }

    @Test
    void filtersAccountsByPlatform() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[]}"));

        client.accounts().list(Map.of("platform", "linkedin"));
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/accounts?platform=linkedin"));
    }

    @Test
    void createsPostsAtV1Posts() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"post_1\",\"caption\":\"Hello!\"}}"));

        JsonNode post = client.posts().create(Map.of(
                "caption", "Hello!",
                "account_ids", List.of("sa_1", "sa_2")
        ), "key-001");

        assertEquals("post_1", post.path("id").asText());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/posts", request.getPath());
        assertEquals("key-001", request.getHeader("Idempotency-Key"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"caption\":\"Hello!\""));
        assertTrue(body.contains("\"account_ids\":[\"sa_1\",\"sa_2\"]"));
    }

    @Test
    void readsMetaNextCursorFromPaginatedPostsList() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":\"post_5\"}],\"meta\":{\"next_cursor\":\"cur_abc\"}}"));

        Page<JsonNode> result = client.posts().list(Map.of("status", "published", "platform", "twitter", "limit", 10));
        assertEquals(1, result.getData().size());
        assertEquals("cur_abc", result.getNextCursor());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("status=published"));
        assertTrue(request.getPath().contains("platform=twitter"));
        assertTrue(request.getPath().contains("limit=10"));
    }

    @Test
    void readsPlatformCapabilitiesAtV1PlatformsCapabilities() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"schema_version\":\"2026-01-01\"}}"));

        JsonNode result = client.platforms().capabilities();
        assertEquals("2026-01-01", result.path("schema_version").asText());

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/platforms/capabilities", request.getPath());
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

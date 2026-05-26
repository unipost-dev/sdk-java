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

    @Test
    void listsAnalyticsPostsWithExplorerFilters() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"post_id\":\"post_1\",\"platform\":\"pinterest\"}],\"meta\":{\"next_cursor\":\"25\"}}"));

        Page<JsonNode> result = client.analytics().posts(Map.of(
                "platform", "pinterest",
                "account_id", "sa_1",
                "post_id", "post_1",
                "sort", "engagement_rate",
                "limit", 25,
                "cursor", "0"
        ));

        assertEquals("post_1", result.getData().get(0).path("post_id").asText());
        assertEquals("25", result.getNextCursor());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/analytics/posts"));
        assertTrue(request.getPath().contains("platform=pinterest"));
        assertTrue(request.getPath().contains("account_id=sa_1"));
        assertTrue(request.getPath().contains("post_id=post_1"));
        assertTrue(request.getPath().contains("sort=engagement_rate"));
        assertTrue(request.getPath().contains("limit=25"));
        assertTrue(request.getPath().contains("cursor=0"));
    }

    @Test
    void exportsAnalyticsPostsAsCsvText() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/csv")
                .setBody("post_id,platform\npost_1,tiktok\n"));

        String csv = client.analytics().exportPostsCsv(Map.of("platform", "tiktok"));

        assertTrue(csv.contains("post_id,platform"));
        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/analytics/posts/export"));
        assertTrue(request.getPath().contains("platform=tiktok"));
    }

    @Test
    void readsAnalyticsPlatformAvailabilityAndDetails() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"platform\":\"tiktok\",\"health\":\"ready\"}]}"));
        server.enqueue(jsonResponse("{\"data\":{\"platform\":\"tiktok\",\"summary\":{\"posts\":3}}}"));

        List<JsonNode> platforms = client.analytics().platforms(Map.of("from", "2026-05-01", "to", "2026-05-31"));
        JsonNode platform = client.analytics().platform("tiktok", Map.of("profile_id", "prof_1"));

        assertEquals("tiktok", platforms.get(0).path("platform").asText());
        assertEquals(3, platform.path("summary").path("posts").asInt());

        RecordedRequest listRequest = server.takeRequest();
        RecordedRequest detailRequest = server.takeRequest();
        assertTrue(listRequest.getPath().contains("/v1/analytics/platforms"));
        assertTrue(listRequest.getPath().contains("from=2026-05-01"));
        assertTrue(detailRequest.getPath().contains("/v1/analytics/platforms/tiktok"));
        assertTrue(detailRequest.getPath().contains("profile_id=prof_1"));
    }

    @Test
    void requestsAnalyticsRefresh() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"status\":\"queued\",\"matched_count\":7,\"requested_count\":5,\"limit\":5}}"));

        JsonNode result = client.analytics().refresh(Map.of("platform", "threads", "limit", 5));

        assertEquals("queued", result.path("status").asText());
        assertEquals(5, result.path("requested_count").asInt());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/analytics/refresh", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"platform\":\"threads\""));
        assertTrue(body.contains("\"limit\":5"));
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

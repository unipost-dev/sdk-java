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

    @Test
    void reservesMediaWithoutRequiredSizeBytes() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"media_audio_1\",\"status\":\"reserved\",\"upload_url\":\"https://upload.example/audio\"}}"));

        JsonNode media = client.media().upload(Map.of(
                "filename", "voiceover.mp3",
                "content_type", "audio/mpeg"
        ));

        assertEquals("media_audio_1", media.path("id").asText());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/media", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"filename\":\"voiceover.mp3\""));
        assertTrue(body.contains("\"content_type\":\"audio/mpeg\""));
        assertFalse(body.contains("size_bytes"));
    }

    @Test
    void createsAudioOverlayWithIdempotency() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"id\":\"mpj_1\",\"status\":\"queued\",\"video_media_id\":\"media_video_1\",\"audio_media_id\":\"media_audio_1\",\"output_media_id\":null,\"mode\":\"mix\",\"fit\":\"trim_to_video\",\"created_at\":\"2026-07-03T12:00:00Z\"}}"));

        JsonNode job = client.media().audioOverlays().create(Map.of(
                "video_media_id", "media_video_1",
                "audio_media_id", "media_audio_1",
                "mode", "mix",
                "fit", "trim_to_video"
        ), "overlay-1");

        assertEquals("mpj_1", job.path("id").asText());
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/media/audio-overlays", request.getPath());
        assertEquals("overlay-1", request.getHeader("Idempotency-Key"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"video_media_id\":\"media_video_1\""));
        assertTrue(body.contains("\"audio_media_id\":\"media_audio_1\""));
    }

    @Test
    void getsAudioOverlayJob() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"mpj_1\",\"status\":\"succeeded\",\"output_media_id\":\"media_output_1\"}}"));

        JsonNode job = client.media().audioOverlays().get("mpj_1");

        assertEquals("media_output_1", job.path("output_media_id").asText());
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/media/audio-overlays/mpj_1", request.getPath());
    }

    @Test
    void listsLogsWithCursorFilters() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":110,\"action\":\"post.publish.failed\",\"status\":\"error\"}],\"meta\":{\"limit\":25,\"has_more\":true,\"next_cursor\":\"cur_abc\"}}"));

        Page<JsonNode> result = client.logs().list(Map.of(
                "status", "error",
                "level", "warn",
                "profile_id", "prof_1",
                "error_code", "provider_failed",
                "limit", 25,
                "cursor", "cur_prev"
        ));

        assertEquals(110, result.getData().get(0).path("id").asLong());
        assertEquals("cur_abc", result.getNextCursor());

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/logs"));
        assertTrue(request.getPath().contains("status=error"));
        assertTrue(request.getPath().contains("level=warn"));
        assertTrue(request.getPath().contains("profile_id=prof_1"));
        assertTrue(request.getPath().contains("error_code=provider_failed"));
        assertTrue(request.getPath().contains("limit=25"));
        assertTrue(request.getPath().contains("cursor=cur_prev"));
    }

    @Test
    void getsSingleLogById() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"id\":110,\"action\":\"post.publish.failed\",\"request_payload\":null}}"));

        JsonNode log = client.logs().get(110);

        assertEquals(110, log.path("id").asLong());
        assertEquals("post.publish.failed", log.path("action").asText());
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/logs/110", request.getPath());
    }

    @Test
    void streamsSseLogCreatedEvents() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("event: log.created\nid: 110\ndata: {\"id\":110,\"action\":\"post.publish.failed\",\"status\":\"error\"}\n\n"));

        try (LogStream stream = client.logs().stream(Map.of("status", "error", "after_id", 109))) {
            assertTrue(stream.next());
            assertEquals("110", stream.id());
            assertEquals("log.created", stream.eventName());
            assertEquals(110, stream.event().path("id").asLong());
        }

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("/v1/logs/stream"));
        assertTrue(request.getPath().contains("status=error"));
        assertTrue(request.getPath().contains("after_id=109"));
        assertEquals("text/event-stream", request.getHeader("Accept"));
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

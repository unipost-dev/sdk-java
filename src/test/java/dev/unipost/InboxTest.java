package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxTest {
    private MockWebServer server;
    private UniPost client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = UniPost.builder()
                .apiKey("up_test_inbox")
                .baseUrl(server.url("/").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void initializesInboxAccessorOnce() {
        assertNotNull(client.inbox());
        assertSame(client.inbox(), client.inbox());
    }

    @Test
    void listsManagedUserInboxWithExactScopeAndFilters() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":\"inbox_1\",\"source\":\"comment\"}],\"request_id\":\"req_1\"}"));

        List<JsonNode> result = client.inbox().managedUser("managed/user +1").list(Map.of(
                "source", "comment",
                "is_read", false,
                "is_own", false,
                "limit", 25
        ));

        assertEquals(1, result.size());
        assertEquals("inbox_1", result.get(0).path("id").asText());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/inbox", request.getRequestUrl().encodedPath());
        assertEquals("managed_user", request.getRequestUrl().queryParameter("inbox_scope"));
        assertEquals("managed/user +1", request.getRequestUrl().queryParameter("external_user_id"));
        assertEquals("comment", request.getRequestUrl().queryParameter("source"));
        assertEquals("false", request.getRequestUrl().queryParameter("is_read"));
        assertEquals("false", request.getRequestUrl().queryParameter("is_own"));
        assertEquals("25", request.getRequestUrl().queryParameter("limit"));
        assertEquals(6, request.getRequestUrl().querySize());
        assertTrue(request.getPath().contains("external_user_id=managed%2Fuser+%2B1"));
        assertEquals("Bearer up_test_inbox", request.getHeader("Authorization"));
    }

    @Test
    void listsWorkspaceInboxWithoutManagedUserIdentifier() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[]}"));

        assertTrue(client.inbox().workspace().list().isEmpty());

        HttpUrl url = server.takeRequest().getRequestUrl();
        assertEquals("/v1/inbox", url.encodedPath());
        assertEquals("workspace", url.queryParameter("inbox_scope"));
        assertEquals(null, url.queryParameter("external_user_id"));
        assertEquals(1, url.querySize());
    }

    @Test
    void omitsNullFiltersAndCopiesCallerInput() throws Exception {
        server.enqueue(jsonResponse("{\"data\":[]}"));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("source", "dm");
        filters.put("is_read", null);

        client.inbox().managedUser("user_1").list(filters);
        filters.put("source", "comment");
        filters.put("inbox_scope", "workspace");

        HttpUrl url = server.takeRequest().getRequestUrl();
        assertEquals("dm", url.queryParameter("source"));
        assertEquals(null, url.queryParameter("is_read"));
        assertEquals("managed_user", url.queryParameter("inbox_scope"));
        assertEquals("user_1", url.queryParameter("external_user_id"));
    }

    @Test
    void rejectsBlankManagedUserBeforeNetwork() {
        assertThrows(IllegalArgumentException.class, () -> client.inbox().managedUser(""));
        assertThrows(IllegalArgumentException.class, () -> client.inbox().managedUser(" \t\n"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rejectsReservedFiltersBeforeNetwork() {
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().managedUser("user_1").list(Map.of("inbox_scope", "workspace")));
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().workspace().list(Map.of("external_user_id", "user_2")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rejectsUnknownFiltersBeforeNetwork() {
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().managedUser("user_1").list(Map.of("cursor", "next")));
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().workspace().list(Map.of("account_id", "sa_1")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void listReturnTypeIsListRatherThanPage() throws Exception {
        Method noArg = Inbox.Scoped.class.getMethod("list");
        Method withFilters = Inbox.Scoped.class.getMethod("list", Map.class);

        assertEquals(List.class, noArg.getReturnType());
        assertEquals(List.class, withFilters.getReturnType());
        assertFalse(Page.class.isAssignableFrom(noArg.getReturnType()));
        assertFalse(Page.class.isAssignableFrom(withFilters.getReturnType()));
    }

    @Test
    void repliesInManagedUserScopeWithCanonicalBodyEncodedIdAndIdempotency() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-UniPost-Operation-Id", "  op_completed  ")
                .setBody("{\"data\":{\"id\":\"inbox_1\",\"source\":\"x_reply\"}}"));
        Map<String, Object> callerBody = new LinkedHashMap<>();
        callerBody.put("text", "Thanks for reaching out!");
        callerBody.put("inbox_scope", "workspace");
        callerBody.put("unexpected", "must-not-be-sent");

        Inbox.ReplyResult result = client.inbox().managedUser("user A").reply(
                "item /?#",
                callerBody,
                "idem-exact-value"
        );
        callerBody.put("text", "mutated");

        assertEquals(Inbox.ReplyState.COMPLETED, result.getState());
        assertEquals("inbox_1", result.getItem().path("id").asText());
        assertEquals("op_completed", result.getOperationId());
        assertEquals(null, result.getCode());
        assertEquals(null, result.getMessage());
        assertEquals(null, result.getRequestId());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/inbox/item%20%2F%3F%23/reply", request.getRequestUrl().encodedPath());
        assertEquals("managed_user", request.getRequestUrl().queryParameter("inbox_scope"));
        assertEquals("user A", request.getRequestUrl().queryParameter("external_user_id"));
        assertEquals(2, request.getRequestUrl().querySize());
        assertEquals("idem-exact-value", request.getHeader("Idempotency-Key"));
        assertEquals("Bearer up_test_inbox", request.getHeader("Authorization"));
        assertEquals("unipost-java/0.5.0", request.getHeader("User-Agent"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals(
                Map.of("text", "Thanks for reaching out!"),
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(request.getBody().readUtf8(), Map.class)
        );
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void completedReplyOmitsAbsentOptionalValues() throws Exception {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"inbox_2\"}}"));

        Inbox.ReplyResult result = client.inbox().workspace().reply(
                "inbox_2",
                Map.of("text", "Acknowledged")
        );

        assertEquals(Inbox.ReplyState.COMPLETED, result.getState());
        assertEquals("inbox_2", result.getItem().path("id").asText());
        assertEquals(null, result.getOperationId());
        assertEquals(null, result.getCode());
        assertEquals(null, result.getMessage());
        assertEquals(null, result.getRequestId());
        RecordedRequest request = server.takeRequest();
        assertEquals("workspace", request.getRequestUrl().queryParameter("inbox_scope"));
        assertEquals(null, request.getRequestUrl().queryParameter("external_user_id"));
        assertEquals(null, request.getHeader("Idempotency-Key"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void acceptedReplyReturnsExplicitReconcilingResult() {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-uNiPoSt-OpErAtIoN-iD", "  op_reconcile_1  ")
                .setBody("{\"error\":{\"code\":\"X_REMOTE_ACCEPTED_RECONCILING\","
                        + "\"message\":\"Remote accepted; poll status\"},\"request_id\":\"req_202\"}"));

        Inbox.ReplyResult result = client.inbox().workspace().reply(
                "inbox_1",
                Map.of("text", "Reply")
        );

        assertEquals(Inbox.ReplyState.RECONCILING, result.getState());
        assertEquals(null, result.getItem());
        assertEquals("op_reconcile_1", result.getOperationId());
        assertEquals("X_REMOTE_ACCEPTED_RECONCILING", result.getCode());
        assertEquals("Remote accepted; poll status", result.getMessage());
        assertEquals("req_202", result.getRequestId());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void malformedSuccessfulRepliesFailClosedOnceWithoutLeakingSecrets() {
        Object[][] cases = {
                {200, null, "{\"secret\":\"response-secret\"}"},
                {200, null, "{\"data\":null,\"secret\":\"response-secret\"}"},
                {202, null, "{\"error\":{\"code\":\"X_REMOTE_ACCEPTED_RECONCILING\",\"message\":\"response-secret\"}}"},
                {202, "   ", "{\"error\":{\"code\":\"X_REMOTE_ACCEPTED_RECONCILING\",\"message\":\"response-secret\"}}"},
                {202, "op_1", "{\"error\":{\"code\":\"PLATFORM_ERROR\",\"message\":\"response-secret\"}}"},
                {202, "op_1", "{\"error\":{\"code\":\"X_REMOTE_ACCEPTED_RECONCILING\"},\"secret\":\"response-secret\"}"},
                {201, null, "{\"data\":{\"id\":\"inbox_1\"},\"secret\":\"response-secret\"}"},
                {204, null, ""}
        };

        for (Object[] testCase : cases) {
            int before = server.getRequestCount();
            MockResponse response = new MockResponse()
                    .setResponseCode((Integer) testCase[0])
                    .addHeader("Content-Type", "application/json")
                    .setBody((String) testCase[2]);
            if (testCase[1] != null) {
                response.addHeader("X-UniPost-Operation-Id", (String) testCase[1]);
            }
            server.enqueue(response);

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client.inbox().workspace().reply(
                            "inbox_1",
                            Map.of("text", "Reply"),
                            "idempotency-secret"
                    )
            );

            assertEquals("Failed to decode Inbox reply response.", error.getMessage());
            assertFalse(error.getMessage().contains("response-secret"));
            assertFalse(error.getMessage().contains("idempotency-secret"));
            assertEquals(before + 1, server.getRequestCount());
        }
    }

    @Test
    void malformedSuccessfulJsonFailsClosedOnceWithoutLeakingSecrets() {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-UniPost-Operation-Id", "op_1")
                .setBody("{response-secret"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client.inbox().workspace().reply(
                        "inbox_1",
                        Map.of("text", "Reply"),
                        "idempotency-secret"
                )
        );

        assertEquals("Failed to decode Inbox reply response.", error.getMessage());
        assertFalse(error.toString().contains("response-secret"));
        assertFalse(error.toString().contains("idempotency-secret"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void preservesReplyApiErrorsAndMakesOneRequestIncludingRateLimits() {
        Object[][] cases = {
                {400, "VALIDATION_ERROR"},
                {402, "X_MONTHLY_USAGE_LIMIT_EXCEEDED"},
                {409, "X_RECONNECT_REQUIRED"},
                {409, "NEEDS_RECONNECT"},
                {409, "IDEMPOTENCY_KEY_CONFLICT"},
                {409, "X_WRITE_OUTCOME_PENDING"},
                {409, "X_WRITE_NEEDS_RECONCILIATION"},
                {409, "X_USAGE_REVERSAL_PENDING"},
                {422, "VALIDATION_ERROR"},
                {422, "PLATFORM_ERROR"},
                {429, "RATE_LIMITED"}
        };

        for (Object[] testCase : cases) {
            int status = (Integer) testCase[0];
            String code = (String) testCase[1];
            int before = server.getRequestCount();
            String raw = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"expected message\"}}";
            server.enqueue(new MockResponse()
                    .setResponseCode(status)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Request-Id", "req_error")
                    .addHeader("Retry-After", "1")
                    .setBody(raw));

            APIError error = assertThrows(
                    APIError.class,
                    () -> client.inbox().workspace().reply("inbox_1", Map.of("text", "Reply"))
            );

            assertEquals(status, error.getStatusCode());
            assertEquals(code, error.getCode());
            assertEquals("req_error", error.getRequestId());
            assertEquals(raw, error.getResponseBody());
            assertEquals(before + 1, server.getRequestCount());
        }
    }

    @Test
    void rejectsUnsafeReplyInputsBeforeNetworkWithSafeErrors() {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"unexpected_request\"}}"));
        for (String id : List.of("", "   ", "\u2003", ".", "..", " .. ", "\u2003..\u2003")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.inbox().workspace().reply(id, Map.of("text", "Reply"))
            );
        }
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().workspace().reply("inbox_1", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().workspace().reply("inbox_1", Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,
                () -> client.inbox().workspace().reply("inbox_1", Map.of("text", 42)));

        for (String unsafeKey : List.of(
                "secret\rvalue",
                "secret\nvalue",
                "secret\u007fvalue",
                "secret\u0085value",
                "secret-\ud83d\udd10-value"
        )) {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.inbox().workspace().reply(
                            "inbox_1",
                            Map.of("text", "Reply"),
                            unsafeKey
                    )
            );
            assertEquals("Invalid Inbox reply idempotency key.", error.getMessage());
            assertFalse(error.toString().contains(unsafeKey));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rejectsUnsafeApiKeysBeforeBuildingAuthorizationHeaderWithoutLeakingThem() {
        server.enqueue(jsonResponse("{\"data\":{\"id\":\"unexpected_request\"}}"));
        for (String unsafeKey : List.of(
                "up_test_secret\n",
                "up_test_secret\u0085value",
                "up_test_secret-\ud83d\udd10-value"
        )) {
            UniPost unsafeClient = UniPost.builder()
                    .apiKey(unsafeKey)
                    .baseUrl(server.url("/").toString())
                    .build();

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> unsafeClient.inbox().workspace().reply(
                            "inbox_1",
                            Map.of("text", "Reply")
                    )
            );

            assertEquals("UniPost Inbox request credentials are invalid.", error.getMessage());
            assertNull(error.getCause());
            assertFalse(error.toString().contains(unsafeKey));
            assertFalse(error.toString().contains("Authorization"));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void replyDoesNotFollowRedirect() throws Exception {
        try (MockWebServer target = new MockWebServer()) {
            target.start();
            target.enqueue(jsonResponse("{\"data\":{\"id\":\"must_not_be_reached\"}}"));
            server.enqueue(new MockResponse()
                    .setResponseCode(307)
                    .addHeader("Location", target.url("/redirect-target"))
                    .setBody("{\"error\":{\"code\":\"TEMPORARY_REDIRECT\",\"message\":\"redirect\"}}"));

            APIError error = assertThrows(
                    APIError.class,
                    () -> client.inbox().workspace().reply("inbox_1", Map.of("text", "Reply"))
            );

            assertEquals(307, error.getStatusCode());
            assertEquals(1, server.getRequestCount());
            assertEquals(0, target.getRequestCount());
        }
    }

    @Test
    void replyRejectsRedirectEnabledCustomClientBeforeNetwork() {
        HttpClient redirectingClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        UniPost unsafeClient = UniPost.builder()
                .apiKey("up_test_inbox")
                .baseUrl(server.url("/").toString())
                .httpClient(redirectingClient)
                .build();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> unsafeClient.inbox().workspace().reply("inbox_1", Map.of("text", "Reply"))
        );

        assertEquals("UniPost Inbox writes require redirects to be disabled.", error.getMessage());
        assertEquals(HttpClient.Redirect.ALWAYS, redirectingClient.followRedirects());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void responseAwareTransportRetainsDeepImmutableMetadata() {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-UniPost-Operation-Id", "op_original")
                .addHeader("X-Value", "one")
                .addHeader("X-Value", "two")
                .setBody("{\"ok\":true}"));
        ApiHttpClient http = new ApiHttpClient(
                "up_test_inbox",
                server.url("/").toString(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                "unipost-java/test"
        );

        ApiHttpClient.Response response = http.postWithResponse(
                "/v1/inbox/inbox_1/reply",
                Map.of("inbox_scope", "workspace"),
                Map.of("text", "Reply"),
                Collections.emptyMap()
        );

        assertEquals(202, response.getStatusCode());
        assertEquals("op_original", response.firstHeader("x-unipost-operation-id"));
        assertTrue(response.getBody().path("ok").asBoolean());
        assertThrows(UnsupportedOperationException.class,
                () -> response.getHeaders().put("x-new", List.of("value")));
        assertThrows(UnsupportedOperationException.class,
                () -> response.getHeaders().get("x-value").add("three"));
        assertEquals(1, server.getRequestCount());
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

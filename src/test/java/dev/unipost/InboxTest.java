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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

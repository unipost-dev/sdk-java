package dev.unipost;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class ApiHttpClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final String userAgent;

    ApiHttpClient(String apiKey, String baseUrl, Duration timeout) {
        this(apiKey, baseUrl, HttpClient.newBuilder().connectTimeout(timeout).build(), "unipost-java/0.5.0");
    }

    ApiHttpClient(String apiKey, String baseUrl, HttpClient httpClient, String userAgent) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
    }

    JsonNode get(String path) {
        return get(path, Collections.emptyMap());
    }

    JsonNode get(String path, Map<String, ?> query) {
        return send("GET", path, query, null, Collections.emptyMap());
    }

    JsonNode getInbox(String path, Map<String, ?> query) {
        validateInboxApiKey();
        return get(path, query);
    }

    String getText(String path, Map<String, ?> query) {
        return sendText("GET", path, query, Collections.emptyMap());
    }

    InputStream stream(String path, Map<String, ?> query, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + buildQuery(query)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .header("User-Agent", userAgent)
                    .GET();

            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            String requestId = response.headers().firstValue("X-Request-Id").orElse(null);
            String raw = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = raw.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(raw);
            String code = textAt(json, "error.normalized_code");
            if (code == null) code = textAt(json, "error.code");
            if (code == null) code = textAt(json, "code");
            String message = textAt(json, "error.message");
            if (message == null) message = textAt(json, "message");
            throw new APIError(response.statusCode(), code, message, requestId, raw);
        } catch (APIError e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode JSON error", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("UniPost request failed", e);
        }
    }

    JsonNode post(String path) {
        return post(path, null, Collections.emptyMap());
    }

    JsonNode post(String path, Object body) {
        return post(path, body, Collections.emptyMap());
    }

    JsonNode post(String path, Object body, Map<String, String> extraHeaders) {
        return send("POST", path, Collections.emptyMap(), body, extraHeaders);
    }

    Response postWithResponse(
            String path,
            Map<String, ?> query,
            Object body,
            Map<String, String> extraHeaders
    ) {
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalStateException("UniPost Inbox writes require redirects to be disabled.");
        }
        validateInboxApiKey();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + buildQuery(query)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent);

            if (body == null) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                final String json;
                try {
                    json = MAPPER.writeValueAsString(body);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to encode UniPost request.");
                }
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            }

            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String raw = response.body() == null ? "" : response.body();
            final JsonNode parsed;
            try {
                parsed = raw.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(raw);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to decode Inbox reply response.");
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiError(response.statusCode(), response.headers(), raw, parsed);
            }
            return new Response(response.statusCode(), response.headers(), parsed);
        } catch (APIError | IllegalStateException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("UniPost request failed", e);
        }
    }

    Inbox.WebSocketConnectionDetails inboxWebSocketConnectionDetails(Map<String, ?> scope) {
        validateInboxApiKey();
        try {
            URI base = new URI(baseUrl);
            String scheme;
            if ("https".equalsIgnoreCase(base.getScheme())) {
                scheme = "wss";
            } else if ("http".equalsIgnoreCase(base.getScheme())) {
                scheme = "ws";
            } else {
                throw new IllegalArgumentException();
            }
            if (base.getHost() == null
                    || base.getHost().isBlank()
                    || base.getRawUserInfo() != null
                    || base.getRawQuery() != null
                    || base.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            URI connection = new URI(
                    scheme,
                    null,
                    base.getHost(),
                    base.getPort(),
                    "/v1/inbox/ws",
                    null,
                    null
            );
            return new Inbox.WebSocketConnectionDetails(
                    connection.toASCIIString() + buildQuery(scope),
                    Map.of("Authorization", "Bearer " + apiKey)
            );
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalStateException("UniPost Inbox WebSocket base URL is invalid.");
        }
    }

    JsonNode patch(String path, Object body) {
        return send("PATCH", path, Collections.emptyMap(), body, Collections.emptyMap());
    }

    JsonNode delete(String path) {
        return send("DELETE", path, Collections.emptyMap(), null, Collections.emptyMap());
    }

    JsonNode send(String method, String path, Map<String, ?> query, Object body, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + buildQuery(query)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent);

            if (body != null) {
                String json = body instanceof String ? (String) body : MAPPER.writeValueAsString(body);
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String raw = response.body() == null ? "" : response.body();
            JsonNode json = raw.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(raw);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiError(response.statusCode(), response.headers(), raw, json);
            }

            return json;
        } catch (APIError e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode/decode JSON", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("UniPost request failed", e);
        }
    }

    String sendText(String method, String path, Map<String, ?> query, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path + buildQuery(query)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", userAgent)
                    .method(method, HttpRequest.BodyPublishers.noBody());

            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String requestId = response.headers().firstValue("X-Request-Id").orElse(null);
                String raw = response.body() == null ? "" : response.body();
                JsonNode json = raw.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(raw);
                String code = textAt(json, "error.normalized_code");
                if (code == null) code = textAt(json, "error.code");
                if (code == null) code = textAt(json, "code");
                String message = textAt(json, "error.message");
                if (message == null) message = textAt(json, "message");
                throw new APIError(response.statusCode(), code, message, requestId, raw);
            }
            return response.body() == null ? "" : response.body();
        } catch (APIError e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode JSON error", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("UniPost request failed", e);
        }
    }

    static JsonNode data(JsonNode root) {
        if (root == null || root.isNull()) return MAPPER.nullNode();
        JsonNode node = root.get("data");
        return node == null ? MAPPER.nullNode() : node;
    }

    static List<JsonNode> dataList(JsonNode root) {
        JsonNode node = data(root);
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    static Page<JsonNode> page(JsonNode root) {
        Map<String, Object> meta = Collections.emptyMap();
        String nextCursor = null;
        if (root != null && root.has("meta") && root.get("meta").isObject()) {
            meta = MAPPER.convertValue(root.get("meta"), new TypeReference<Map<String, Object>>() {});
            Object rawCursor = meta.get("next_cursor");
            nextCursor = rawCursor == null ? null : String.valueOf(rawCursor);
        }
        return new Page<>(dataList(root), meta, nextCursor);
    }

    private static String buildQuery(Map<String, ?> query) {
        if (query == null || query.isEmpty()) return "";
        String encoded = query.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> normalizeQueryValue(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.joining("&"));
        return encoded.isEmpty() ? "" : "?" + encoded;
    }

    private static List<String> normalizeQueryValue(String key, Object value) {
        if (value instanceof Iterable<?>) {
            List<String> parts = new ArrayList<>();
            Iterator<?> iterator = ((Iterable<?>) value).iterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item != null) parts.add(String.valueOf(item));
            }
            if (parts.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(encode(key) + "=" + encode(String.join(",", parts)));
        }
        if (value.getClass().isArray()) {
            List<String> parts = new ArrayList<>();
            Object[] array = (Object[]) value;
            for (Object item : array) {
                if (item != null) parts.add(String.valueOf(item));
            }
            if (parts.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(encode(key) + "=" + encode(String.join(",", parts)));
        }
        return Collections.singletonList(encode(key) + "=" + encode(String.valueOf(value)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static APIError apiError(int statusCode, HttpHeaders headers, String raw, JsonNode json) {
        String requestId = headers.firstValue("X-Request-Id").orElse(null);
        String code = textAt(json, "error.normalized_code");
        if (code == null) code = textAt(json, "error.code");
        if (code == null) code = textAt(json, "code");
        String message = textAt(json, "error.message");
        if (message == null) message = textAt(json, "message");
        return new APIError(statusCode, code, message, requestId, raw);
    }

    private void validateInboxApiKey() {
        for (int index = 0; index < apiKey.length(); index++) {
            char value = apiKey.charAt(index);
            if (value < 32 || (value >= 127 && value <= 159) || value > 255) {
                throw new IllegalStateException("UniPost Inbox request credentials are invalid.");
            }
        }
    }

    private static String textAt(JsonNode root, String dottedPath) {
        if (root == null) return null;
        JsonNode node = root;
        for (String part : dottedPath.split("\\.")) {
            node = node.get(part);
            if (node == null) return null;
        }
        return node.isNull() ? null : node.asText();
    }

    static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    static final class Response {
        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final JsonNode body;

        private Response(int statusCode, HttpHeaders headers, JsonNode body) {
            this.statusCode = statusCode;
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            headers.map().forEach((name, values) -> copiedHeaders.put(
                    name.toLowerCase(Locale.ROOT),
                    Collections.unmodifiableList(new ArrayList<>(values))
            ));
            this.headers = Collections.unmodifiableMap(copiedHeaders);
            this.body = body == null ? MAPPER.nullNode() : body.deepCopy();
        }

        int getStatusCode() {
            return statusCode;
        }

        Map<String, List<String>> getHeaders() {
            return headers;
        }

        String firstHeader(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        JsonNode getBody() {
            return body.deepCopy();
        }
    }
}

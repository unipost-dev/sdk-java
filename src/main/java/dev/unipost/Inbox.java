package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scoped access to Inbox messages and comments. */
public final class Inbox extends UniPost.Resource {
    private static final String RECONCILING_CODE = "X_REMOTE_ACCEPTED_RECONCILING";
    private static final String REPLY_DECODE_ERROR = "Failed to decode Inbox reply response.";

    Inbox(ApiHttpClient http) {
        super(http);
    }

    /** Identifies whether a reply completed or requires reconciliation polling. */
    public enum ReplyState {
        COMPLETED,
        RECONCILING
    }

    /** A reply result whose state makes completed and reconciling values explicit. */
    public static final class ReplyResult {
        private final ReplyState state;
        private final JsonNode item;
        private final String operationId;
        private final String code;
        private final String message;
        private final String requestId;

        private ReplyResult(
                ReplyState state,
                JsonNode item,
                String operationId,
                String code,
                String message,
                String requestId
        ) {
            this.state = state;
            this.item = item == null ? null : item.deepCopy();
            this.operationId = operationId;
            this.code = code;
            this.message = message;
            this.requestId = requestId;
        }

        private static ReplyResult completed(JsonNode item, String operationId) {
            return new ReplyResult(ReplyState.COMPLETED, item, operationId, null, null, null);
        }

        private static ReplyResult reconciling(String operationId, String message, String requestId) {
            return new ReplyResult(
                    ReplyState.RECONCILING,
                    null,
                    operationId,
                    RECONCILING_CODE,
                    message,
                    requestId
            );
        }

        public ReplyState getState() {
            return state;
        }

        public JsonNode getItem() {
            return item == null ? null : item.deepCopy();
        }

        public String getOperationId() {
            return operationId;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /** Access Inbox data belonging to one managed user. */
    public Scoped managedUser(String externalUserId) {
        if (externalUserId == null || externalUserId.isBlank()) {
            throw new IllegalArgumentException("externalUserId must not be blank");
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("inbox_scope", "managed_user");
        scope.put("external_user_id", externalUserId);
        return new Scoped(http, scope);
    }

    /** Access the workspace-wide Inbox view. */
    public Scoped workspace() {
        return new Scoped(http, Map.of("inbox_scope", "workspace"));
    }

    /** An Inbox resource whose authorization scope cannot be changed by callers. */
    public static final class Scoped extends UniPost.Resource {
        private static final Set<String> FILTER_KEYS = Set.of("source", "is_read", "is_own", "limit");
        private static final Set<String> RESERVED_KEYS = Set.of("inbox_scope", "external_user_id");

        private final Map<String, Object> scope;

        private Scoped(ApiHttpClient http, Map<String, ?> scope) {
            super(http);
            this.scope = immutableCopy(scope);
        }

        public List<JsonNode> list() {
            return list(Collections.emptyMap());
        }

        public List<JsonNode> list(Map<String, ?> params) {
            Map<String, Object> query = params == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(params);
            validateFilters(query);
            query.putAll(scope);
            return dataList(http.get("/v1/inbox", query));
        }

        /** Sends one reply without retrying or following redirects. */
        public ReplyResult reply(String itemId, Map<String, ?> body) {
            return reply(itemId, body, null);
        }

        /** Sends one reply with an optional idempotency key. */
        public ReplyResult reply(String itemId, Map<String, ?> body, String idempotencyKey) {
            String encodedItemId = encodePathId(itemId);
            Map<String, Object> canonicalBody = canonicalReplyBody(body);
            Map<String, String> headers = Collections.emptyMap();
            if (idempotencyKey != null) {
                validateIdempotencyKey(idempotencyKey);
                headers = Map.of("Idempotency-Key", idempotencyKey);
            }

            ApiHttpClient.Response response = http.postWithResponse(
                    "/v1/inbox/" + encodedItemId + "/reply",
                    scope,
                    canonicalBody,
                    headers
            );
            return decodeReply(response);
        }

        private static void validateFilters(Map<String, ?> params) {
            for (String key : params.keySet()) {
                if (RESERVED_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Inbox scope cannot be provided as a list filter: " + key);
                }
                if (!FILTER_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unknown Inbox list filter: " + key);
                }
            }
        }

        private static Map<String, Object> immutableCopy(Map<String, ?> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        private static Map<String, Object> canonicalReplyBody(Map<String, ?> body) {
            if (body == null || !(body.get("text") instanceof String)) {
                throw new IllegalArgumentException("Inbox reply text must be a string.");
            }
            return Map.of("text", body.get("text"));
        }

        private static ReplyResult decodeReply(ApiHttpClient.Response response) {
            JsonNode root = response.getBody();
            String operationId = trimmed(response.firstHeader("X-UniPost-Operation-Id"));
            if (response.getStatusCode() == 200) {
                JsonNode item = root == null ? null : root.get("data");
                if (item != null && item.isObject()) {
                    return ReplyResult.completed(item, operationId);
                }
                throw replyDecodeError();
            }

            if (response.getStatusCode() == 202) {
                JsonNode error = root == null ? null : root.get("error");
                JsonNode code = error == null ? null : error.get("code");
                JsonNode message = error == null ? null : error.get("message");
                JsonNode requestId = root == null ? null : root.get("request_id");
                boolean requestIdValid = requestId == null || requestId.isTextual();
                if (operationId != null
                        && code != null
                        && code.isTextual()
                        && RECONCILING_CODE.equals(code.asText())
                        && message != null
                        && message.isTextual()
                        && !message.asText().isBlank()
                        && requestIdValid) {
                    return ReplyResult.reconciling(
                            operationId,
                            message.asText(),
                            requestId == null ? null : requestId.asText()
                    );
                }
                throw replyDecodeError();
            }

            throw replyDecodeError();
        }

        private static String encodePathId(String itemId) {
            if (itemId == null) {
                throw new IllegalArgumentException("Inbox item ID must be a safe path segment.");
            }
            String trimmed = itemId.strip();
            if (trimmed.isEmpty() || ".".equals(trimmed) || "..".equals(trimmed)) {
                throw new IllegalArgumentException("Inbox item ID must be a safe path segment.");
            }

            StringBuilder encoded = new StringBuilder();
            for (byte value : itemId.getBytes(StandardCharsets.UTF_8)) {
                int unsigned = value & 0xff;
                if ((unsigned >= 'a' && unsigned <= 'z')
                        || (unsigned >= 'A' && unsigned <= 'Z')
                        || (unsigned >= '0' && unsigned <= '9')
                        || unsigned == '-'
                        || unsigned == '.'
                        || unsigned == '_'
                        || unsigned == '~') {
                    encoded.append((char) unsigned);
                } else {
                    encoded.append('%');
                    encoded.append(Character.toUpperCase(Character.forDigit((unsigned >>> 4) & 0xf, 16)));
                    encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
                }
            }
            return encoded.toString();
        }

        private static void validateIdempotencyKey(String idempotencyKey) {
            for (int index = 0; index < idempotencyKey.length(); index++) {
                char value = idempotencyKey.charAt(index);
                if (value < 32 || (value >= 127 && value <= 159) || value > 255) {
                    throw new IllegalArgumentException("Invalid Inbox reply idempotency key.");
                }
            }
        }

        private static String trimmed(String value) {
            if (value == null) return null;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static IllegalStateException replyDecodeError() {
            return new IllegalStateException(REPLY_DECODE_ERROR);
        }
    }
}

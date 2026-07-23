package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scoped access to Inbox messages and comments. */
public final class Inbox extends UniPost.Resource {
    Inbox(ApiHttpClient http) {
        super(http);
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
    }
}

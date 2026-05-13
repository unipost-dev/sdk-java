package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UniPost {
    public static final String DEFAULT_BASE_URL = "https://api.unipost.dev";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final String SDK_VERSION = "0.2.9";

    private final ApiHttpClient http;

    private final WorkspaceResource workspace;
    private final ProfilesResource profiles;
    private final AccountsResource accounts;
    private final PlatformsResource platforms;
    private final PlansResource plans;
    private final PlatformCredentialsResource platformCredentials;
    private final ApiKeysResource apiKeys;
    private final PostsResource posts;
    private final DeliveryJobsResource deliveryJobs;
    private final MediaResource media;
    private final AnalyticsResource analytics;
    private final ConnectResource connect;
    private final UsersResource users;
    private final WebhooksResource webhooks;
    private final OAuthResource oauth;
    private final UsageResource usage;

    public UniPost() {
        this(builder());
    }

    public UniPost(String apiKey) {
        this(builder().apiKey(apiKey));
    }

    public UniPost(Builder builder) {
        String apiKey = builder.apiKey != null ? builder.apiKey : System.getenv("UNIPOST_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("UniPost API key is required. Pass new UniPost(\"up_live_xxx\") or set UNIPOST_API_KEY.");
        }
        this.http = new ApiHttpClient(
                apiKey,
                builder.baseUrl == null || builder.baseUrl.isBlank() ? DEFAULT_BASE_URL : builder.baseUrl,
                builder.httpClient != null ? builder.httpClient : HttpClient.newBuilder().connectTimeout(builder.timeout).build(),
                "unipost-java/" + SDK_VERSION
        );
        this.workspace = new WorkspaceResource(http);
        this.profiles = new ProfilesResource(http);
        this.accounts = new AccountsResource(http);
        this.platforms = new PlatformsResource(http);
        this.plans = new PlansResource(http);
        this.platformCredentials = new PlatformCredentialsResource(http);
        this.apiKeys = new ApiKeysResource(http);
        this.posts = new PostsResource(http);
        this.deliveryJobs = new DeliveryJobsResource(http);
        this.media = new MediaResource(http);
        this.analytics = new AnalyticsResource(http);
        this.connect = new ConnectResource(http);
        this.users = new UsersResource(http);
        this.webhooks = new WebhooksResource(http);
        this.oauth = new OAuthResource(http);
        this.usage = new UsageResource(http);
    }

    public static Builder builder() {
        return new Builder();
    }

    public WorkspaceResource workspace() { return workspace; }
    public ProfilesResource profiles() { return profiles; }
    public AccountsResource accounts() { return accounts; }
    public PlatformsResource platforms() { return platforms; }
    public PlansResource plans() { return plans; }
    public PlatformCredentialsResource platformCredentials() { return platformCredentials; }
    public ApiKeysResource apiKeys() { return apiKeys; }
    public PostsResource posts() { return posts; }
    public DeliveryJobsResource deliveryJobs() { return deliveryJobs; }
    public MediaResource media() { return media; }
    public AnalyticsResource analytics() { return analytics; }
    public ConnectResource connect() { return connect; }
    public UsersResource users() { return users; }
    public WebhooksResource webhooks() { return webhooks; }
    public OAuthResource oauth() { return oauth; }
    public UsageResource usage() { return usage; }

    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private HttpClient httpClient;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public UniPost build() {
            return new UniPost(this);
        }
    }

    public abstract static class Resource {
        protected final ApiHttpClient http;

        protected Resource(ApiHttpClient http) {
            this.http = http;
        }

        protected JsonNode data(JsonNode root) {
            return ApiHttpClient.data(root);
        }

        protected List<JsonNode> dataList(JsonNode root) {
            return ApiHttpClient.dataList(root);
        }

        protected Page<JsonNode> page(JsonNode root) {
            return ApiHttpClient.page(root);
        }
    }

    public static final class WorkspaceResource extends Resource {
        WorkspaceResource(ApiHttpClient http) { super(http); }

        public JsonNode get() {
            return data(http.get("/v1/workspace"));
        }

        public JsonNode update(Map<String, Object> params) {
            return data(http.patch("/v1/workspace", params == null ? Map.of() : params));
        }
    }

    public static final class ProfilesResource extends Resource {
        ProfilesResource(ApiHttpClient http) { super(http); }

        public Page<JsonNode> list() {
            return page(http.get("/v1/profiles"));
        }

        public JsonNode get(String profileId) {
            return data(http.get("/v1/profiles/" + profileId));
        }

        public JsonNode create(Map<String, Object> body) {
            return data(http.post("/v1/profiles", body));
        }

        public JsonNode update(String profileId, Map<String, Object> body) {
            return data(http.patch("/v1/profiles/" + profileId, body));
        }

        public void delete(String profileId) {
            http.delete("/v1/profiles/" + profileId);
        }
    }

    public static final class AccountsResource extends Resource {
        AccountsResource(ApiHttpClient http) { super(http); }

        public Page<JsonNode> list() {
            return list(null);
        }

        public Page<JsonNode> list(Map<String, ?> params) {
            return page(http.get("/v1/accounts", params));
        }

        public JsonNode get(String accountId) {
            for (JsonNode account : list().getData()) {
                if (accountId.equals(account.path("id").asText())) {
                    return account;
                }
            }
            throw new IllegalArgumentException("Account not found: " + accountId);
        }

        public JsonNode connect(Map<String, Object> body) {
            return data(http.post("/v1/accounts/connect", body));
        }

        public void disconnect(String accountId) {
            http.delete("/v1/accounts/" + accountId);
        }

        public JsonNode capabilities(String accountId) {
            return data(http.get("/v1/accounts/" + accountId + "/capabilities"));
        }

        public JsonNode health(String accountId) {
            return data(http.get("/v1/accounts/" + accountId + "/health"));
        }

        public JsonNode tikTokCreatorInfo(String accountId) {
            return data(http.get("/v1/accounts/" + accountId + "/tiktok/creator-info"));
        }

        public JsonNode facebookPageInsights(String accountId) {
            return data(http.get("/v1/accounts/" + accountId + "/facebook/page-insights"));
        }
    }

    public static final class PlatformsResource extends Resource {
        PlatformsResource(ApiHttpClient http) { super(http); }

        public JsonNode capabilities() {
            return data(http.get("/v1/platforms/capabilities"));
        }
    }

    public static final class PlansResource extends Resource {
        PlansResource(ApiHttpClient http) { super(http); }

        public List<JsonNode> list() {
            return dataList(http.get("/v1/plans"));
        }
    }

    public static final class PlatformCredentialsResource extends Resource {
        PlatformCredentialsResource(ApiHttpClient http) { super(http); }

        public JsonNode create(Map<String, Object> body) {
            return data(http.post("/v1/platform-credentials", body));
        }

        public Page<JsonNode> list() {
            return page(http.get("/v1/platform-credentials"));
        }

        public void delete(String platform) {
            http.delete("/v1/platform-credentials/" + platform);
        }
    }

    public static final class ApiKeysResource extends Resource {
        ApiKeysResource(ApiHttpClient http) { super(http); }

        public Page<JsonNode> list() {
            return page(http.get("/v1/api-keys"));
        }

        public JsonNode create(Map<String, Object> body) {
            return data(http.post("/v1/api-keys", body));
        }

        public void revoke(String keyId) {
            http.delete("/v1/api-keys/" + keyId);
        }
    }

    public static final class PostsResource extends Resource {
        PostsResource(ApiHttpClient http) { super(http); }

        public JsonNode create(Map<String, Object> body) {
            return create(body, null);
        }

        public JsonNode create(Map<String, Object> body, String idempotencyKey) {
            Map<String, String> headers = idempotencyKey == null ? Map.of() : Map.of("Idempotency-Key", idempotencyKey);
            return data(http.post("/v1/posts", body, headers));
        }

        public JsonNode validate(Map<String, Object> body) {
            return data(http.post("/v1/posts/validate", body));
        }

        public Page<JsonNode> list() {
            return list(null);
        }

        public Page<JsonNode> list(Map<String, ?> params) {
            return page(http.get("/v1/posts", params));
        }

        public JsonNode get(String postId) {
            return data(http.get("/v1/posts/" + postId));
        }

        public JsonNode getQueue(String postId) {
            return data(http.get("/v1/posts/" + postId + "/queue"));
        }

        public List<JsonNode> analytics(String postId) {
            return analytics(postId, null);
        }

        public List<JsonNode> analytics(String postId, Map<String, ?> params) {
            return dataList(http.get("/v1/posts/" + postId + "/analytics", params));
        }

        public JsonNode publish(String postId) {
            return data(http.post("/v1/posts/" + postId + "/publish"));
        }

        public JsonNode update(String postId, Map<String, Object> body) {
            return data(http.patch("/v1/posts/" + postId, body));
        }

        public JsonNode archive(String postId) {
            return data(http.post("/v1/posts/" + postId + "/archive"));
        }

        public JsonNode restore(String postId) {
            return data(http.post("/v1/posts/" + postId + "/restore"));
        }

        public JsonNode cancel(String postId) {
            return data(http.post("/v1/posts/" + postId + "/cancel"));
        }

        public void delete(String postId) {
            http.delete("/v1/posts/" + postId);
        }

        public JsonNode previewLink(String postId) {
            return data(http.post("/v1/posts/" + postId + "/preview-link"));
        }

        public JsonNode retryResult(String postId, String resultId) {
            return data(http.post("/v1/posts/" + postId + "/results/" + resultId + "/retry"));
        }

        public List<JsonNode> bulkCreate(List<Map<String, Object>> posts) {
            return dataList(http.post("/v1/posts/bulk", Map.of("posts", posts)));
        }
    }

    public static final class DeliveryJobsResource extends Resource {
        DeliveryJobsResource(ApiHttpClient http) { super(http); }

        public Page<JsonNode> list() {
            return list(null);
        }

        public Page<JsonNode> list(Map<String, ?> params) {
            return page(http.get("/v1/post-delivery-jobs", params));
        }

        public JsonNode summary() {
            return data(http.get("/v1/post-delivery-jobs/summary"));
        }

        public JsonNode retry(String jobId) {
            return data(http.post("/v1/post-delivery-jobs/" + jobId + "/retry"));
        }

        public JsonNode cancel(String jobId) {
            return data(http.post("/v1/post-delivery-jobs/" + jobId + "/cancel"));
        }
    }

    public static final class MediaResource extends Resource {
        MediaResource(ApiHttpClient http) { super(http); }

        public JsonNode upload(Map<String, Object> body) {
            return data(http.post("/v1/media", body));
        }

        public JsonNode get(String mediaId) {
            return data(http.get("/v1/media/" + mediaId));
        }

        public void delete(String mediaId) {
            http.delete("/v1/media/" + mediaId);
        }
    }

    public static final class AnalyticsResource extends Resource {
        AnalyticsResource(ApiHttpClient http) { super(http); }

        public JsonNode summary(Map<String, ?> params) {
            return data(http.get("/v1/analytics/summary", params));
        }

        public JsonNode trend(Map<String, ?> params) {
            return data(http.get("/v1/analytics/trend", params));
        }

        public List<JsonNode> byPlatform(Map<String, ?> params) {
            return dataList(http.get("/v1/analytics/by-platform", params));
        }

        public JsonNode rollup(Map<String, ?> params) {
            return data(http.get("/v1/analytics/rollup", params));
        }
    }

    public static final class ConnectResource extends Resource {
        ConnectResource(ApiHttpClient http) { super(http); }

        public JsonNode getConnectUrl(Map<String, ?> params) {
            return data(http.post("/v1/oauth/connect", params));
        }

        public JsonNode createSession(Map<String, Object> body) {
            return data(http.post("/v1/connect/sessions", body));
        }

        public JsonNode getSession(String sessionId) {
            return data(http.get("/v1/connect/sessions/" + sessionId));
        }
    }

    public static final class UsersResource extends Resource {
        UsersResource(ApiHttpClient http) { super(http); }

        public Page<JsonNode> list() {
            return page(http.get("/v1/users"));
        }

        public JsonNode get(String externalUserId) {
            return data(http.get("/v1/users/" + externalUserId));
        }
    }

    public static final class WebhooksResource extends Resource {
        WebhooksResource(ApiHttpClient http) { super(http); }

        public JsonNode create(Map<String, Object> body) {
            return data(http.post("/v1/webhooks", body));
        }

        public Page<JsonNode> list() {
            return page(http.get("/v1/webhooks"));
        }

        public JsonNode get(String webhookId) {
            return data(http.get("/v1/webhooks/" + webhookId));
        }

        public JsonNode update(String webhookId, Map<String, Object> body) {
            return data(http.patch("/v1/webhooks/" + webhookId, body));
        }

        public JsonNode rotate(String webhookId) {
            return data(http.post("/v1/webhooks/" + webhookId + "/rotate"));
        }

        public void delete(String webhookId) {
            http.delete("/v1/webhooks/" + webhookId);
        }
    }

    public static final class OAuthResource extends Resource {
        OAuthResource(ApiHttpClient http) { super(http); }

        public JsonNode connect(String platform) {
            return connect(platform, null);
        }

        public JsonNode connect(String platform, Map<String, ?> params) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("platform", platform);
            if (params != null) {
                body.putAll(params);
            }
            return data(http.post("/v1/oauth/connect", body));
        }
    }

    public static final class UsageResource extends Resource {
        UsageResource(ApiHttpClient http) { super(http); }

        public JsonNode get() {
            return data(http.get("/v1/usage"));
        }
    }
}

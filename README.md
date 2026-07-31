# UniPost Java SDK

Official Java client for the UniPost API.

## Latest release: v0.7.0

Inbox operations are now explicitly bound to either one managed user or the
owner/admin workspace aggregate.

- List, read, reply, thread state, media context, sync, X backfill, X reply reconciliation, and WebSocket connection details are supported.
- Reply results distinguish completed delivery from accepted-but-reconciling X delivery.
- WebSocket helpers return backend connection details without opening a connection or adding a runtime dependency.

## Install

Maven:

```xml
<dependency>
  <groupId>dev.unipost</groupId>
  <artifactId>sdk-java</artifactId>
  <version>0.7.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("dev.unipost:sdk-java:0.7.0")
```

## Quickstart

```java
import dev.unipost.UniPost;

import java.util.List;
import java.util.Map;

UniPost client = new UniPost("up_live_xxx");

var post = client.posts().create(Map.of(
    "caption", "Hello from UniPost!",
    "account_ids", List.of("sa_bluesky_123")
));

System.out.println(post.get("id").asText());
```

Or read the API key from `UNIPOST_API_KEY`:

```java
UniPost client = new UniPost();
```

## Design

The Java SDK intentionally keeps the request/response surface close to the
wire format:

- request bodies use `Map<String, Object>` with the same snake_case keys as the API
- responses are returned as Jackson `JsonNode`
- paginated endpoints return `Page<JsonNode>`

This keeps the SDK complete and stable while still feeling natural in Java.

## Included resources

- workspace
- profiles
- accounts
- platforms
- plans
- platform credentials
- api keys
- posts
- delivery jobs
- media
- analytics
- connect
- users
- webhooks
- oauth
- usage
- logs
- inbox

## Analytics Explorer

```java
Page<JsonNode> posts = client.analytics().posts(Map.of(
    "platform", "tiktok",
    "limit", 25,
    "sort", "engagement_rate"
));

List<JsonNode> platforms = client.analytics().platforms(Map.of());
JsonNode tiktok = client.analytics().platform("tiktok", Map.of());
String csv = client.analytics().exportPostsCsv(Map.of("platform", "pinterest"));

client.analytics().refresh(Map.of(
    "platform", "threads",
    "limit", 100
));
```

## Developer Logs

```java
Page<JsonNode> logs = client.logs().list(Map.of(
    "status", "error",
    "limit", 50
));

if (!logs.getData().isEmpty()) {
    JsonNode log = client.logs().get(logs.getData().get(0).path("id").asLong());
    System.out.println(log.path("action").asText());
}

try (LogStream stream = client.logs().stream(Map.of(
    "status", "error",
    "after_id", logs.getData().isEmpty() ? 0 : logs.getData().get(0).path("id").asLong() - 1
))) {
    if (stream.next()) {
        System.out.println(stream.event().path("action").asText());
    }
}
```

## Media upload

```java
var media = client.media().upload(Map.of(
    "filename", "voiceover.mp3",
    "content_type", "audio/mpeg"
    // size_bytes is optional
));

System.out.println(media.path("id").asText());
```

## Custom audio overlay

```java
var job = client.media().audioOverlays().create(Map.of(
    "video_media_id", "media_video_123",
    "audio_media_id", "media_audio_456",
    "mode", "mix",
    "fit", "trim_to_video"
), "overlay-demo-001");

while (job.path("status").asText().equals("queued") ||
       job.path("status").asText().equals("processing")) {
    Thread.sleep(1500);
    job = client.media().audioOverlays().get(job.path("id").asText());
}

if (!job.path("status").asText().equals("succeeded")) {
    throw new IllegalStateException("audio overlay failed");
}

client.posts().create(Map.of(
    "caption", "Video with custom audio",
    "account_ids", List.of("sa_tiktok_xxx"),
    "media_ids", List.of(job.path("output_media_id").asText())
));
```

## Get Connect URL (Your Own Accounts)

```java
var connect = client.connect().getConnectUrl(Map.of(
    "profile_id", "pr_brand_us",
    "platform", "linkedin",
    "redirect_url", "https://app.acme.com/integrations/done" // optional
));

System.out.println(connect.get("auth_url").asText());
```

## Connect (Managed Users)

```java
var request = new java.util.HashMap<>(Map.of(
    "platform", "twitter",
    "external_user_id", "your_user_123",
    "return_url", "https://yourapp.com/callback"
)));
request.put("allow_quickstart_creds", true); // optional

var session = client.connect().createSession(request);

System.out.println(session.get("url").asText());
```

## Production Inbox integration

Keep the UniPost workspace API key in a server-side secret store. Never return
it to a managed user or embed it in browser JavaScript, a mobile app, a
WebSocket URL, logs, or exception telemetry. Derive the stable external user ID
from your app's authenticated session, rather than accepting an arbitrary scope
field from a request:

```java
import dev.unipost.Inbox;

String externalUserId = authenticatedSession.getUserId();
Inbox.Scoped managedInbox = client.inbox().managedUser(externalUserId);
```

`managedUser(...)` rejects blank IDs and never falls back to workspace access.
Use `client.inbox().workspace()` only for an explicit aggregate workflow. The
UniPost API allows workspace scope only while the workspace API key's creator
is a workspace owner or admin. That creator role is separate from roles in your
own application; an app-level admin must not automatically receive UniPost
workspace access.

### Read and manage a scoped Inbox

`list(...)` accepts exactly `source`, `is_read`, `is_own`, and `limit`.
Explicit `false` values are sent. The result is one limit-only list, not a
paginated collection: there is no cursor, offset, next token, or total. An
omitted, invalid, zero, or negative limit uses the server default of 50; values
above 500 are clamped to 500.

```java
List<JsonNode> items = managedInbox.list(Map.of(
    "source", "x_dm",
    "is_read", false,
    "is_own", false,
    "limit", 25
));

JsonNode unread = managedInbox.unreadCount();
if (!items.isEmpty()) {
    JsonNode item = managedInbox.get(items.get(0).path("id").asText());
    managedInbox.markRead(item.path("id").asText());
    JsonNode updated = managedInbox.updateThreadState(item.path("id").asText(), Map.of(
        "thread_status", "assigned",
        "assigned_to", "owner_123"
    ));
    JsonNode media = managedInbox.mediaContext(item.path("id").asText());
}
JsonNode marked = managedInbox.markAllRead();
```

### Reply once and reconcile X delivery

Create one stable idempotency key for each logical X reply and reuse that same
key if your job retries. Never resend a reconciling reply under a new key. HTTP
`200` produces `COMPLETED`; a valid HTTP `202` produces `RECONCILING`, meaning
X accepted the reply while UniPost is still reconciling it.

```java
Inbox.ReplyResult reply = managedInbox.reply(
    "inbox_item_123",
    Map.of("text", "Thanks—we are looking into this."),
    "reply-order-8721-comment-4"
);

if (reply.getState() == Inbox.ReplyState.COMPLETED) {
    System.out.println(reply.getItem().path("id").asText());
} else {
    JsonNode status = managedInbox.xOutboundStatus(reply.getOperationId());
    System.out.println(status.path("status").asText());
}
```

### Backend WebSocket connection details

`webSocketConnectionDetails()` is local-only: it makes no network request and
adds no WebSocket dependency. It returns a scoped `ws://` or `wss://` URL and
keeps the API key only in the `Authorization` header map.

```java
Inbox.WebSocketConnectionDetails details = managedInbox.webSocketConnectionDetails();
// Pass details.getUrl() and details.getHeaders() to a trusted backend WebSocket client.
```

Never log the returned header or put the key in the URL. Native browser
WebSocket clients cannot attach the required authorization header; terminate
or proxy the connection through your authenticated backend.

### Ordinary sync and metered X backfill

`sync()` performs ordinary selected-scope polling without a request body.
`syncXBackfill(...)` is a separate metered operation. Managed-user scope limits
eligible accounts to that managed user; workspace scope can span the workspace.
Review the estimate, selected scope, and X credit cost before confirmation.
Never schedule an unreviewed workspace-wide backfill.

```java
JsonNode ordinary = managedInbox.sync();

Map<String, Object> request = Map.of(
    "account_id", "sa_x_123",
    "lookback_days", 7,
    "max_items", 100,
    "include_replies", true,
    "include_dms", false
);
JsonNode estimate = managedInbox.syncXBackfill(request);

if (estimate.path("confirmation_required").asBoolean()) {
    String confirmationToken = estimate.path("confirmation_token").asText();
    Map<String, Object> confirmedRequest = new java.util.LinkedHashMap<>(request);
    confirmedRequest.put("confirmation_token", confirmationToken);
    JsonNode confirmed = managedInbox.syncXBackfill(confirmedRequest);
}
```

Treat the optional confirmation token as a short-lived secret: do not log it,
send it to a browser, or store it in client-visible state.

## Webhook verification

```java
import dev.unipost.WebhookVerifier;

boolean ok = WebhookVerifier.verifySignature(secret, payload, signatureHeader);
```

## Publishing

The repo is set up for Maven Central style releases through Gradle:

- `./gradlew test` validates the SDK locally
- `./gradlew publishToMavenLocal` installs a release candidate for source validation
- pushing a `vX.Y.Z` tag triggers `.github/workflows/publish.yml`

Required GitHub secrets for release:

- `MAVEN_CENTRAL_DEPLOY_URL`
- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_SIGNING_KEY`
- `MAVEN_SIGNING_PASSWORD`

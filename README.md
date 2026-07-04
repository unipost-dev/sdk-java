# UniPost Java SDK

Official Java client for the UniPost API.

## Latest release: v0.5.0

Media uploads now support custom audio overlay jobs and optional reserve-time
file sizes.

- Use `client.media().audioOverlays().create(...)` to combine one uploaded video with one uploaded audio file.
- Poll the job with `client.media().audioOverlays().get(...)`, then publish the returned `output_media_id`.
- Omit `size_bytes` when reserving media if your app cannot know the raw file length up front.
- Post failure responses also include the typed v0.4.1 error contract fields.

## Install

Maven:

```xml
<dependency>
  <groupId>dev.unipost</groupId>
  <artifactId>sdk-java</artifactId>
  <version>0.5.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("dev.unipost:sdk-java:0.5.0")
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

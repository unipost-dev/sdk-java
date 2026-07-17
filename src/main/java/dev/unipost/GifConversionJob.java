package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;

public final class GifConversionJob {
    private final String id;
    private final String kind;
    private final String status;
    private final String gifMediaId;
    private final String backgroundColor;
    private final String outputProfile;
    private final String outputMediaId;
    private final String createdAt;
    private final String startedAt;
    private final String completedAt;
    private final GifConversionJobError error;

    private GifConversionJob(JsonNode node) {
        this.id = text(node, "id");
        this.kind = text(node, "kind");
        this.status = text(node, "status");
        this.gifMediaId = text(node, "gif_media_id");
        this.backgroundColor = text(node, "background_color");
        this.outputProfile = text(node, "output_profile");
        this.outputMediaId = text(node, "output_media_id");
        this.createdAt = text(node, "created_at");
        this.startedAt = text(node, "started_at");
        this.completedAt = text(node, "completed_at");
        JsonNode errorNode = node.get("error");
        this.error = errorNode == null || errorNode.isNull() ? null : new GifConversionJobError(
                text(errorNode, "code"), text(errorNode, "message"), errorNode.path("retryable").asBoolean(false));
    }

    static GifConversionJob fromJson(JsonNode node) { return new GifConversionJob(node); }

    private static String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public String getGifMediaId() { return gifMediaId; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getOutputProfile() { return outputProfile; }
    public String getOutputMediaId() { return outputMediaId; }
    public String getCreatedAt() { return createdAt; }
    public String getStartedAt() { return startedAt; }
    public String getCompletedAt() { return completedAt; }
    public GifConversionJobError getError() { return error; }
}

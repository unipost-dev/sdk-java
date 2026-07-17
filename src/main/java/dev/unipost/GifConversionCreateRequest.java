package dev.unipost;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GifConversionCreateRequest {
    private final String gifMediaId;
    private final String backgroundColor;

    public GifConversionCreateRequest(String gifMediaId, String backgroundColor) {
        this.gifMediaId = Objects.requireNonNull(gifMediaId, "gifMediaId");
        this.backgroundColor = backgroundColor;
    }

    public String getGifMediaId() { return gifMediaId; }
    public String getBackgroundColor() { return backgroundColor; }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gif_media_id", gifMediaId);
        if (backgroundColor != null) body.put("background_color", backgroundColor);
        return body;
    }
}

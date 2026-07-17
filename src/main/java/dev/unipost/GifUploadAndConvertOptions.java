package dev.unipost;

import java.time.Duration;

public final class GifUploadAndConvertOptions {
    private final String backgroundColor;
    private final String idempotencyKey;
    private final Duration pollInterval;
    private final Duration timeout;

    public GifUploadAndConvertOptions(
            String backgroundColor,
            String idempotencyKey,
            Duration pollInterval,
            Duration timeout
    ) {
        this.backgroundColor = backgroundColor;
        this.idempotencyKey = idempotencyKey;
        this.pollInterval = pollInterval;
        this.timeout = timeout;
    }

    public String getBackgroundColor() { return backgroundColor; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Duration getPollInterval() { return pollInterval; }
    public Duration getTimeout() { return timeout; }
}

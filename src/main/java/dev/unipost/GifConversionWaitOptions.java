package dev.unipost;

import java.time.Duration;
import java.util.Objects;

public final class GifConversionWaitOptions {
    public static final GifConversionWaitOptions DEFAULTS =
            new GifConversionWaitOptions(Duration.ofSeconds(2), Duration.ofMinutes(5));

    private final Duration pollInterval;
    private final Duration timeout;

    public GifConversionWaitOptions(Duration pollInterval, Duration timeout) {
        this.pollInterval = positive(Objects.requireNonNull(pollInterval, "pollInterval"), "pollInterval");
        this.timeout = positive(Objects.requireNonNull(timeout, "timeout"), "timeout");
    }

    private static Duration positive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public Duration getPollInterval() { return pollInterval; }
    public Duration getTimeout() { return timeout; }
}

package dev.unipost;

public final class GifConversionTimeoutException extends RuntimeException {
    public GifConversionTimeoutException(String conversionId) {
        super("Timed out waiting for GIF conversion " + conversionId);
    }
}

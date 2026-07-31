package dev.unipost;

import com.fasterxml.jackson.databind.JsonNode;

public class APIError extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final String requestId;
    private final String responseBody;
    private final JsonNode details;
    private final Boolean retriable;
    private final Integer retryAfterSeconds;

    public APIError(int statusCode, String code, String message, String requestId, String responseBody) {
        this(statusCode, code, message, requestId, responseBody, null, null, null);
    }

    public APIError(
            int statusCode,
            String code,
            String message,
            String requestId,
            String responseBody,
            JsonNode details,
            Boolean retriable,
            Integer retryAfterSeconds
    ) {
        super(message == null || message.isBlank() ? "UniPost API request failed" : message);
        this.statusCode = statusCode;
        this.code = code;
        this.requestId = requestId;
        this.responseBody = responseBody;
        this.details = details == null ? null : details.deepCopy();
        this.retriable = retriable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public JsonNode getDetails() {
        return details == null ? null : details.deepCopy();
    }

    public Boolean isRetriable() {
        return retriable;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

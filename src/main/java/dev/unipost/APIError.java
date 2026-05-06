package dev.unipost;

public class APIError extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final String requestId;
    private final String responseBody;

    public APIError(int statusCode, String code, String message, String requestId, String responseBody) {
        super(message == null || message.isBlank() ? "UniPost API request failed" : message);
        this.statusCode = statusCode;
        this.code = code;
        this.requestId = requestId;
        this.responseBody = responseBody;
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
}

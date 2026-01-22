package com.assignment.draftly.exceptionHandler;

public class GmailApiException extends RuntimeException {

    private final int statusCode;
    private final String errorBody;

    public GmailApiException(String message, int statusCode, String errorBody) {
        super(message);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorBody() {
        return errorBody;
    }
}





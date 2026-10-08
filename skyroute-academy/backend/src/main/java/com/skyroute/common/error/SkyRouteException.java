package com.skyroute.common.error;

import org.springframework.http.HttpStatus;

/**
 * Root of the domain exception hierarchy. Every subclass carries a stable,
 * machine-readable error code and the HTTP status it maps to, so the global
 * handler never needs an if/else ladder (Open/Closed principle).
 */
public abstract class SkyRouteException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected SkyRouteException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

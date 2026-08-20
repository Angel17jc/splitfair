package com.expensesplit.exception;

import lombok.Getter;

/**
 * Se supero el cupo de peticiones. Se traduce a HTTP 429.
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    /** Segundos que el cliente deberia esperar antes de reintentar. */
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

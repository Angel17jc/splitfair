package com.expensesplit.exception;

/**
 * El usuario esta autenticado pero no tiene derecho sobre el recurso.
 * Se traduce a HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

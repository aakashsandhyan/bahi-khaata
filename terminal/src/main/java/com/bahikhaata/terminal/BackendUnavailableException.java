package com.bahikhaata.terminal;

/**
 * The backend could not be reached, or answered in a way that means it cannot serve.
 *
 * <p>Deliberately one exception for both cases. A backend that is not listening and a
 * backend that reports itself unusable are the same thing to a cashier — the terminal
 * cannot take a sale — and collapsing them here stops calling code from accidentally
 * treating "answered with 503" as good enough to proceed.
 */
public class BackendUnavailableException extends RuntimeException {

    public BackendUnavailableException(String message) {
        super(message);
    }

    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

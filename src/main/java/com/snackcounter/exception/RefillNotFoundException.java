package com.snackcounter.exception;

/** No refill with that id. Mapped to HTTP 404. */
public class RefillNotFoundException extends RuntimeException {

    public RefillNotFoundException(long id) {
        super("No refill #" + id);
    }
}

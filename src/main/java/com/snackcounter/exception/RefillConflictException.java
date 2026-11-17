package com.snackcounter.exception;

/** Someone else got there first, or the refill has moved on (already committed, already refilled). HTTP 409. */
public class RefillConflictException extends RuntimeException {

    public RefillConflictException(String message) {
        super(message);
    }
}

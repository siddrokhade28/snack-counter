package com.snackcounter.exception;

/** The caller may not do this to this refill (not the assignee, not an admin of that office). HTTP 403. */
public class NotAllowedException extends RuntimeException {

    public NotAllowedException(String message) {
        super(message);
    }
}

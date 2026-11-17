package com.snackcounter.client;

/** HR could not take the claim right now (down, timing out). The claim stays queued and is retried. */
public class HrUnavailableException extends RuntimeException {

    public HrUnavailableException(String message) {
        super(message);
    }
}

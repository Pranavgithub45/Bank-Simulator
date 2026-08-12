package com.simulator.dhanlaxmi.exception;

public class InvalidMercodeException extends RuntimeException {
    public InvalidMercodeException(String mercode) {
        super("Invalid mercode: " + mercode);
    }
}

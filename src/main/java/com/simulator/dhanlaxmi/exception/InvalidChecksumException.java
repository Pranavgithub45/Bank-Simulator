package com.simulator.dhanlaxmi.exception;

public class InvalidChecksumException extends RuntimeException {
    public InvalidChecksumException() {
        super("Checksum validation failed");
    }
}

package com.simulator.dhanlaxmi.exception;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String prn) {
        super("Duplicate transaction - PRN already exists: " + prn);
    }
}

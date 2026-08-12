package com.simulator.dhanlaxmi.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String prn) {
        super("No transaction found for PRN: " + prn);
    }
}

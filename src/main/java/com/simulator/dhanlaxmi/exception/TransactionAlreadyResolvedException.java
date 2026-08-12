package com.simulator.dhanlaxmi.exception;

public class TransactionAlreadyResolvedException extends RuntimeException {
    public TransactionAlreadyResolvedException(String prn, String currentStatus) {
        super("Transaction " + prn + " is already resolved with status " + currentStatus);
    }
}

package com.simulator.dhanlaxmi.model;

/**
 * Controls HOW the callback is delivered, kept separate from the payment
 * OUTCOME (TransactionStatus) - per the original spec, these are two
 * independent axes: a transaction can be SUCCESS + DROPPED just as
 * easily as FAILURE + NORMAL.
 */
public enum CallbackBehavior {
    NORMAL,
    DELAY,
    DROP,
    DUPLICATE
}

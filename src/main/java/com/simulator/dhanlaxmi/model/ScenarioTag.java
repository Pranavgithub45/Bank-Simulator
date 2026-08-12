package com.simulator.dhanlaxmi.model;

/**
 * Tags a dummy bank account with the outcome it should produce once it
 * has already passed the two generic checks every account goes through:
 * (1) does the account exist, (2) does it have enough balance. Those two
 * are computed dynamically per transaction, NOT tagged on the account -
 * see PaymentService.completePayment(). This tag only covers outcomes
 * that can't be derived that way.
 */
public enum ScenarioTag {
    SUCCESS,
    TIMEOUT,
    GENERIC_FAILURE,
    PENDING
}

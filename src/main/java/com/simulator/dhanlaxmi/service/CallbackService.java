package com.simulator.dhanlaxmi.service;

import com.simulator.dhanlaxmi.model.CallbackBehavior;
import com.simulator.dhanlaxmi.model.Transaction;

public interface CallbackService {

    /**
     * Builds the encrypted response and delivers it to the transaction's
     * return URL according to the given behavior. Updates and persists
     * the transaction's callbackStatus as a side effect.
     */
    void sendCallback(Transaction transaction, CallbackBehavior behavior);
}

package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.model.*;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.service.CallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deliberately uses invalid/unreachable return URLs so these tests never
 * depend on network access - DROP should never even attempt a call, and
 * NORMAL against a bad URL should land on FAILED via the caught exception.
 */
@SpringBootTest
@Transactional
class CallbackServiceTests {

    @Autowired
    private CallbackService callbackService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BankProperties bankProperties;

    private Transaction newTransaction(String prn, String returnUrl) {
        Transaction transaction = new Transaction();
        transaction.setPrn(prn);
        transaction.setBid("SIMTEST" + prn);
        transaction.setMerchantCode(bankProperties.getMerchantCode());
        transaction.setMerchantName("Test Merchant");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("INR");
        transaction.setReturnUrl(returnUrl);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setCallbackStatus(CallbackStatus.NOT_SENT);
        return transactionRepository.save(transaction);
    }

    @Test
    void dropNeverAttemptsDeliveryAndMarksDropped() {
        // Deliberately garbage URL - if DROP tried to call it, this would throw
        Transaction transaction = newTransaction("CBTEST001", "not-a-real-url");

        callbackService.sendCallback(transaction, CallbackBehavior.DROP);

        assertEquals(CallbackStatus.DROPPED, transaction.getCallbackStatus());
    }

    @Test
    void normalAgainstUnreachableUrlEndsUpFailed() {
        Transaction transaction = newTransaction("CBTEST002", "http://localhost:1/does-not-exist");

        callbackService.sendCallback(transaction, CallbackBehavior.NORMAL);

        assertEquals(CallbackStatus.FAILED, transaction.getCallbackStatus());
    }
}

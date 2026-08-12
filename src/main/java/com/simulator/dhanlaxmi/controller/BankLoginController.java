package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.model.BankAccount;
import com.simulator.dhanlaxmi.model.CallbackBehavior;
import com.simulator.dhanlaxmi.model.ScenarioTag;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.model.TransactionStatus;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import com.simulator.dhanlaxmi.service.CallbackService;
import com.simulator.dhanlaxmi.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands in for the customer authentication + payment authorization
 * screens (spec sections 23-24) - no login UI, just pick which account
 * number to "log in" with. The outcome is computed generically by
 * PaymentService (invalid account / insufficient balance / scenario tag).
 * Once resolved, the callback is sent automatically (skipped for PENDING,
 * per spec section 25 - pending transactions have no callback until
 * something else resolves them).
 *
 * prn, accountNo, and the optional callbackBehavior override all travel
 * in the JSON body, not the URL - keeps them out of server access logs,
 * proxy logs, and browser history.
 *
 * Example:
 *   POST /bank/complete-payment
 *   { "prn": "BLDK000123", "accountNo": "1111000011" }
 *
 * Optional override, e.g. to force a dropped callback for testing
 * Double Verification regardless of which account was used:
 *   { "prn": "BLDK000123", "accountNo": "1111000011", "callbackBehavior": "DROP" }
 *
 * If callbackBehavior is omitted, it defaults to NORMAL - except for
 * accounts tagged TIMEOUT, which default to DELAY automatically.
 */
@RestController
public class BankLoginController {

    private final PaymentService paymentService;
    private final CallbackService callbackService;
    private final BankAccountRepository bankAccountRepository;

    public BankLoginController(PaymentService paymentService,
                                CallbackService callbackService,
                                BankAccountRepository bankAccountRepository) {
        this.paymentService = paymentService;
        this.callbackService = callbackService;
        this.bankAccountRepository = bankAccountRepository;
    }

    public record CompletePaymentRequest(String prn, String accountNo, String callbackBehavior) {}

    @PostMapping("/bank/complete-payment")
    public ResponseEntity<Map<String, String>> completePayment(@RequestBody CompletePaymentRequest request) {
        Transaction transaction = paymentService.completePayment(request.prn(), request.accountNo());

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            callbackService.sendCallback(transaction, resolveCallbackBehavior(request));
        }

        Map<String, String> response = new LinkedHashMap<>();
        response.put("prn", transaction.getPrn());
        response.put("accountNo", request.accountNo());
        response.put("status", transaction.getStatus().name());
        response.put("failureReason", transaction.getFailureReason());
        response.put("callbackStatus", transaction.getCallbackStatus().name());
        return ResponseEntity.ok(response);
    }

    private CallbackBehavior resolveCallbackBehavior(CompletePaymentRequest request) {
        if (request.callbackBehavior() != null && !request.callbackBehavior().isBlank()) {
            return CallbackBehavior.valueOf(request.callbackBehavior().toUpperCase());
        }

        boolean isTimeoutAccount = bankAccountRepository.findByAccountNo(request.accountNo())
                .map(BankAccount::getScenarioTag)
                .map(tag -> tag == ScenarioTag.TIMEOUT)
                .orElse(false);

        return isTimeoutAccount ? CallbackBehavior.DELAY : CallbackBehavior.NORMAL;
    }
}


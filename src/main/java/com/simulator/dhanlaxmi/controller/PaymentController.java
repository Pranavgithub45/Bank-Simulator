package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The real bank contract endpoint. BillDesk POSTs mercode + encDhanBankData
 * here exactly as documented in the spec (form-urlencoded, matching how
 * BillDesk actually sends it).
 */
@RestController
@RequestMapping("/Corporate/prelogin/payment-gateway")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receivePayment(@RequestParam("mercode") String mercode,
                                                                @RequestParam("encDhanBankData") String encDhanBankData) {
        Transaction transaction = paymentService.processPaymentRequest(mercode, encDhanBankData);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("prn", transaction.getPrn());
        response.put("bid", transaction.getBid());
        response.put("status", transaction.getStatus().name());
        response.put("message", "Transaction received. Complete it with POST /bank/complete-payment "
                + "and JSON body {\"prn\": \"" + transaction.getPrn() + "\", \"accountNo\": \"<account>\"}");
        return ResponseEntity.ok(response);
    }
}

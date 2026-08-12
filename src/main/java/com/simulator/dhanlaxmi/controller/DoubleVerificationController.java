package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.service.DoubleVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/Corporate/prelogin/payment-gateway/paymentDoubleVerification"
)
public class DoubleVerificationController {

    private final DoubleVerificationService verificationService;

    public DoubleVerificationController(
            DoubleVerificationService verificationService) {

        this.verificationService =
                verificationService;
    }

    public record DoubleVerificationRequest(
            String mercode,
            String encDhanBankData
    ) {
    }

    public record DoubleVerificationResponse(
            String statusCode,
            String statusDescription,
            String verificationResponse
    ) {
    }

    @PostMapping
    public ResponseEntity<DoubleVerificationResponse> verify(
            @RequestBody DoubleVerificationRequest request) {

        DoubleVerificationService.VerificationResult result =
                verificationService.verifyRequest(
                        request.mercode(),
                        request.encDhanBankData()
                );

        return ResponseEntity.ok(
                new DoubleVerificationResponse(
                        result.statusCode(),
                        result.statusDescription(),
                        result.verificationResponse()
                )
        );
    }
}
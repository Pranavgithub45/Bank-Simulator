package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.crypto.ChecksumService;
import com.simulator.dhanlaxmi.crypto.EncryptionService;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.util.ParamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/billdesk")
public class BillDeskCallbackReceiverController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BillDeskCallbackReceiverController.class
            );

    private final EncryptionService encryptionService;
    private final ChecksumService checksumService;
    private final TransactionRepository transactionRepository;

    private final RestTemplate restTemplate;

    private final String doubleVerificationUrl;

    public BillDeskCallbackReceiverController(
            EncryptionService encryptionService,
            ChecksumService checksumService,
            TransactionRepository transactionRepository,
            @Value("${server.port:8082}") int serverPort) {

        this.encryptionService =
                encryptionService;

        this.checksumService =
                checksumService;

        this.transactionRepository =
                transactionRepository;

        this.restTemplate =
                new RestTemplate();

        this.doubleVerificationUrl =
                "http://localhost:"
                        + serverPort
                        + "/Corporate/prelogin/payment-gateway/paymentDoubleVerification";
    }

    @PostMapping("/callback-receiver")
    public ResponseEntity<Map<String, String>> receiveCallback(
            @RequestParam("mercode") String mercode,
            @RequestParam("encDhanBankData") String encDhanBankData) {

        Map<String, String> response =
                new LinkedHashMap<>();

        response.put(
                "received",
                "true"
        );

        try {

            // -----------------------------------------------------
            // 1. DECRYPT CALLBACK
            // -----------------------------------------------------
            String decrypted =
                    encryptionService.decrypt(
                            encDhanBankData
                    );

            // -----------------------------------------------------
            // 2. PARSE CALLBACK
            // -----------------------------------------------------
            Map<String, String> params =
                    ParamCodec.parse(
                            decrypted
                    );

            // -----------------------------------------------------
            // 3. GET CALLBACK CHECKSUM
            // -----------------------------------------------------
            String receivedChecksum =
                    params.remove(
                            "CHECKSUM"
                    );

            // -----------------------------------------------------
            // 4. VALIDATE CALLBACK CHECKSUM
            // -----------------------------------------------------
            String recreatedPlainText =
                    ParamCodec.build(
                            params
                    );

            boolean checksumValid =
                    checksumService.verifyChecksum(
                            recreatedPlainText,
                            receivedChecksum
                    );

            String prn =
                    params.get("PRN");

            String bid =
                    params.get("BID");

            String paid =
                    params.get("PAID");

            log.info(
                    "Callback received: mercode={}, PRN={}, BID={}, PAID={}, checksumValid={}",
                    mercode,
                    prn,
                    bid,
                    paid,
                    checksumValid
            );

            response.put(
                    "checksumValid",
                    String.valueOf(
                            checksumValid
                    )
            );

            // -----------------------------------------------------
            // 5. ONLY VALID CALLBACKS TRIGGER DOUBLE VERIFICATION
            // -----------------------------------------------------
            if (!checksumValid) {

                log.warn(
                        "Double Verification NOT triggered because callback checksum is invalid. PRN={}",
                        prn
                );

                response.put(
                        "doubleVerification",
                        "NOT_TRIGGERED_INVALID_CHECKSUM"
                );

                return ResponseEntity.ok(
                        response
                );
            }

            // -----------------------------------------------------
            // 6. FIND TRANSACTION
            // -----------------------------------------------------
            if (prn == null || prn.isBlank()) {

                log.warn(
                        "Double Verification skipped because PRN is missing."
                );

                response.put(
                        "doubleVerification",
                        "SKIPPED_NO_PRN"
                );

                return ResponseEntity.ok(
                        response
                );
            }

            if (transactionRepository
                    .findByPrn(prn)
                    .isEmpty()) {

                log.warn(
                        "Double Verification skipped because transaction does not exist. PRN={}",
                        prn
                );

                response.put(
                        "doubleVerification",
                        "SKIPPED_TRANSACTION_NOT_FOUND"
                );

                return ResponseEntity.ok(
                        response
                );
            }

            // -----------------------------------------------------
            // 7. AUTOMATIC SERVER -> SERVER DOUBLE VERIFICATION
            // -----------------------------------------------------
            invokeDoubleVerificationApi(
                    mercode,
                    encDhanBankData,
                    prn
            );

            response.put(
                    "doubleVerification",
                    "TRIGGERED"
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (Exception e) {

            log.error(
                    "Could not process callback.",
                    e
            );

            response.put(
                    "checksumValid",
                    "false"
            );

            response.put(
                    "doubleVerification",
                    "NOT_TRIGGERED"
            );

            return ResponseEntity.ok(
                    response
            );
        }
    }

    /**
     * Calls the Double Verification endpoint internally.
     *
     * This is SERVER -> SERVER.
     * The browser is not involved.
     */
    private void invokeDoubleVerificationApi(
            String mercode,
            String encDhanBankData,
            String prn) {

        try {

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            Map<String, String> requestBody =
                    new LinkedHashMap<>();

            requestBody.put(
                    "mercode",
                    mercode
            );

            requestBody.put(
                    "encDhanBankData",
                    encDhanBankData
            );

            HttpEntity<Map<String, String>> request =
                    new HttpEntity<>(
                            requestBody,
                            headers
                    );

            log.info(
                    "=================================================="
            );

            log.info(
                    "AUTOMATIC DOUBLE VERIFICATION START"
            );

            log.info(
                    "PRN={}",
                    prn
            );

            log.info(
                    "POST {}",
                    doubleVerificationUrl
            );

            ResponseEntity<String> verificationResponse =
                    restTemplate.postForEntity(
                            doubleVerificationUrl,
                            request,
                            String.class
                    );

            log.info(
                    "Double Verification HTTP Status: {}",
                    verificationResponse
                            .getStatusCode()
                            .value()
            );

            log.info(
                    "Double Verification Response: {}",
                    verificationResponse.getBody()
            );

            log.info(
                    "AUTOMATIC DOUBLE VERIFICATION END"
            );

            log.info(
                    "=================================================="
            );

        } catch (Exception e) {

            log.error(
                    "Automatic Double Verification API failed. PRN={}, URL={}",
                    prn,
                    doubleVerificationUrl,
                    e
            );
        }
    }
}
package com.simulator.dhanlaxmi.service;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.ChecksumService;
import com.simulator.dhanlaxmi.crypto.EncryptionService;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.model.TransactionStatus;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.util.ParamCodec;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DoubleVerificationService {

    /**
     * Result returned by the Double Verification service.
     */
    public record VerificationResult(
            String statusCode,
            String statusDescription,
            String verificationResponse
    ) {
    }

    private final EncryptionService encryptionService;
    private final ChecksumService checksumService;
    private final BankProperties bankProperties;
    private final TransactionRepository transactionRepository;

    public DoubleVerificationService(
            EncryptionService encryptionService,
            ChecksumService checksumService,
            BankProperties bankProperties,
            TransactionRepository transactionRepository) {

        this.encryptionService = encryptionService;
        this.checksumService = checksumService;
        this.bankProperties = bankProperties;
        this.transactionRepository = transactionRepository;
    }

    /**
     * This method is used by the Double Verification HTTP endpoint.
     *
     * Endpoint:
     * POST /Corporate/prelogin/payment-gateway/paymentDoubleVerification
     */
    public VerificationResult verifyRequest(
            String mercode,
            String encDhanBankData) {

        // ---------------------------------------------------------
        // 1. Validate merchant code
        // ---------------------------------------------------------
        if (mercode == null
                || !bankProperties.getMerchantCode().equals(mercode)) {

            return new VerificationResult(
                    "001",
                    "Invalid mercode",
                    null
            );
        }

        // ---------------------------------------------------------
        // 2. Decrypt request
        // ---------------------------------------------------------
        final String decrypted;

        try {

            decrypted =
                    encryptionService.decrypt(
                            encDhanBankData
                    );

        } catch (Exception e) {

            return new VerificationResult(
                    "002",
                    "Decryption failed",
                    null
            );
        }

        // ---------------------------------------------------------
        // 3. Parse parameters
        // ---------------------------------------------------------
        Map<String, String> params;

        try {

            params =
                    ParamCodec.parse(
                            decrypted
                    );

        } catch (Exception e) {

            return new VerificationResult(
                    "002",
                    "Invalid request data",
                    null
            );
        }

        // ---------------------------------------------------------
        // 4. Validate checksum
        // ---------------------------------------------------------
        String receivedChecksum =
                params.remove("CHECKSUM");

        String recreatedPlainText =
                ParamCodec.build(params);

        boolean checksumValid =
                checksumService.verifyChecksum(
                        recreatedPlainText,
                        receivedChecksum
                );

        if (!checksumValid) {

            return new VerificationResult(
                    "003",
                    "Checksum validation failed",
                    null
            );
        }

        // ---------------------------------------------------------
        // 5. Get PRN
        // ---------------------------------------------------------
        String prn =
                params.get("PRN");

        if (prn == null || prn.isBlank()) {

            return new VerificationResult(
                    "004",
                    "PRN missing",
                    null
            );
        }

        // ---------------------------------------------------------
        // 6. Verify transaction
        // ---------------------------------------------------------
        return verifyTransaction(prn);
    }

    /**
     * Performs Double Verification against an existing transaction.
     *
     * IMPORTANT:
     * This method is read-only.
     * It does not modify transaction state.
     */
    public VerificationResult verifyTransaction(
            String prn) {

        return transactionRepository
                .findByPrn(prn)
                .map(this::buildVerificationResponse)
                .orElseGet(() ->
                        new VerificationResult(
                                "004",
                                "Transaction not found",
                                null
                        )
                );
    }

    /**
     * Builds the encrypted Bank Double Verification response.
     */
    private VerificationResult buildVerificationResponse(
            Transaction transaction) {

        TransactionStatus status =
                transaction.getStatus();

        boolean success =
                status == TransactionStatus.SUCCESS;

        boolean processing =
                status == TransactionStatus.RECEIVED;

        String statusCode;

        String statusDescription;

        if (success) {

            statusCode = "000";
            statusDescription = "Success";

        } else if (processing) {

            statusCode = "001";
            statusDescription = "Processing";

        } else {

            statusCode = "001";
            statusDescription = "Failure";
        }

        // ---------------------------------------------------------
        // Build response parameters
        // ---------------------------------------------------------
        Map<String, String> params =
                new LinkedHashMap<>();

        params.put(
                "PRN",
                transaction.getPrn() != null
                        ? transaction.getPrn()
                        : ""
        );

        params.put(
                "BID",
                transaction.getBid() != null
                        ? transaction.getBid()
                        : ""
        );

        params.put(
                "ACCOUNTNO",
                transaction.getAccountNo() != null
                        ? transaction.getAccountNo()
                        : ""
        );

        params.put(
                "PAID",
                success
                        ? "Y"
                        : "N"
        );

        params.put(
                "AMT",
                transaction.getAmount() != null
                        ? transaction.getAmount().toPlainString()
                        : ""
        );

        params.put(
                "PAYDATE",
                LocalDate.now().toString()
        );

        params.put(
                "REMNAME",
                transaction.getMerchantName() != null
                        ? transaction.getMerchantName()
                        : ""
        );

        params.put(
                "PAYREFNO",
                transaction.getBid() != null
                        ? transaction.getBid()
                        : ""
        );

        if (transaction.getFailureReason() != null
                && !transaction.getFailureReason().isBlank()) {

            params.put(
                    "REASON",
                    transaction.getFailureReason()
            );
        }

        // ---------------------------------------------------------
        // Generate checksum
        // ---------------------------------------------------------
        String plainData =
                ParamCodec.build(params);

        String checksum =
                checksumService.generateChecksum(
                        plainData
                );

        String finalPlainText =
                plainData
                        + "&CHECKSUM="
                        + checksum;

        // ---------------------------------------------------------
        // Encrypt response
        // ---------------------------------------------------------
        String encryptedResponse =
                encryptionService.encrypt(
                        finalPlainText
                );

        return new VerificationResult(
                statusCode,
                statusDescription,
                encryptedResponse
        );
    }
}
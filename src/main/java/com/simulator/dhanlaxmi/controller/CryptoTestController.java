package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.ChecksumService;
import com.simulator.dhanlaxmi.crypto.EncryptionService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEST-ONLY endpoints to exercise the Phase 3 crypto layer directly over
 * HTTP, without needing the real payment endpoint (that's Phase 4).
 * Not part of the real bank contract - safe to delete once the payment
 * flow is built and you're testing end to end instead.
 */
@RestController
@RequestMapping("/test")
public class CryptoTestController {

    private final ChecksumService checksumService;
    private final EncryptionService encryptionService;
    private final BankProperties bankProperties;

    public CryptoTestController(ChecksumService checksumService,
                                 EncryptionService encryptionService,
                                 BankProperties bankProperties) {
        this.checksumService = checksumService;
        this.encryptionService = encryptionService;
        this.bankProperties = bankProperties;
    }

    public record TextRequest(String text) {}

    @PostMapping("/checksum")
    public Map<String, String> checksum(@RequestBody TextRequest request) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("plainText", request.text());
        response.put("checksum", checksumService.generateChecksum(request.text()));
        return response;
    }

    @PostMapping("/encrypt")
    public Map<String, String> encrypt(@RequestBody TextRequest request) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("plainText", request.text());
        response.put("encrypted", encryptionService.encrypt(request.text()));
        return response;
    }

    @PostMapping("/decrypt")
    public Map<String, String> decrypt(@RequestBody TextRequest request) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("encrypted", request.text());
        response.put("decrypted", encryptionService.decrypt(request.text()));
        return response;
    }

    /**
     * Builds a full, ready-to-use sample: plaintext -> checksum -> appended
     * -> encrypted, using the bank's own sample parameter values. Copy the
     * "encrypted" field straight into Phase 4's payment endpoint once it
     * exists, or paste it into /test/decrypt right now to see the full
     * round trip.
     */
    @GetMapping("/sample-payment-payload")
    public Map<String, String> samplePaymentPayload() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action.ShoppingMall.Login.Init", "Y");
        params.put("BankId", bankProperties.getBankId());
        params.put("MD", "P");
        params.put("PID", bankProperties.getMerchantCode());
        params.put("ITC", "BillDeskTestMerchantName");
        params.put("PRN", "BLDK000123");
        params.put("AMT", "221.01");
        params.put("CRN", bankProperties.getCurrency());
        params.put("RU", bankProperties.getDefaultReturnUrl());
        params.put("CG", "Y");
        params.put("USER_LANG_ID", bankProperties.getLanguageId());
        params.put("UserType", bankProperties.getUserType());
        params.put("AppType", bankProperties.getApplicationType());

        StringBuilder plain = new StringBuilder();
        params.forEach((k, v) -> plain.append(k).append('=').append(v).append('&'));
        plain.setLength(plain.length() - 1);

        String checksum = checksumService.generateChecksum(plain.toString());
        String fullPlainText = plain + "&CHECKSUM=" + checksum;
        String encrypted = encryptionService.encrypt(fullPlainText);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("mercode", bankProperties.getMerchantCode());
        response.put("plainTextBeforeChecksum", plain.toString());
        response.put("checksum", checksum);
        response.put("fullPlainTextWithChecksum", fullPlainText);
        response.put("encDhanBankData", encrypted);
        return response;
    }
}

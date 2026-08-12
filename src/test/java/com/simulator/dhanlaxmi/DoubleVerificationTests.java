package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.controller.DoubleVerificationController;
import com.simulator.dhanlaxmi.controller.DoubleVerificationController.DoubleVerificationRequest;
import com.simulator.dhanlaxmi.controller.DoubleVerificationController.DoubleVerificationResponse;
import com.simulator.dhanlaxmi.crypto.AesEncryptionService;
import com.simulator.dhanlaxmi.crypto.Sha512ChecksumService;
import com.simulator.dhanlaxmi.model.BankAccount;
import com.simulator.dhanlaxmi.model.ScenarioTag;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import com.simulator.dhanlaxmi.service.PaymentService;
import com.simulator.dhanlaxmi.util.ParamCodec;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DoubleVerificationTests {

    @Resource
    private DoubleVerificationController doubleVerificationController;

    @Resource
    private PaymentService paymentService;

    @Resource
    private BankAccountRepository bankAccountRepository;

    @Resource
    private BankProperties bankProperties;

    private AesEncryptionService encryptionService;
    private Sha512ChecksumService checksumService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionService(bankProperties);
        checksumService = new Sha512ChecksumService();
        bankAccountRepository.save(new BankAccount("7777000077", "DV Test User", new BigDecimal("50000.00"), ScenarioTag.SUCCESS));
    }

    private String buildEncryptedPaymentRequest(String prn, String amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action.ShoppingMall.Login.Init", "Y");
        params.put("BankId", "001");
        params.put("MD", "P");
        params.put("PID", bankProperties.getMerchantCode());
        params.put("ITC", "BillDeskTestMerchantName");
        params.put("PRN", prn);
        params.put("AMT", amount);
        params.put("CRN", "INR");
        params.put("RU", "http://localhost:1/callback");
        params.put("CG", "Y");
        params.put("USER_LANG_ID", "001");
        params.put("UserType", "1");
        params.put("AppType", "Corporate");

        String plainData = ParamCodec.build(params);
        String checksum = checksumService.generateChecksum(plainData);
        return encryptionService.encrypt(plainData + "&CHECKSUM=" + checksum);
    }

    private String buildEncryptedDvRequest(String prn) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action.ShoppingMall.Login.Init", "Y");
        params.put("BankId", "001");
        params.put("MD", "V");
        params.put("STATFLG", "Y");
        params.put("BID", "");
        params.put("PID", bankProperties.getMerchantCode());
        params.put("ITC", "BillDeskTestMerchantName");
        params.put("PRN", prn);
        params.put("AMT", "500.00");
        params.put("CRN", "INR");
        params.put("RU", "http://localhost:1/callback");
        params.put("CG", "Y");
        params.put("USER_LANG_ID", "001");
        params.put("UserType", "1");
        params.put("AppType", "Corporate");

        String plainData = ParamCodec.build(params);
        String checksum = checksumService.generateChecksum(plainData);
        return encryptionService.encrypt(plainData + "&CHECKSUM=" + checksum);
    }

    @Test
    void resolvedSuccessTransactionReturnsStatusCode000() {
        paymentService.processPaymentRequest(bankProperties.getMerchantCode(), buildEncryptedPaymentRequest("DVTEST001", "500.00"));
        paymentService.completePayment("DVTEST001", "7777000077");

        String dvRequest = buildEncryptedDvRequest("DVTEST001");
        ResponseEntity<DoubleVerificationResponse> response =
                doubleVerificationController.verify(new DoubleVerificationRequest(bankProperties.getMerchantCode(), dvRequest));

        assertEquals("000", response.getBody().statusCode());
        assertEquals("Success", response.getBody().statusDescription());
        assertNotNull(response.getBody().verificationResponse());

        // Decrypt the verification response and confirm it reports PAID=Y
        String decrypted = encryptionService.decrypt(response.getBody().verificationResponse());
        Map<String, String> params = ParamCodec.parse(decrypted);
        assertEquals("Y", params.get("PAID"));
        assertEquals("DVTEST001", params.get("PRN"));
    }

    @Test
    void unknownPrnReturnsStatusCode004() {
        String dvRequest = buildEncryptedDvRequest("NO_SUCH_PRN");
        ResponseEntity<DoubleVerificationResponse> response =
                doubleVerificationController.verify(new DoubleVerificationRequest(bankProperties.getMerchantCode(), dvRequest));

        assertEquals("004", response.getBody().statusCode());
        assertNull(response.getBody().verificationResponse());
    }

    @Test
    void wrongMercodeReturnsStatusCode001() {
        String dvRequest = buildEncryptedDvRequest("DVTEST002");
        ResponseEntity<DoubleVerificationResponse> response =
                doubleVerificationController.verify(new DoubleVerificationRequest("WRONGMERCODE", dvRequest));

        assertEquals("001", response.getBody().statusCode());
    }

    @Test
    void tamperedChecksumReturnsStatusCode003() {
        String badRequest = encryptionService.encrypt("PRN=DVTEST003&AMT=500.00&CHECKSUM=deadbeef");
        ResponseEntity<DoubleVerificationResponse> response =
                doubleVerificationController.verify(new DoubleVerificationRequest(bankProperties.getMerchantCode(), badRequest));

        assertEquals("003", response.getBody().statusCode());
    }

    @Test
    void pendingTransactionReportedAsPending() {
        bankAccountRepository.save(new BankAccount("8888000088", "DV Pending User", new BigDecimal("50000.00"), ScenarioTag.PENDING));
        paymentService.processPaymentRequest(bankProperties.getMerchantCode(), buildEncryptedPaymentRequest("DVTEST004", "500.00"));
        paymentService.completePayment("DVTEST004", "8888000088");

        String dvRequest = buildEncryptedDvRequest("DVTEST004");
        ResponseEntity<DoubleVerificationResponse> response =
                doubleVerificationController.verify(new DoubleVerificationRequest(bankProperties.getMerchantCode(), dvRequest));

        assertEquals("001", response.getBody().statusCode());
        assertEquals("Pending", response.getBody().statusDescription());
    }
}

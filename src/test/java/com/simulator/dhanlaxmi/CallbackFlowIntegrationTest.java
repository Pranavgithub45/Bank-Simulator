package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.AesEncryptionService;
import com.simulator.dhanlaxmi.crypto.Sha512ChecksumService;
import com.simulator.dhanlaxmi.model.CallbackStatus;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.model.TransactionStatus;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.util.ParamCodec;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression test for the callback-status bug: a payment
 * completed on a SUCCESS-tagged account, whose RU actually points at a
 * reachable listener (this app's own BillDeskCallbackReceiverController),
 * must end up with callbackStatus SENT - not FAILED.
 *
 * Runs the app on a real random port and drives it purely over HTTP
 * (payment request -> complete-payment -> callback delivery), the same
 * way a real client would, rather than calling services directly.
 *
 * Deliberately does NOT use @ActiveProfiles("test") - PaymentFlowTests
 * already owns that profile's in-memory H2 context (a different Spring
 * context than this one, since webEnvironment differs). Using the default
 * profile here instead means this test's context talks to the same
 * AUTO_SERVER file-mode H2 instance CallbackServiceTests and
 * DoubleVerificationTests already use, which is designed for exactly this
 * kind of multi-context access and avoids any risk of two contexts
 * fighting over the same named in-memory database.
 *
 * Deliberately does NOT use @Transactional: the HTTP calls below run on
 * the embedded server's own request thread, not the test thread, so a
 * test-level transaction would never see (or roll back) what that thread
 * commits anyway. Each test generates a fresh random PRN instead, so
 * re-running the suite against the persistent file-mode H2 database never
 * collides with a PRN a previous run already committed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CallbackFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Resource
    private TestRestTemplate restTemplate;

    @Resource
    private BankProperties bankProperties;

    @Resource
    private TransactionRepository transactionRepository;

    private AesEncryptionService encryptionService;
    private Sha512ChecksumService checksumService;
    private String baseUrl;
    private String returnUrl;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionService(bankProperties);
        checksumService = new Sha512ChecksumService();
        baseUrl = "http://localhost:" + port;
        // The app's own mock receiver, on the app's own actual port -
        // guaranteed reachable, unlike the old hardcoded :8081 sample.
        returnUrl = baseUrl + "/billdesk/callback-receiver";
    }

    private String buildEncryptedPaymentRequest(String prn, String amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action.ShoppingMall.Login.Init", "Y");
        params.put("BankId", bankProperties.getBankId());
        params.put("MD", "P");
        params.put("PID", bankProperties.getMerchantCode());
        params.put("ITC", "BillDeskTestMerchantName");
        params.put("PRN", prn);
        params.put("AMT", amount);
        params.put("CRN", bankProperties.getCurrency());
        params.put("RU", returnUrl);
        params.put("CG", "Y");
        params.put("USER_LANG_ID", bankProperties.getLanguageId());
        params.put("UserType", bankProperties.getUserType());
        params.put("AppType", bankProperties.getApplicationType());

        String plainData = ParamCodec.build(params);
        String checksum = checksumService.generateChecksum(plainData);
        return encryptionService.encrypt(plainData + "&CHECKSUM=" + checksum);
    }

    private String freshPrn(String label) {
        // Short, unique per run so re-running this test against the
        // persistent file-mode H2 database never collides with a PRN a
        // previous run already committed (see class-level Javadoc).
        return (label + UUID.randomUUID()).substring(0, 20).toUpperCase();
    }

    @Test
    void successfulPaymentWithReachableReturnUrlEndsUpSent() {
        String prn = freshPrn("CBFLOWOK");
        String encrypted = buildEncryptedPaymentRequest(prn, "500.00");

        // Step 1: BillDesk -> Bank payment request
        MultiValueMap<String, String> paymentForm = new LinkedMultiValueMap<>();
        paymentForm.add("mercode", bankProperties.getMerchantCode());
        paymentForm.add("encDhanBankData", encrypted);

        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> paymentResponse = restTemplate.postForEntity(
                baseUrl + "/Corporate/prelogin/payment-gateway",
                new HttpEntity<>(paymentForm, formHeaders),
                Map.class);

        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode());
        assertEquals("RECEIVED", paymentResponse.getBody().get("status"));

        // Step 2: customer "logs in" with a SUCCESS-tagged seeded account
        Map<String, String> completeBody = new LinkedHashMap<>();
        completeBody.put("prn", prn);
        completeBody.put("accountNo", "1111000011");

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> completeResponse = restTemplate.postForEntity(
                baseUrl + "/bank/complete-payment",
                new HttpEntity<>(completeBody, jsonHeaders),
                Map.class);

        assertEquals(HttpStatus.OK, completeResponse.getStatusCode());
        assertEquals("SUCCESS", completeResponse.getBody().get("status"));
        assertEquals("SENT", completeResponse.getBody().get("callbackStatus"));

        // Confirm persisted state matches what the response claimed
        Optional<Transaction> saved = transactionRepository.findByPrn(prn);
        assertTrue(saved.isPresent());
        assertEquals(TransactionStatus.SUCCESS, saved.get().getStatus());
        assertEquals(CallbackStatus.SENT, saved.get().getCallbackStatus());
    }

    @Test
    void dropBehaviorStillNeverAttemptsDeliveryAfterTheFix() {
        String prn = freshPrn("CBFLOWDROP");
        String encrypted = buildEncryptedPaymentRequest(prn, "500.00");

        MultiValueMap<String, String> paymentForm = new LinkedMultiValueMap<>();
        paymentForm.add("mercode", bankProperties.getMerchantCode());
        paymentForm.add("encDhanBankData", encrypted);
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        restTemplate.postForEntity(baseUrl + "/Corporate/prelogin/payment-gateway",
                new HttpEntity<>(paymentForm, formHeaders), Map.class);

        Map<String, String> completeBody = new LinkedHashMap<>();
        completeBody.put("prn", prn);
        completeBody.put("accountNo", "1111000011");
        completeBody.put("callbackBehavior", "DROP");
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> completeResponse = restTemplate.postForEntity(
                baseUrl + "/bank/complete-payment",
                new HttpEntity<>(completeBody, jsonHeaders),
                Map.class);

        assertEquals("SUCCESS", completeResponse.getBody().get("status"));
        assertEquals("DROPPED", completeResponse.getBody().get("callbackStatus"));
    }
}

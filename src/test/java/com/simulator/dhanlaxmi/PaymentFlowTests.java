package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.AesEncryptionService;
import com.simulator.dhanlaxmi.crypto.Sha512ChecksumService;
import com.simulator.dhanlaxmi.exception.DuplicateTransactionException;
import com.simulator.dhanlaxmi.exception.InvalidChecksumException;
import com.simulator.dhanlaxmi.exception.InvalidMercodeException;
import com.simulator.dhanlaxmi.exception.TransactionAlreadyResolvedException;
import com.simulator.dhanlaxmi.exception.TransactionNotFoundException;
import com.simulator.dhanlaxmi.model.BankAccount;
import com.simulator.dhanlaxmi.model.ScenarioTag;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.model.TransactionStatus;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.service.PaymentService;
import com.simulator.dhanlaxmi.util.ParamCodec;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentFlowTests {

    @Resource
    private PaymentService paymentService;

    @Resource
    private BankAccountRepository bankAccountRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private BankProperties bankProperties;

    private AesEncryptionService encryptionService;
    private Sha512ChecksumService checksumService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesEncryptionService(bankProperties);
        checksumService = new Sha512ChecksumService();

        /*
         * Clean the test database before each test.
         *
         * deleteAllInBatch() performs a bulk delete and flush() makes sure
         * the database has processed the delete before the seed records
         * are inserted.
         */
        transactionRepository.deleteAllInBatch();
        bankAccountRepository.deleteAllInBatch();

        transactionRepository.flush();
        bankAccountRepository.flush();

        bankAccountRepository.save(
                new BankAccount(
                        "1111000011",
                        "Success User",
                        new BigDecimal("50000.00"),
                        ScenarioTag.SUCCESS
                )
        );

        bankAccountRepository.save(
                new BankAccount(
                        "2222000022",
                        "Low Balance User",
                        new BigDecimal("100.00"),
                        ScenarioTag.SUCCESS
                )
        );

        bankAccountRepository.save(
                new BankAccount(
                        "5555000055",
                        "Generic Failure User",
                        new BigDecimal("50000.00"),
                        ScenarioTag.GENERIC_FAILURE
                )
        );

        bankAccountRepository.save(
                new BankAccount(
                        "6666000066",
                        "Pending User",
                        new BigDecimal("50000.00"),
                        ScenarioTag.PENDING
                )
        );

        bankAccountRepository.flush();
    }

    private String buildEncryptedRequest(String prn, String amount) {
        Map<String, String> params = new LinkedHashMap<>();

        params.put("Action.ShoppingMall.Login.Init", "Y");
        params.put("BankId", "001");
        params.put("MD", "P");
        params.put("PID", bankProperties.getMerchantCode());
        params.put("ITC", "BillDeskTestMerchantName");
        params.put("PRN", prn);
        params.put("AMT", amount);
        params.put("CRN", "INR");
        params.put("RU", "http://localhost:8081/callback");
        params.put("CG", "Y");
        params.put("USER_LANG_ID", "001");
        params.put("UserType", "1");
        params.put("AppType", "Corporate");

        String plainData = ParamCodec.build(params);
        String checksum = checksumService.generateChecksum(plainData);

        return encryptionService.encrypt(
                plainData + "&CHECKSUM=" + checksum
        );
    }

    @Test
    void successfulAccountProducesSuccess() {
        String encrypted =
                buildEncryptedRequest("TESTPRN001", "500.00");

        Transaction received =
                paymentService.processPaymentRequest(
                        bankProperties.getMerchantCode(),
                        encrypted
                );

        assertEquals(
                TransactionStatus.RECEIVED,
                received.getStatus()
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN001",
                        "1111000011"
                );

        assertEquals(
                TransactionStatus.SUCCESS,
                resolved.getStatus()
        );

        assertNull(resolved.getFailureReason());
    }

    @Test
    void unregisteredAccountProducesInvalidAccountGenerically() {
        String encrypted =
                buildEncryptedRequest("TESTPRN002", "500.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN002",
                        "9999999999"
                );

        assertEquals(
                TransactionStatus.FAILURE,
                resolved.getStatus()
        );

        assertEquals(
                "INVALID_ACCOUNT",
                resolved.getFailureReason()
        );
    }

    @Test
    void amountExceedingBalanceProducesInsufficientBalanceGenerically() {
        String encrypted =
                buildEncryptedRequest("TESTPRN003", "5000.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN003",
                        "2222000022"
                );

        assertEquals(
                TransactionStatus.FAILURE,
                resolved.getStatus()
        );

        assertEquals(
                "INSUFFICIENT_BALANCE",
                resolved.getFailureReason()
        );
    }

    @Test
    void sameLowBalanceAccountSucceedsWhenAmountIsWithinBalance() {
        String encrypted =
                buildEncryptedRequest("TESTPRN004", "50.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN004",
                        "2222000022"
                );

        assertEquals(
                TransactionStatus.SUCCESS,
                resolved.getStatus()
        );
    }

    @Test
    void genericFailureTaggedAccountProducesFailure() {
        String encrypted =
                buildEncryptedRequest("TESTPRN005", "500.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN005",
                        "5555000055"
                );

        assertEquals(
                TransactionStatus.FAILURE,
                resolved.getStatus()
        );

        assertEquals(
                "TRANSACTION_FAILED",
                resolved.getFailureReason()
        );
    }

    @Test
    void pendingTaggedAccountProducesPending() {
        String encrypted =
                buildEncryptedRequest("TESTPRN006", "500.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        Transaction resolved =
                paymentService.completePayment(
                        "TESTPRN006",
                        "6666000066"
                );

        assertEquals(
                TransactionStatus.PENDING,
                resolved.getStatus()
        );
    }

    @Test
    void duplicatePrnIsRejected() {
        String encrypted =
                buildEncryptedRequest("TESTPRN007", "500.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        String secondEncrypted =
                buildEncryptedRequest("TESTPRN007", "500.00");

        assertThrows(
                DuplicateTransactionException.class,
                () -> paymentService.processPaymentRequest(
                        bankProperties.getMerchantCode(),
                        secondEncrypted
                )
        );
    }

    @Test
    void wrongMercodeIsRejected() {
        String encrypted =
                buildEncryptedRequest("TESTPRN008", "500.00");

        assertThrows(
                InvalidMercodeException.class,
                () -> paymentService.processPaymentRequest(
                        "WRONGMERCODE",
                        encrypted
                )
        );
    }

    @Test
    void tamperedChecksumIsRejected() {
        String encrypted =
                encryptionService.encrypt(
                        "PRN=TESTPRN009&AMT=500.00&CHECKSUM=deadbeef"
                );

        assertThrows(
                InvalidChecksumException.class,
                () -> paymentService.processPaymentRequest(
                        bankProperties.getMerchantCode(),
                        encrypted
                )
        );
    }

    @Test
    void completingAlreadyResolvedTransactionIsRejected() {
        String encrypted =
                buildEncryptedRequest("TESTPRN010", "500.00");

        paymentService.processPaymentRequest(
                bankProperties.getMerchantCode(),
                encrypted
        );

        paymentService.completePayment(
                "TESTPRN010",
                "1111000011"
        );

        assertThrows(
                TransactionAlreadyResolvedException.class,
                () -> paymentService.completePayment(
                        "TESTPRN010",
                        "1111000011"
                )
        );
    }

    @Test
    void completingUnknownPrnIsRejected() {
        assertThrows(
                TransactionNotFoundException.class,
                () -> paymentService.completePayment(
                        "NO_SUCH_PRN",
                        "1111000011"
                )
        );
    }

}
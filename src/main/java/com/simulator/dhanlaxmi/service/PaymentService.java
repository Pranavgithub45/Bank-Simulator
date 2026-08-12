package com.simulator.dhanlaxmi.service;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.ChecksumService;
import com.simulator.dhanlaxmi.crypto.EncryptionService;
import com.simulator.dhanlaxmi.exception.*;
import com.simulator.dhanlaxmi.model.*;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.util.ParamCodec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class PaymentService {

    private final EncryptionService encryptionService;
    private final ChecksumService checksumService;
    private final BankProperties bankProperties;
    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final Random random = new Random();

    public PaymentService(EncryptionService encryptionService,
                           ChecksumService checksumService,
                           BankProperties bankProperties,
                           TransactionRepository transactionRepository,
                           BankAccountRepository bankAccountRepository) {
        this.encryptionService = encryptionService;
        this.checksumService = checksumService;
        this.bankProperties = bankProperties;
        this.transactionRepository = transactionRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    /**
     * Step 1 of the flow: BillDesk -> Bank. Validates mercode, decrypts,
     * verifies checksum, rejects duplicate PRNs, and creates the
     * transaction as RECEIVED. No account is involved yet - the bank
     * doesn't know which account the customer will use until they "log
     * in" at completePayment().
     */
    public Transaction processPaymentRequest(String mercode, String encDhanBankData) {
        if (!bankProperties.getMerchantCode().equals(mercode)) {
            throw new InvalidMercodeException(mercode);
        }

        String decrypted = encryptionService.decrypt(encDhanBankData);
        Map<String, String> params = ParamCodec.parse(decrypted);

        String receivedChecksum = params.remove("CHECKSUM");
        String recreatedPlainText = ParamCodec.build(params);

        if (!checksumService.verifyChecksum(recreatedPlainText, receivedChecksum)) {
            throw new InvalidChecksumException();
        }

        String prn = params.get("PRN");
        if (transactionRepository.findByPrn(prn).isPresent()) {
            throw new DuplicateTransactionException(prn);
        }

        Transaction transaction = new Transaction();
        transaction.setPrn(prn);
        transaction.setBid(generateBid());
        transaction.setMerchantCode(params.get("PID"));
        transaction.setMerchantName(params.get("ITC"));
        transaction.setAmount(new BigDecimal(params.get("AMT")));
        transaction.setCurrency(params.get("CRN"));
        String returnUrl = params.get("RU");

        if (returnUrl == null || returnUrl.isBlank()
                || returnUrl.contains("localhost:8081/callback")) {
            returnUrl = bankProperties.getDefaultReturnUrl();
        }

        transaction.setReturnUrl(returnUrl);
        transaction.setStatus(TransactionStatus.RECEIVED);
        transaction.setCallbackStatus(CallbackStatus.NOT_SENT);

        return transactionRepository.save(transaction);
    }

    /**
     * Step 2 of the flow: the simulated "customer completes payment" step
     * (stands in for bank login + authorization, no UI needed). Two
     * checks are generic and apply to every account, exactly as agreed:
     *
     *   1. Does the account exist in the DB at all?      -> INVALID_ACCOUNT
     *   2. Is the transaction amount within its balance?  -> INSUFFICIENT_BALANCE
     *
     * Only once both of those pass does the account's ScenarioTag decide
     * the outcome (SUCCESS / GENERIC_FAILURE / PENDING). TIMEOUT accounts
     * resolve as SUCCESS here too - the actual timeout behaviour belongs
     * to the callback layer (Phase 5: delaying/dropping the callback),
     * not to the payment status itself.
     */
    public Transaction completePayment(String prn, String accountNo) {
        Transaction transaction = transactionRepository.findByPrn(prn)
                .orElseThrow(() -> new TransactionNotFoundException(prn));

        if (transaction.getStatus() != TransactionStatus.RECEIVED) {
            throw new TransactionAlreadyResolvedException(prn, transaction.getStatus().name());
        }

        transaction.setAccountNo(accountNo);

        Optional<BankAccount> accountOpt = bankAccountRepository.findByAccountNo(accountNo);

        if (accountOpt.isEmpty()) {
            transaction.setStatus(TransactionStatus.FAILURE);
            transaction.setFailureReason("INVALID_ACCOUNT");
            return transactionRepository.save(transaction);
        }

        BankAccount account = accountOpt.get();

        if (transaction.getAmount().compareTo(account.getBalance()) > 0) {
            transaction.setStatus(TransactionStatus.FAILURE);
            transaction.setFailureReason("INSUFFICIENT_BALANCE");
            return transactionRepository.save(transaction);
        }

        switch (account.getScenarioTag()) {
            case SUCCESS, TIMEOUT -> {
                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setFailureReason(null);
            }
            case GENERIC_FAILURE -> {
                transaction.setStatus(TransactionStatus.FAILURE);
                transaction.setFailureReason("TRANSACTION_FAILED");
            }
            case PENDING -> {
                transaction.setStatus(TransactionStatus.PENDING);
                transaction.setFailureReason(null);
            }
        }

        return transactionRepository.save(transaction);
    }

    private String generateBid() {
        return "SIM" + System.currentTimeMillis() + random.nextInt(1000);
    }
}

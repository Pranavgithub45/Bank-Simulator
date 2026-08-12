package com.simulator.dhanlaxmi.service;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.ChecksumService;
import com.simulator.dhanlaxmi.crypto.EncryptionService;
import com.simulator.dhanlaxmi.model.CallbackBehavior;
import com.simulator.dhanlaxmi.model.CallbackStatus;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.model.TransactionStatus;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import com.simulator.dhanlaxmi.util.ParamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CallbackServiceImpl implements CallbackService {

    private static final Logger log =
            LoggerFactory.getLogger(CallbackServiceImpl.class);

    private final ChecksumService checksumService;
    private final EncryptionService encryptionService;
    private final BankProperties bankProperties;
    private final TransactionRepository transactionRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public CallbackServiceImpl(
            ChecksumService checksumService,
            EncryptionService encryptionService,
            BankProperties bankProperties,
            TransactionRepository transactionRepository) {

        this.checksumService = checksumService;
        this.encryptionService = encryptionService;
        this.bankProperties = bankProperties;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void sendCallback(
            Transaction transaction,
            CallbackBehavior behavior) {

        log.info(
                "Starting callback for PRN={}, status={}, behavior={}, returnUrl={}",
                transaction.getPrn(),
                transaction.getStatus(),
                behavior,
                transaction.getReturnUrl()
        );

        switch (behavior) {

            case DROP -> {
                transaction.setCallbackStatus(
                        CallbackStatus.DROPPED
                );

                transactionRepository.save(transaction);

                log.info(
                        "Callback DROPPED for PRN={}",
                        transaction.getPrn()
                );
            }

            case DELAY -> {
                sleep(
                        bankProperties.getCallbackDelaySeconds()
                );

                deliverOnce(transaction);
            }

            case DUPLICATE -> {
                deliverOnce(transaction);
                deliverOnce(transaction);
            }

            case NORMAL -> deliverOnce(transaction);
        }
    }

    private void deliverOnce(Transaction transaction) {

        try {

            String callbackUrl =
                    transaction.getReturnUrl();

            if (callbackUrl == null || callbackUrl.isBlank()) {
                throw new IllegalStateException(
                        "Callback URL is missing"
                );
            }

            log.info(
                    "Delivering callback for PRN={} to URL={}",
                    transaction.getPrn(),
                    callbackUrl
            );

            String encryptedData =
                    buildEncryptedResponse(transaction);

            MultiValueMap<String, String> form =
                    new LinkedMultiValueMap<>();

            form.add(
                    "mercode",
                    bankProperties.getMerchantCode()
            );

            form.add(
                    "encDhanBankData",
                    encryptedData
            );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED
            );

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(form, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            callbackUrl,
                            request,
                            String.class
                    );

            int statusCode =
                    response.getStatusCode().value();

            log.info(
                    "Callback response for PRN={} = HTTP {}",
                    transaction.getPrn(),
                    statusCode
            );

            if (response.getStatusCode().is2xxSuccessful()) {

                transaction.setCallbackStatus(
                        CallbackStatus.SENT
                );

                log.info(
                        "Callback SENT successfully for PRN={}",
                        transaction.getPrn()
                );

            } else {

                transaction.setCallbackStatus(
                        CallbackStatus.FAILED
                );

                log.warn(
                        "Callback FAILED for PRN={} because HTTP status was {}",
                        transaction.getPrn(),
                        statusCode
                );
            }

        } catch (Exception e) {

            transaction.setCallbackStatus(
                    CallbackStatus.FAILED
            );

            log.error(
                    "Callback FAILED for PRN={}, URL={}, error={}: {}",
                    transaction.getPrn(),
                    transaction.getReturnUrl(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
        }

        transactionRepository.save(transaction);
    }

    private void sleep(int seconds) {

        try {

            Thread.sleep(
                    seconds * 1000L
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.warn(
                    "Callback delay interrupted"
            );
        }
    }

    public String buildEncryptedResponse(
            Transaction transaction) {

        Map<String, String> params =
                new LinkedHashMap<>();

        params.put(
                "PRN",
                transaction.getPrn()
        );

        params.put(
                "BID",
                transaction.getBid()
        );

        params.put(
                "PAID",
                transaction.getStatus()
                        == TransactionStatus.SUCCESS
                        ? "Y"
                        : "N"
        );

        params.put(
                "AMT",
                transaction.getAmount()
                        .toPlainString()
        );

        params.put(
                "ITC",
                transaction.getMerchantName()
        );

        params.put(
                "REASON",
                transaction.getFailureReason() != null
                        ? transaction.getFailureReason()
                        : transaction.getStatus().name()
        );

        String plainData =
                ParamCodec.build(params);

        String checksum =
                checksumService.generateChecksum(
                        plainData
                );

        String fullPlainText =
                plainData +
                        "&CHECKSUM=" +
                        checksum;

        return encryptionService.encrypt(
                fullPlainText
        );
    }
}
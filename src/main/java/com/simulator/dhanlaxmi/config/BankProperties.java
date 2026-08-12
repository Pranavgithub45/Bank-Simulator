package com.simulator.dhanlaxmi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All bank-specific constants live here instead of being scattered
 * throughout the code, as recommended by the bank's own spec.
 */
@ConfigurationProperties(prefix = "dhanbank")
public class BankProperties {

    private String bankId = "001";
    private String merchantCode = "BLDKPG001C";
    private String currency = "INR";
    private String applicationType = "Corporate";
    private String languageId = "001";
    private String userType = "1";
    private String checksumAlgorithm = "SHA-512";
    private String encryptionAlgorithm = "AES-256";

    /**
     * Hex-encoded AES key exactly as supplied by the bank spec.
     * Hex-decoded once at startup to get the raw 32-byte (256-bit) key.
     * Kept openly in config on purpose - this is a test simulator only.
     */
    private String encryptionKeyHex;

    /**
     * AES cipher transformation. The bank spec does not document an IV
     * anywhere, so ECB/PKCS5Padding (no IV required) is the default.
     * This is explicitly an open item - change it here the moment the
     * real bank reference code / a working test vector says otherwise.
     * Nothing outside EncryptionService needs to change if this does.
     */
    private String cipherTransformation = "AES/ECB/PKCS5Padding";

    /**
     * How long a DELAY-behavior callback waits before sending, in seconds.
     * Also what the TIMEOUT scenario tag uses automatically (Phase 5).
     */
    private int callbackDelaySeconds = 5;

    /**
     * Default `RU` (return URL) used by /test/sample-payment-payload.
     *
     * Previously this pointed at http://localhost:8081/callback - a port
     * nothing in this project ever listens on - so every callback attempt
     * against it threw a connection exception and was (correctly) marked
     * FAILED. That's not a bug in the delivery logic itself; it's a sample
     * payload pointing at a URL with no listener behind it.
     *
     * Now defaults to this app's own built-in mock BillDesk receiver
     * (BillDeskCallbackReceiverController), which is always reachable
     * whenever the simulator itself is running, so a NORMAL callback for a
     * SUCCESS transaction actually lands on SENT.
     */
//    private String defaultReturnUrl = "http://localhost:8082/billdesk/callback-receiver";
//    private String defaultReturnUrl =
//            "http://localhost:8082/billdesk/callback-receiver";
    private  String defaultReturnUrl = "http://localhost:8082/billdesk/callback-receiver";


    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }

    public String getLanguageId() { return languageId; }
    public void setLanguageId(String languageId) { this.languageId = languageId; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getChecksumAlgorithm() { return checksumAlgorithm; }
    public void setChecksumAlgorithm(String checksumAlgorithm) { this.checksumAlgorithm = checksumAlgorithm; }

    public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
    public void setEncryptionAlgorithm(String encryptionAlgorithm) { this.encryptionAlgorithm = encryptionAlgorithm; }

    public String getEncryptionKeyHex() { return encryptionKeyHex; }
    public void setEncryptionKeyHex(String encryptionKeyHex) { this.encryptionKeyHex = encryptionKeyHex; }

    public String getCipherTransformation() { return cipherTransformation; }
    public void setCipherTransformation(String cipherTransformation) { this.cipherTransformation = cipherTransformation; }

    public int getCallbackDelaySeconds() { return callbackDelaySeconds; }
    public void setCallbackDelaySeconds(int callbackDelaySeconds) { this.callbackDelaySeconds = callbackDelaySeconds; }

    public String getDefaultReturnUrl() { return defaultReturnUrl; }
    public void setDefaultReturnUrl(String defaultReturnUrl) { this.defaultReturnUrl = defaultReturnUrl; }
}

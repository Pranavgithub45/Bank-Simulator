package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import com.simulator.dhanlaxmi.crypto.AesEncryptionService;
import com.simulator.dhanlaxmi.crypto.Sha512ChecksumService;
import com.simulator.dhanlaxmi.util.ParamCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CryptoCoreTests {

    private BankProperties bankProperties;
    private AesEncryptionService encryptionService;
    private Sha512ChecksumService checksumService;

    @BeforeEach
    void setUp() {
        bankProperties = new BankProperties();
        bankProperties.setMerchantCode("BLDKPG001C");
        bankProperties.setEncryptionKeyHex(
                "79244226452948404D635166546A576E5A7234753777217A25432A462D4A614E");
        bankProperties.setCipherTransformation("AES/ECB/PKCS5Padding");

        encryptionService = new AesEncryptionService(bankProperties);
        checksumService = new Sha512ChecksumService();
    }

    @Test
    void aesEncryptDecryptRoundTrip() {
        String plainText = "Hello Dhanlaxmi Bank Simulator";
        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    void encryptedOutputIsHex() {
        String encrypted = encryptionService.encrypt("some plaintext");
        assertTrue(encrypted.matches("^[0-9A-F]+$"), "Encrypted output should be uppercase hex");
    }

    @Test
    void checksumIsDeterministic() {
        String plainText = "PRN=BLDK000123&AMT=221.01";
        String checksum1 = checksumService.generateChecksum(plainText);
        String checksum2 = checksumService.generateChecksum(plainText);
        assertEquals(checksum1, checksum2);
        assertEquals(128, checksum1.length(), "SHA-512 hex output should be 128 chars");
    }

    @Test
    void checksumDetectsTampering() {
        String plainText = "PRN=BLDK000123&AMT=221.01";
        String checksum = checksumService.generateChecksum(plainText);

        assertTrue(checksumService.verifyChecksum(plainText, checksum));
        assertFalse(checksumService.verifyChecksum("PRN=BLDK000123&AMT=999.00", checksum));
    }

    @Test
    void paramCodecRoundTripPreservesOrder() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("BankId", "001");
        params.put("PRN", "BLDK000123");
        params.put("AMT", "221.01");

        String built = ParamCodec.build(params);
        assertEquals("BankId=001&PRN=BLDK000123&AMT=221.01", built);

        Map<String, String> parsed = ParamCodec.parse(built);
        assertEquals(params.keySet().toString(), parsed.keySet().toString());
        assertEquals("221.01", parsed.get("AMT"));
    }

    @Test
    void fullChecksumThenEncryptThenDecryptThenVerifyFlow() {
        String plainData = "PRN=BLDK000123&AMT=221.01&CRN=INR";
        String checksum = checksumService.generateChecksum(plainData);
        String fullPlainText = plainData + "&CHECKSUM=" + checksum;

        String encrypted = encryptionService.encrypt(fullPlainText);
        String decrypted = encryptionService.decrypt(encrypted);

        Map<String, String> params = ParamCodec.parse(decrypted);
        String receivedChecksum = params.remove("CHECKSUM");
        String recreatedPlainText = ParamCodec.build(params);

        assertTrue(checksumService.verifyChecksum(recreatedPlainText, receivedChecksum));
    }
}

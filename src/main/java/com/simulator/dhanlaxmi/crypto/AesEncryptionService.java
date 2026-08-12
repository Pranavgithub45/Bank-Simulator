package com.simulator.dhanlaxmi.crypto;

import com.simulator.dhanlaxmi.config.BankProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * AES-256 encrypt/decrypt, isolated behind EncryptionService so the rest
 * of the application never touches raw crypto.
 *
 * Key handling: the bank supplies the key as a HEX string. Hex-decoding it
 * yields the raw 32-byte (256-bit) key used directly as the AES key.
 *
 * Output format: HEX (uppercase), matching the sample encrypted values
 * shown in the bank's own spec document (they are hex, not Base64).
 *
 * Mode: no IV is documented anywhere in the bank spec, so
 * ECB/PKCS5Padding (no IV required) is used by default. This was checked
 * against the sample plaintext/checksum/encrypted-value triplet in the
 * spec doc, but that sample turned out not to be internally consistent
 * (its own checksum doesn't verify against its own plaintext), so it
 * could not be used to confirm the real mode. Treat this as an open item -
 * update dhanbank.cipher-transformation the moment a working reference
 * test vector is available. Nothing outside this class needs to change.
 */
@Service
public class AesEncryptionService implements EncryptionService {

    private static final String KEY_ALGORITHM = "AES";

    private final BankProperties bankProperties;
    private final SecretKeySpec secretKey;

    public AesEncryptionService(BankProperties bankProperties) {
        this.bankProperties = bankProperties;
        byte[] rawKey = HexFormat.of().parseHex(bankProperties.getEncryptionKeyHex());
        this.secretKey = new SecretKeySpec(rawKey, KEY_ALGORITHM);
    }

    @Override
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(bankProperties.getCipherTransformation());
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encrypted).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(bankProperties.getCipherTransformation());
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = HexFormat.of().parseHex(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed", e);
        }
    }
}

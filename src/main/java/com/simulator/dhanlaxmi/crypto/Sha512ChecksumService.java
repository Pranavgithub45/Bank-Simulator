package com.simulator.dhanlaxmi.crypto;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class Sha512ChecksumService implements ChecksumService {

    @Override
    public String generateChecksum(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }

    @Override
    public boolean verifyChecksum(String plainText, String checksum) {
        if (checksum == null) {
            return false;
        }
        String calculated = generateChecksum(plainText);
        return calculated.equalsIgnoreCase(checksum);
    }
}

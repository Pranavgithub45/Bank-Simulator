package com.simulator.dhanlaxmi.crypto;

public interface EncryptionService {

    String encrypt(String plainText);

    String decrypt(String encryptedText);
}

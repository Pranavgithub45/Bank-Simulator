package com.simulator.dhanlaxmi.crypto;

public interface ChecksumService {

    String generateChecksum(String plainText);

    boolean verifyChecksum(String plainText, String checksum);
}

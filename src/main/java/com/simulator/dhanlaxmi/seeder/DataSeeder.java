package com.simulator.dhanlaxmi.seeder;

import com.simulator.dhanlaxmi.model.BankAccount;
import com.simulator.dhanlaxmi.model.ScenarioTag;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds one dummy account per test scenario that genuinely needs a tag.
 *
 * "Invalid account" and "insufficient balance" are deliberately NOT
 * seeded here - they're computed generically for every transaction in
 * PaymentService.completePayment(): any account number not in this table
 * is automatically INVALID_ACCOUNT, and any amount exceeding the
 * account's balance is automatically INSUFFICIENT_BALANCE. That's why
 * account 2222000022 below has a small balance instead of a special tag -
 * send it an amount over 100.00 to trigger insufficient balance
 * naturally, or use any account number that isn't in this table at all
 * to trigger invalid account.
 *
 * Runs once on startup, skips if accounts already exist (so restarting
 * the app - H2 is file-mode - won't duplicate rows).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final BankAccountRepository bankAccountRepository;

    public DataSeeder(BankAccountRepository bankAccountRepository) {
        this.bankAccountRepository = bankAccountRepository;
    }

    @Override
    public void run(String... args) {
        // Remove the old Pending test account from an existing persistent H2 database
        // as well as from the seed set.
        bankAccountRepository.deleteByAccountNo("6666000066");

        if (bankAccountRepository.count() > 0) {
            return;
        }

        bankAccountRepository.save(new BankAccount(
                "1111000011", "Test Success User", new BigDecimal("50000.00"), ScenarioTag.SUCCESS));

        bankAccountRepository.save(new BankAccount(
                "2222000022", "Test Low Balance User", new BigDecimal("100.00"), ScenarioTag.SUCCESS));

        bankAccountRepository.save(new BankAccount(
                "4444000044", "Test Timeout User", new BigDecimal("50000.00"), ScenarioTag.TIMEOUT));

        bankAccountRepository.save(new BankAccount(
                "5555000055", "Test Generic Failure User", new BigDecimal("50000.00"), ScenarioTag.GENERIC_FAILURE));

    }
}

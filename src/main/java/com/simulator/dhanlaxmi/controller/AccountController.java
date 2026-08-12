package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.model.BankAccount;
import com.simulator.dhanlaxmi.repository.BankAccountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoint to confirm the seeded dummy accounts (Phase 2) are
 * actually in the database. Not part of the real bank contract.
 */
@RestController
public class AccountController {

    private final BankAccountRepository bankAccountRepository;

    public AccountController(BankAccountRepository bankAccountRepository) {
        this.bankAccountRepository = bankAccountRepository;
    }

    @GetMapping("/accounts")
    public List<BankAccount> listAccounts() {
        return bankAccountRepository.findAll();
    }
}

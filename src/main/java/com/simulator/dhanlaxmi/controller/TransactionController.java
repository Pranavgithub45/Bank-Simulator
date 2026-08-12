package com.simulator.dhanlaxmi.controller;

import com.simulator.dhanlaxmi.exception.TransactionNotFoundException;
import com.simulator.dhanlaxmi.model.Transaction;
import com.simulator.dhanlaxmi.repository.TransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints to inspect transaction state while testing.
 * Not part of the real bank contract.
 */
@RestController
public class TransactionController {

    private final TransactionRepository transactionRepository;

    public TransactionController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return transactionRepository.findAll();
    }

    @GetMapping("/transactions/{prn}")
    public Transaction getTransaction(@PathVariable String prn) {
        return transactionRepository.findByPrn(prn)
                .orElseThrow(() -> new TransactionNotFoundException(prn));
    }
}

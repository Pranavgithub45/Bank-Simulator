package com.simulator.dhanlaxmi.repository;

import com.simulator.dhanlaxmi.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByPrn(String prn);
}

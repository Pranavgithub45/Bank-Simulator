package com.simulator.dhanlaxmi.repository;

import com.simulator.dhanlaxmi.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByAccountNo(String accountNo);

    void deleteByAccountNo(String accountNo);
}

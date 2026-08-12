package com.simulator.dhanlaxmi.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * A seeded dummy bank account. Which account the simulated customer
 * "logs in" with determines the payment outcome (Phase 4+), based on
 * this account's scenarioTag.
 */
@Entity
@Table(name = "bank_account")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_no", nullable = false, unique = true)
    private String accountNo;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_tag", nullable = false)
    private ScenarioTag scenarioTag;

    public BankAccount() {
    }

    public BankAccount(String accountNo, String holderName, BigDecimal balance, ScenarioTag scenarioTag) {
        this.accountNo = accountNo;
        this.holderName = holderName;
        this.balance = balance;
        this.scenarioTag = scenarioTag;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public ScenarioTag getScenarioTag() { return scenarioTag; }
    public void setScenarioTag(ScenarioTag scenarioTag) { this.scenarioTag = scenarioTag; }
}

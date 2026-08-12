package com.simulator.dhanlaxmi.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Schema is ready as of Phase 2. Rows start getting created once the
 * payment endpoint is built in Phase 4.
 */
@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String prn;

    private String bid;

    @Column(name = "merchant_code")
    private String merchantCode;

    @Column(name = "merchant_name")
    private String merchantName;

    private BigDecimal amount;

    private String currency;

    @Column(name = "return_url")
    private String returnUrl;

    @Column(name = "account_no")
    private String accountNo;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "callback_status")
    private CallbackStatus callbackStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrn() { return prn; }
    public void setPrn(String prn) { this.prn = prn; }

    public String getBid() { return bid; }
    public void setBid(String bid) { this.bid = bid; }

    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public CallbackStatus getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(CallbackStatus callbackStatus) {
        this.callbackStatus = callbackStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.orchestrator.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * CloudBankSagaInfo is a class which holds the saga's info and is used to store in the cache.
 */
public class CloudBankSagaInfo implements Serializable {

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public void setReplies(Set<String> replies) {
        this.replies = replies;
    }

    public boolean isAccounts() {
        return accounts;
    }

    public void setAccounts(boolean accounts) {
        this.accounts = accounts;
    }

    public boolean isNewBA() {
        return newBA;
    }

    public void setNewBA(boolean newBA) {
        this.newBA = newBA;
    }
    public boolean isViewAll() {
        return viewAll;
    }

    public void setViewAll(boolean viewAll) {
        this.viewAll = viewAll;
    }

    public boolean isViewBA() {
        return viewBA;
    }

    public void setViewBA(boolean viewBA) {
        this.viewBA = viewBA;
    }

    public boolean isAccTransfer() {
        return accTransfer;
    }

    public void setAccTransfer(boolean accTransfer) {
        this.accTransfer = accTransfer;
    }

    public boolean isNewCustomer() {
        return newCustomer;
    }

    public void setNewCustomer(boolean newCustomer) {
        this.newCustomer = newCustomer;
    }

    public boolean isRollbackPerformed() {
        return rollbackPerformed;
    }

    public void setRollbackPerformed(boolean rollbackPerformed) {
        this.rollbackPerformed = rollbackPerformed;
    }

    public boolean isAccountsResponse() {
        return accountsResponse;
    }

    public void setAccountsResponse(boolean accountsResponse) {
        this.accountsResponse = accountsResponse;
    }

    public boolean isAccountsSecondResponse() {
        return accountsSecondResponse;
    }

    public void setAccountsSecondResponse(boolean accountsSecondResponse) {
        this.accountsSecondResponse = accountsSecondResponse;
    }

    public LoginDTO getLoginPayload() {
        return loginPayload;
    }

    public void setLoginPayload(LoginDTO loginPayload) {
        this.loginPayload = loginPayload;
    }

    public AccountTransferDTO getAccountTransferPayload() {
        return accountTransferPayload;
    }

    public void setAccountTransferPayload(AccountTransferDTO accountTransferPayload) {
        this.accountTransferPayload = accountTransferPayload;
    }

    public Accounts getRequestAccounts() {
        return requestAccounts;
    }

    public void setRequestAccounts(Accounts requestAccounts) {
        this.requestAccounts = requestAccounts;
    }

    private String sagaId;
    private Set<String> replies;
    private boolean accounts;
    private boolean newBA;
    private String accountResponse;

    public String getAccountResponse() {
        return accountResponse;
    }

    public void setAccountResponse(String accountResponse) {
        this.accountResponse = accountResponse;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private boolean viewAll;
    private boolean viewBA;
    private boolean accTransfer;
    private boolean newCustomer;
    private boolean rollbackPerformed;
    private boolean accountsResponse;
    private boolean accountsSecondResponse;
    private boolean accountsThirdResponse;
    private LoginDTO loginPayload;
    private AccountTransferDTO accountTransferPayload;
    private Accounts requestAccounts;
    public Boolean getDepositResponse() {
        return isDepositResponse;
    }

    public void setDepositResponse(Boolean depositResponse) {
        isDepositResponse = depositResponse;
    }

    public Boolean getWithdrawResponse() {
        return isWithdrawResponse;
    }

    public void setWithdrawResponse(Boolean withdrawResponse) {
        isWithdrawResponse = withdrawResponse;
    }

    private Boolean isDepositResponse;
    private Boolean isWithdrawResponse;
    private Boolean isWithdrawalCheck;

    public CloudBankSagaInfo() {
        replies = new HashSet<>();
    }

    public Set<String> getReplies() {
        return this.replies;
    }

    public void addReply(String participant) {
        replies.add(participant);
    }

    private String fromBank;
    private String toBank;

    public String getFromBank() {
        return fromBank;
    }

    public void setFromBank(String fromBank) {
        this.fromBank = fromBank;
    }

    public String getToBank() {
        return toBank;
    }

    public void setToBank(String toBank) {
        this.toBank = toBank;
    }

    /**
     * @return Boolean return the isDepositResponse
     */
    public Boolean isIsDepositResponse() {
        return isDepositResponse;
    }

    /**
     * @param isDepositResponse the isDepositResponse to set
     */
    public void setIsDepositResponse(Boolean isDepositResponse) {
        this.isDepositResponse = isDepositResponse;
    }

    /**
     * @return Boolean return the isWithdrawResponse
     */
    public Boolean isIsWithdrawResponse() {
        return isWithdrawResponse;
    }

    /**
     * @param isWithdrawResponse the isWithdrawResponse to set
     */
    public void setIsWithdrawResponse(Boolean isWithdrawResponse) {
        this.isWithdrawResponse = isWithdrawResponse;
    }

    /**
     * @return Boolean return the isWithdrawalCheck
     */
    public Boolean isIsWithdrawalCheck() {
        return isWithdrawalCheck;
    }

    /**
     * @param isWithdrawalCheck the isWithdrawalCheck to set
     */
    public void setIsWithdrawalCheck(Boolean isWithdrawalCheck) {
        this.isWithdrawalCheck = isWithdrawalCheck;
    }


    /**
     * @return boolean return the accountsThirdResponse
     */
    public boolean isAccountsThirdResponse() {
        return accountsThirdResponse;
    }

    /**
     * @param accountsThirdResponse the accountsThirdResponse to set
     */
    public void setAccountsThirdResponse(boolean accountsThirdResponse) {
        this.accountsThirdResponse = accountsThirdResponse;
    }

}

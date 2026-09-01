/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.orchestrator.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AccountTransferValidationResponseDTO is a class which 
 * sends back the validation response for the particular Account.
 */
public class AccountTransferValidationResponse {

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
    
    private String ucid;
    private String isBankSame;
    private String toAccountNumber;
    private String fromAccountNumber;
    private String responseForTo;
    private String responseForFrom;


    /**
     * @return String return the ucid
     */
    public String getUcid() {
        return ucid;
    }

    /**
     * @param ucid the ucid to set
     */
    public void setUcid(String ucid) {
        this.ucid = ucid;
    }

    /**
     * @return String return the isBankSame
     */
    public String getIsBankSame() {
        return isBankSame;
    }

    /**
     * @param isBankSame the isBankSame to set
     */
    public void setIsBankSame(String isBankSame) {
        this.isBankSame = isBankSame;
    }

    /**
     * @return String return the toAccountNumber
     */
    public String getToAccountNumber() {
        return toAccountNumber;
    }

    /**
     * @param toAccountNumber the toAccountNumber to set
     */
    public void setToAccountNumber(String toAccountNumber) {
        this.toAccountNumber = toAccountNumber;
    }

    /**
     * @return String return the fromAccountNumber
     */
    public String getFromAccountNumber() {
        return fromAccountNumber;
    }

    /**
     * @param fromAccountNumber the fromAccountNumber to set
     */
    public void setFromAccountNumber(String fromAccountNumber) {
        this.fromAccountNumber = fromAccountNumber;
    }

    /**
     * @return String return the responseForTo
     */
    public String getResponseForTo() {
        return responseForTo;
    }

    /**
     * @param responseForTo the responseForTo to set
     */
    public void setResponseForTo(String responseForTo) {
        this.responseForTo = responseForTo;
    }

    /**
     * @return String return the responseForFrom
     */
    public String getResponseForFrom() {
        return responseForFrom;
    }

    /**
     * @param responseForFrom the responseForFrom to set
     */
    public void setResponseForFrom(String responseForFrom) {
        this.responseForFrom = responseForFrom;
    }

}

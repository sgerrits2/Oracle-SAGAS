/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.saga.cloudbank.orchestrator.data.*;
import com.oracle.saga.cloudbank.orchestrator.stubs.Stubs;
import com.oracle.saga.cloudbank.orchestrator.util.ConnectionPools;
import com.oracle.saga.cloudbank.orchestrator.util.PropertiesHelper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.saga.Saga;
import oracle.saga.SagaException;
import oracle.saga.SagaInitiator;
import oracle.saga.SagaMessageContext;
import oracle.saga.annotation.*;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * CloudBankController is the initiator controller for the CloudBank participant.
 * The @Participant annotation maps the class to the registered CloudBank participant.
 * SagaInitiator provides the APIs used to begin, commit, and roll back sagas.
 */
@Path("/")
@Singleton
@Participant(name = "CloudBank")
public class CloudBankController extends SagaInitiator {
    private static final Logger logger = LoggerFactory.getLogger(CloudBankController.class);
    private static final String STATUS = "status";
    private final CacheManager cacheManager;
    private final Cache<String, CloudBankSagaInfo> cloudBankSagaCache;

    private static final Tracer otelTracer =
            GlobalOpenTelemetry.getTracer("com.oracle.saga.cloudbank.orchestrator");


    /**
     * Constructor to initialize the cache and set different values.
     */
    public CloudBankController() throws SagaException {
        var p = PropertiesHelper.loadProperties();
        var cacheSize = Integer.parseInt(p.getProperty("cacheSize", "100000"));
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        cloudBankSagaCache = cacheManager.createCache(Stubs.CACHE_NAME,
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class,
                        CloudBankSagaInfo.class, ResourcePoolsBuilder.heap(cacheSize)));
    }

    @SagaConnection
    public static Connection getCloudBankConnection() throws SQLException {
        return ConnectionPools.getCloudBankConnection();
    }

    /**
     * The PreDestroy annotation is used on a method as a callback notification to
     * signal that the instance is in the process of being removed by the container.
     */
    @Override
    @PreDestroy
    public void close() {
        try {
            logger.debug("Shutting down CloudBank Controller");
            super.close();
        } catch (SagaException e) {
            logger.error("Unable to shutdown initiator");
        }
    }

    /**
     * Indicates that the annotated method responds to HTTP GET requests.
     */
    @GET
    @Path("version")
    public Response getVersion() {
        return Response.status(Response.Status.OK.getStatusCode()).entity("Connection Spring-Boot no Wallet 2.0").build();
    }
    
    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("newCustomer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newCustomer(NewCustomerDTO payload) {
        Response response;
        var obj = new ObjectMapper();
        String loginId = null;

            try (var conn = ConnectionPools.getCloudBankConnection()) {
                loginId = Stubs.createNewCustomer(conn, payload);
            } catch (SQLException ex) {
                logger.error("Error creating new customer!!!");
            }

        var rpayload = obj.createObjectNode();
        if(loginId!=null){
            rpayload.put(STATUS, Stubs.ACCEPTED_STATUS);
            rpayload.put("login_id", loginId);
            response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
        }else{
            rpayload.put(STATUS, "Rejected");
            response = Response.status(Response.Status.BAD_REQUEST).entity(rpayload.toString()).build();
        }
        logger.debug(Stubs.RESPONSE_IS, response);

        return response;
    }

    /**
     * Returns the top 10 rows from the cloudbank_book table.
     */
    @GET
    @Path("logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecentLogs(@QueryParam("ucid") String ucid) {

        if (ucid == null || ucid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required query-param `ucid`")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        try (var conn = ConnectionPools.getCloudBankConnection()) {
            List<CloudBankBookEntryDTO> logs = Stubs.fetchRecentEntries(conn, ucid);
            Map<String,List<CloudBankBookEntryDTO>> wrapper = Map.of("data", logs);
            return Response.ok(wrapper).build();
        } catch (Exception ex) {
            logger.error("Error fetching cloudbank_book table details!!!");
            return Response.serverError().entity("Could not fetch logs: " + ex.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginDTO payload) {

        Response response;
        var obj = new ObjectMapper();
        LoginReplyDTO rplyQuery = null;
        String bank=null;
        JsonObjectBuilder jsonObjectBuilderMain = null;
        var client1 = HttpClient.newHttpClient();

        try (var conn = ConnectionPools.getCloudBankConnection()) {
                rplyQuery = Stubs.login(conn, payload);

            if(rplyQuery!=null){

            JsonObject jsonObject1;
            try(JsonReader reader = Json.createReader(new StringReader(rplyQuery.toString()))){
                jsonObject1 = reader.readObject();
            }
            jsonObjectBuilderMain = Json.createObjectBuilder(jsonObject1);

                bank = Stubs.getBankBasedOnUCID(conn, payload.getId());
            }else{
                response = Response.status(Response.Status.FORBIDDEN).build();
            }

                if(bank!=null){
                        var pack1 = new ViewAllAccountsDTO();
                        pack1.setUcid(rplyQuery.getUcid());
                        HttpRequest request1;
                        if(bank.equalsIgnoreCase(Stubs.BANK_A)){
                            request1 = HttpRequest.newBuilder()
                                    .uri(new URI(Stubs.URL_VIEW_ALL_ACCOUNTS_BANK_A))
                                    .header(Stubs.CONTENT_TYPE, Stubs.HEADER_JSON)
                                    .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                    .build();
                        }else{
                            request1 = HttpRequest.newBuilder()
                                    .uri(new URI(Stubs.URL_VIEW_ALL_ACCOUNTS_BANK_B))
                                    .header(Stubs.CONTENT_TYPE, Stubs.HEADER_JSON)
                                    .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                    .build();
                        }
                        HttpResponse<String> apiResp1 = client1.send(request1, HttpResponse.BodyHandlers.ofString());

                        if(Response.Status.ACCEPTED.getStatusCode() == apiResp1.statusCode()){
                            JsonObject jsonObject2;
                            try(JsonReader reader = Json.createReader(new StringReader(apiResp1.body()))){
                                jsonObject2 = reader.readObject();
                            }
                            var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject2);
                            jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                        }
                        var finalJSON = jsonObjectBuilderMain.build();
                        var rpayload = obj.createObjectNode();
                        rpayload.put(STATUS, Stubs.ACCEPTED_STATUS);
                        rpayload.put("data", finalJSON.toString());
                        response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                        logger.debug(Stubs.RESPONSE_IS, response);
                }else{
                    response = Response.status(Response.Status.FORBIDDEN).build();
                }

            } catch (URISyntaxException | IOException | SQLException | InterruptedException e1) {
                if (e1 instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.error("Login Viewing Error");
                response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                logger.debug(Stubs.STATUS_OF, Response.Status.INTERNAL_SERVER_ERROR);
            }

        return response;
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response refresh(RefreshDTO payload) {

        Response response = null;
        var obj = new ObjectMapper();
        String bank =null;

        try {
                var jsonObjectBuilderMain = Json.createObjectBuilder();

                try (var conn = ConnectionPools.getCloudBankConnection()) {
                    bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
                }

                if (bank != null) {
                        var client1 = HttpClient.newHttpClient();
                        var pack1 = new ViewAllAccountsDTO();
                        pack1.setUcid(payload.getUcid());
                        HttpRequest request1;
                        if (bank.equalsIgnoreCase(Stubs.BANK_A)) {
                            request1 = HttpRequest.newBuilder()
                                    .uri(new URI(Stubs.URL_VIEW_ALL_ACCOUNTS_BANK_A))
                                    .header(Stubs.CONTENT_TYPE, Stubs.HEADER_JSON)
                                    .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                    .build();
                        } else {
                            request1 = HttpRequest.newBuilder()
                                    .uri(new URI(Stubs.URL_VIEW_ALL_ACCOUNTS_BANK_B))
                                    .header(Stubs.CONTENT_TYPE, Stubs.HEADER_JSON)
                                    .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                    .build();
                        }
                        HttpResponse<String> apiResp1 = client1.send(request1, HttpResponse.BodyHandlers.ofString());

                        if (Response.Status.ACCEPTED.getStatusCode() == apiResp1.statusCode()) {
                            JsonObject jsonObject2;
                            try (JsonReader reader = Json.createReader(new StringReader(apiResp1.body()))) {
                                jsonObject2 = reader.readObject();
                            }
                            var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject2);
                            jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                           }

                        var finalJSON = jsonObjectBuilderMain.build();

                        var rpayload = obj.createObjectNode();
                        rpayload.put(STATUS, Stubs.ACCEPTED_STATUS);
                        rpayload.put("data", finalJSON.toString());
                        response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                        logger.debug(Stubs.RESPONSE_IS, response);

                } else {
                    response = Response.status(Response.Status.FORBIDDEN).build();
                }
        } catch (SQLException | URISyntaxException | IOException | InterruptedException e1) {
            if (e1 instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Login Viewing Error: {}", e1.getMessage(), e1);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug(Stubs.STATUS_OF, Response.Status.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    /**
     * Indicates that the annotated method responds to HTTP GET requests.
     */
    @GET
    @Path("notification")
    @Produces(MediaType.APPLICATION_JSON)
    public Response notification() {

        Response response;
        var obj = new ObjectMapper();
        String reply = null;

        try (var conn = ConnectionPools.getCloudBankConnection()) {
            reply = Stubs.getNotifications(conn);
        } catch (SQLException e) {
            logger.error("FETCH NOTIFICATIONS ERROR");
        }

            if(reply!=null) {
                var rpayload = obj.createObjectNode();
                rpayload.put(STATUS, Stubs.ACCEPTED_STATUS);
                rpayload.put("data", reply);
                rpayload.put("participant", Stubs.SPAN_CLOUDBANK);
                response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                logger.debug(Stubs.RESPONSE_IS, response);
            }else{
                response = Response.status(Response.Status.BAD_REQUEST).build();
            }

        return response;
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("newBankAccount")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newBankAccount(Accounts payload) {
        Response response;
        var obj = new ObjectMapper();
        var sagaInfo = new CloudBankSagaInfo();
        String bank =null;
        Span span = null;

        try (var conn = ConnectionPools.getCloudBankConnection()) {
            bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
        } catch (SQLException e) {
            logger.error("FETCH bank ERROR");
        }              

        try{

            Saga saga = this.beginSaga();
            var sagaId = saga.getSagaId();
            
            span = initializeTracingSpan(sagaId, null, "new-bank-account", "new-bank-account");
            span.setAttribute(AttributeKey.stringKey(Stubs.SPAN_USER_ID), payload.getUcid() != null ? payload.getUcid() : "unknown");
            span.setAttribute(AttributeKey.stringKey("bank"), bank != null ? bank : "unknown");
            
            span.makeCurrent();  
           
            span.addEvent("New bank account saga started");

            logger.debug("New Bank Account saga id: {}", sagaId);
            sagaInfo.setSagaId(sagaId);
            sagaInfo.setNewBA(Boolean.TRUE);
            sagaInfo.setAccounts(Boolean.TRUE);
            sagaInfo.setRequestAccounts(payload);
            sagaInfo.setFromBank(bank);
            cloudBankSagaCache.put(sagaId, sagaInfo);
            logBookUpdateCLoudBank(sagaInfo, Stubs.PENDING, Stubs.NEW_ACCOUNT);

            if(bank!=null){
                span.addEvent("Sending request to bank: " + bank);
                if(bank.equalsIgnoreCase(Stubs.BANK_A)){
                    saga.sendRequest(Stubs.BANK_A, payload.toString());
                }else{
                    saga.sendRequest(Stubs.BANK_B, payload.toString());
                }
                span.addEvent("Request sent to bank");
            }

            var finalPayload = obj.createObjectNode();
            finalPayload.put(STATUS, Stubs.ACCEPTED_STATUS);
            finalPayload.put(Stubs.RESPONSE_REASON, "Your new account is being created. You will receive a notification once its created.");
            finalPayload.put("id", sagaId);
            response = Response.status(Response.Status.ACCEPTED)
                    .header("X-Zipkin-Trace-Id", span.getSpanContext().getTraceId())
                    .header("X-Saga-Id", sagaId)
                    .entity(finalPayload.toString())
                    .build();
            logger.debug(Stubs.RESPONSE_IS, response);
            logBookUpdateCLoudBank(sagaInfo, Stubs.ONGOING, Stubs.NEW_ACCOUNT);
            span.addEvent("New bank account request submitted successfully");
            span.setStatus(StatusCode.OK);
        }catch (Exception e1) {
            span.setStatus(StatusCode.ERROR, e1.getMessage());
            span.recordException(e1);
            logger.error("NEW ACCOUNT CREATION ERROR");
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug(Stubs.STATUS_OF, Response.Status.INTERNAL_SERVER_ERROR);
            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.NEW_ACCOUNT);
        } finally {
            if(span!=null){
                span.end();
            }
        }
        return response;
    }

    private String convertSagaIdToTraceId(String sagaId) {
        String cleanSagaId = sagaId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();

        if (cleanSagaId.length() == 32) {
            return cleanSagaId;
        }

        if (cleanSagaId.length() < 32) {
            return String.format("%-32s", cleanSagaId).replace(' ', '0');
        }

        if (cleanSagaId.length() > 32) {
            return cleanSagaId.substring(0, 32);
        }

        return String.format("%032x", sagaId.hashCode() & 0xFFFFFFFFL)
                .substring(0, 32);
    }

    private String generateSpanId() {
        return String.format("%016x", System.nanoTime() & 0xFFFFFFFFL);
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("transfer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response transfer(AccountTransferDTO payload) {
        Response response;
        var obj = new ObjectMapper();
        var sagaInfo = new CloudBankSagaInfo();
        Span span = null;
        try {
            Saga saga = this.beginSaga();
            var sagaId = saga.getSagaId();
           
            span = initializeTracingSpan(sagaId, null, "money-transfer", "money-transfer");
            span.setAttribute(AttributeKey.stringKey(Stubs.SPAN_USER_ID),
                            payload.getUcid() != null ? payload.getUcid() : "unknown");
            span.setAttribute(AttributeKey.doubleKey("amount"),
                            payload.getAmount() != null ? Double.valueOf(payload.getAmount()) : 0.0);

            span.makeCurrent();
            span.addEvent("Money transfer saga started");
            logger.debug("New transfer saga id: {}", sagaId);
            sagaInfo.setSagaId(sagaId);
            sagaInfo.setAccTransfer(Boolean.TRUE);
            sagaInfo.setAccounts(Boolean.TRUE);
            sagaInfo.setAccountTransferPayload(payload);
            sagaInfo.setDepositResponse(Boolean.FALSE);
            sagaInfo.setWithdrawResponse(Boolean.FALSE);
            sagaInfo.setIsWithdrawalCheck(Boolean.FALSE);

            String bank = null;
            Boolean same = Boolean.FALSE;

            try (var conn = ConnectionPools.getCloudBankConnection()) {
                bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
                same = Stubs.bankCompare(bank, payload.getToAccountNumber());
            }

            sagaInfo.setFromBank(bank);
            if (bank != null) {
                if (same.equals(Boolean.TRUE)) {
                    sagaInfo.setToBank(bank);
                } else {
                    if (bank.equalsIgnoreCase(Stubs.BANK_A)) {
                        sagaInfo.setToBank(Stubs.BANK_B);
                    } else {
                        sagaInfo.setToBank(Stubs.BANK_A);
                    }
                }
            }

            span.setAllAttributes(Attributes.builder()
                    .put(AttributeKey.stringKey("from.bank"),
                            sagaInfo.getFromBank() != null ? sagaInfo.getFromBank() : "unknown")
                    .put(AttributeKey.stringKey("to.bank"),
                            sagaInfo.getToBank() != null ? sagaInfo.getToBank() : "unknown")
                    .put(AttributeKey.booleanKey("same.bank"), same)
                    .build());

            cloudBankSagaCache.put(sagaId, sagaInfo);
            logBookUpdateCLoudBank(sagaInfo, Stubs.PENDING, Stubs.WITHDRAWAL_CHECK);
            logBookUpdateCLoudBank(sagaInfo, Stubs.PENDING, Stubs.TRANSFER);
            Boolean verificationFailed = Boolean.FALSE;
            var conn1 = ConnectionPools.getCloudBankConnection();
            if (Stubs.verifyUserForTransaction(conn1, payload)) {
                span.addEvent("User verification successful");
                JsonObject jsonObject;
                try (JsonReader reader = Json.createReader(new StringReader(payload.toString()))) {
                    jsonObject = reader.readObject();
                }

                span.addEvent("Processing Withdrawal check");
                var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject).add(Stubs.OPERATIONTYPE,
                        "WITHDRAWAL_CHECK").add(Stubs.TRANSACTIONTYPE, "DEBIT");
                if (bank != null) {
                    if (bank.equalsIgnoreCase(Stubs.BANK_A)) {
                        saga.sendRequest(Stubs.BANK_A, jsonObjectBuildertemp.build().toString());
                    } else {
                        saga.sendRequest(Stubs.BANK_B, jsonObjectBuildertemp.build().toString());
                    }
                }
                span.addEvent("Withdrawal check request sent");
            } else {
                span.addEvent("User verification failed");
                verificationFailed = Boolean.TRUE;
                logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.WITHDRAWAL_CHECK);
                logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.TRANSFER);
            }

            var finalPayload = obj.createObjectNode();
            if (verificationFailed == Boolean.TRUE) {
                finalPayload.put(STATUS, Stubs.DECLINED_STATUS);
                finalPayload.put(Stubs.RESPONSE_REASON, "Transfer process stopped. User verification failed.");
                finalPayload.put("id", sagaId);
                response = Response.status(Response.Status.UNAUTHORIZED)
                        .header("X-Zipkin-Trace-Id", span.getSpanContext().getTraceId())
                        .header("X-Saga-Id", sagaId)
                        .entity(finalPayload.toString())
                        .build();
                logger.debug(Stubs.RESPONSE_IS, response);
                span.addEvent("Money transfer request failed");
                span.setStatus(StatusCode.ERROR);

            } else {
                finalPayload.put(STATUS, Stubs.ACCEPTED_STATUS);
                finalPayload.put(Stubs.RESPONSE_REASON, "Transfer process started. You will be updated shortly.");
                finalPayload.put("id", sagaId);
                response = Response.status(Response.Status.ACCEPTED)
                        .header("X-Zipkin-Trace-Id", span.getSpanContext().getTraceId())
                        .header("X-Saga-Id", sagaId)
                        .entity(finalPayload.toString())
                        .build();
                logger.debug(Stubs.RESPONSE_IS, response);
                logBookUpdateCLoudBank(sagaInfo, Stubs.ONGOING, Stubs.WITHDRAWAL_CHECK);
                span.addEvent("Money transfer request submitted successfully");
                span.setStatus(StatusCode.OK);
            }
        } catch (SagaException | SQLException e1) {
            logger.error("TRANSFER ERROR");
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug(Stubs.STATUS_OF, Response.Status.INTERNAL_SERVER_ERROR);
            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.WITHDRAWAL_CHECK);
            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.TRANSFER);
            span.setStatus(StatusCode.ERROR, e1.getMessage());
            span.recordException(e1);
        } finally {
            span.end();
        }
        return response;
    }

    /**
     * If a resource method executes in the context of an LRA and if the containing class has a method annotated
     * with @Compensate then this method will be invoked if the LRA is cancelled.
     */
    @Compensate
    public void onPostRollback(SagaMessageContext info) {
        
        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-post-rollback", "compensate");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Post-rollback processing started");

            logger.debug("After Rollback from {} for {}", info.getSender(), info.getSagaId());
            CloudBankSagaInfo sagaInfo = null;
            Cache<String, CloudBankSagaInfo> cachedSagaInfo = cacheManager
                    .getCache(Stubs.CACHE_NAME, String.class, CloudBankSagaInfo.class);

            sagaInfo = cachedSagaInfo.get(info.getSagaId());

            if (sagaInfo != null) {
                span.addEvent("Saga info found in cache");
                if (sagaInfo.isNewBA()) {
                    span.addEvent("Rolling back new bank account");
                    logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.NEW_ACCOUNT);
                }
                if (sagaInfo.isAccTransfer()) {
                    span.addEvent("Rolling back account transfer");
                    logBookUpdateCLoudBank(sagaInfo, Stubs.COMPLETED, Stubs.WITHDRAWAL_CHECK);
                    logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.TRANSFER);
                }
            } else {
                span.addEvent("Saga info not found in cache");
            }

            span.addEvent("Post-rollback processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
    }

    /**
     * @interface BeforeComplete
     * Any method annotated with @BeforeComplete will be invoked during saga finalization before a saga is committed.
     * The method annotated with @BeforeComplete is invoked before automatic completion for any lockless reservations performed by the saga.
     * The use of @BeforeComplete is optional.
     */
    @BeforeComplete
    public void onPreCommit(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-pre-commit", "pre-commit");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Pre-commit processing started");
            logger.debug("Before Commit from {} for {}", info.getSender(), info.getSagaId());
            span.addEvent("Pre-commit processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
    }

    /**
     * @interface InviteToJoin
     * Defined by a participant, this method will be invoked when the initiator requests that this participant join a given saga (via Saga.sendRequest(java.lang.String, java.lang.String)).
     * If the method returns true, the participant joins the saga.
     * Otherwise, a negative acknowledgement is returned, and Reject is invoked.
     * The use of @InviteToJoin is optional
     */
    @InviteToJoin
    public boolean onInviteToJoin(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-invite-to-join", "invite-to-join");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Processing invite to join saga");
            logger.debug("CloudBank received invite to join saga {}", info.getSagaId());

            span.setAttribute(AttributeKey.booleanKey("invite.accepted"), Boolean.TRUE);
            span.addEvent("Invite accepted");
            span.setStatus(StatusCode.OK);

            return Boolean.TRUE;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return false;
        } finally {
            span.end();
        }
    }

    /**
     * @interface Request
     * The @Request annotation is used to annotate a method that receives incoming requests from saga initiators.
     * The saga framework provides a SagaMessageContext object as an input to the annotated method.
     * If the participant is working with multiple initiators, an optional sender attribute can be specified (regular expressions are allowed) to differentiate between them.
     */
    @Request(sender = ".*")
    public String onRequest(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-request-received", "request");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Processing incoming saga request");

            if (info.getPayload() != null && !info.getPayload().isEmpty()) {
                span.setAttribute(AttributeKey.longKey("payload.length"), (long) info.getPayload().length());
                span.addEvent("Request payload received");
            }

            span.addEvent("Request processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
        return null;
    }

    /**
     * If a resource method executes in the context of an LRA and if the containing class has a method annotated
     * with @Complete (as well as method annotated with @Compensate) then this Complete method will be invoked
     * if the LRA is closed.
     */
    @Complete
    public void onPostCommit(SagaMessageContext info) {
        
        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-post-commit", "complete");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Post-commit processing started");
            logger.debug("After Commit from {} for {}", info.getSender(), info.getSagaId());
            span.addEvent("Post-commit processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
    }

    /**
     * @interface BeforeCompensate
     * Any method annotated with @BeforeCompensate will be invoked during saga finalization before a saga is rolled back.
     * The method annotated with @BeforeCompensate is invoked before automatic compensation for any lockless reservations performed by the saga.
     * The use of @BeforeCompensate is optional.
     */
    @BeforeCompensate
    public void onPreRollback(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-pre-rollback", "pre-rollback");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Pre-rollback processing started");
            logger.debug("Before Rollback from {} for {}", info.getSender(), info.getSagaId());
            span.addEvent("Pre-rollback processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
    }

    /**
     * @interface Response
     * Initiators use the @Response annotation to annotate methods that collect responses from saga participants
     * enrolled into a saga using the Saga.sendRequest(java.lang.String, java.lang.String) API.
     * The saga framework provides a SagaMessageContext object as an input to the annotated method.
     * If the initiator is working with multiple participants, an optional sender attribute can be specified
     * (regular expressions are allowed) to differentiate between them.
     */
    @oracle.saga.annotation.Response(sender = "BankChicago.*")
    public void onResponseBankChicago(SagaMessageContext info) {
        
        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-response-bank-chicago", "bank-response");
        span.setAttribute(AttributeKey.stringKey("bank.name"), "BankChicago");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Processing response from BankChicago");

            logger.info("Response(BankChicago) from {} for saga {}: {}", info.getSender(), info.getSagaId(),
                    info.getPayload());

            if (info.getPayload() != null) {
                span.setAttribute(AttributeKey.longKey("response.payload.length"), (long) info.getPayload().length());
                span.addEvent("Response payload received from BankChicago");
            }

            handleResponse(info);

            span.addEvent("BankChicago response processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * @interface Response
     * Initiators use the @Response annotation to annotate methods that collect responses from saga participants
     * enrolled into a saga using the Saga.sendRequest(java.lang.String, java.lang.String) API.
     * The saga framework provides a SagaMessageContext object as an input to the annotated method.
     * If the initiator is working with multiple participants, an optional sender attribute can be specified
     * (regular expressions are allowed) to differentiate between them.
     */
    @oracle.saga.annotation.Response(sender = "BankMex.*")
    public void onResponseBankMex(SagaMessageContext info) {
        
        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "saga-response-bank-mex", "bank-response");
        span.setAttribute(AttributeKey.stringKey("bank.name"), "BankMex");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Processing response from BankMex");

            logger.info("Response(BankMex) from {} for saga {}: {}", info.getSender(), info.getSagaId(),
                    info.getPayload());

            if (info.getPayload() != null) {
                span.setAttribute(AttributeKey.longKey("response.payload.length"), (long) info.getPayload().length());
                span.addEvent("Response payload received from BankMex");
            }

            handleResponse(info);

            span.addEvent("BankMex response processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * handleResponse is used to handle response received from  different participants.
     */
    public void handleResponse(SagaMessageContext info) {
        Saga saga = null;
        CloudBankSagaInfo sagaInfo = null;
        Cache<String, CloudBankSagaInfo> cachedSagaInfo = cacheManager
                .getCache(Stubs.CACHE_NAME, String.class, CloudBankSagaInfo.class);
        try {
            sagaInfo = cachedSagaInfo.get(info.getSagaId());
            if (!sagaInfo.isRollbackPerformed()) {
                saga = info.getSaga();
            } else {
                logger.error("Saga {} Already Rolled Back", info.getSagaId());
            }
        } catch (SagaException e) {
            logger.error("Error in handling response");
        }

        if (saga != null) {
            sagaInfo.addReply(info.getSender());
            Reader inputReader = new StringReader(info.getPayload());
            var factory = new OracleJsonFactory();
            try (OracleJsonParser parser = factory.createJsonTextParser(inputReader)) {
                parser.next();
                OracleJsonObject currentJsonObj = parser.getObject();
                String result = currentJsonObj.get("result").toString().replaceAll(Stubs.REPLACE_STRING, "");

                if (!result.equals("success")) {
                    if (!sagaInfo.isRollbackPerformed()) {

                        logger.info("Rollingback Saga [{}]", info.getSagaId());
                        saga.rollbackSaga();
                        sagaInfo.setRollbackPerformed(true);

                    } else {
                        logger.info("Saga {} Already Rolled Back", info.getSagaId());
                    }
                }
                if (!sagaInfo.isRollbackPerformed()) {
                    if (info.getSender().equalsIgnoreCase(Stubs.BANK_A)
                            || info.getSender().equalsIgnoreCase(Stubs.BANK_B)) {
                        if (sagaInfo.isAccountsResponse() && sagaInfo.isAccountsSecondResponse()) {
                            sagaInfo.setAccountsThirdResponse(true);
                        } else if (sagaInfo.isAccountsResponse() && !sagaInfo.isAccountsSecondResponse()) {
                            sagaInfo.setAccountsSecondResponse(true);
                        } else if (!sagaInfo.isAccountsResponse() && !sagaInfo.isAccountsSecondResponse()) {
                            sagaInfo.setAccountsResponse(true);
                        }
                    }
                }

                if (sagaInfo.isAccountsResponse() && sagaInfo.isNewBA() && !sagaInfo.isRollbackPerformed()) {

                    logger.info(Stubs.COMMITTING_SAGA, info.getSagaId());
                    saga.commitSaga();
                    logBookUpdateCLoudBank(sagaInfo, Stubs.COMPLETED, Stubs.NEW_ACCOUNT);

                } else {
                    logger.debug("{}: replies:{}, getRollbackPerformed[{}] ",
                            info.getSagaId(), sagaInfo.getReplies(), sagaInfo.isRollbackPerformed());
                }

                if (sagaInfo.isAccountsResponse() && sagaInfo.isAccTransfer() && !sagaInfo.isRollbackPerformed()) {
                    String operationType = currentJsonObj.get(Stubs.OPERATIONTYPE).toString()
                            .replaceAll(Stubs.REPLACE_STRING, "");
                    if (operationType.equals(Stubs.DEPOSIT)) {
                        sagaInfo.setDepositResponse(Boolean.TRUE);
                    } else if (operationType.equals(Stubs.WITHDRAW)) {
                        sagaInfo.setWithdrawResponse(Boolean.TRUE);
                    } else if (operationType.equalsIgnoreCase("TRANSACT")) {
                        sagaInfo.setDepositResponse(Boolean.TRUE);
                        sagaInfo.setWithdrawResponse(Boolean.TRUE);
                    } else if (operationType.equalsIgnoreCase("WITHDRAWAL_CHECK")) {
                        sagaInfo.setIsWithdrawalCheck(Boolean.TRUE);
                    }

                    if (sagaInfo.isIsWithdrawalCheck() && sagaInfo.getDepositResponse()
                            && (sagaInfo.getWithdrawResponse() == Boolean.TRUE)) {

                        logger.info(Stubs.COMMITTING_SAGA, info.getSagaId());
                        saga.commitSaga();
                        logBookUpdateCLoudBank(sagaInfo, Stubs.COMPLETED, Stubs.TRANSFER);
                    }

                    if ((sagaInfo.isIsWithdrawalCheck() == Boolean.TRUE)
                            && (sagaInfo.getDepositResponse() == Boolean.FALSE)
                            && (sagaInfo.getWithdrawResponse() == Boolean.FALSE)) {
                        logBookUpdateCLoudBank(sagaInfo, Stubs.COMPLETED, Stubs.WITHDRAWAL_CHECK);
                        try (var conn = ConnectionPools.getCloudBankConnection()) {
                            String bank = sagaInfo.getFromBank();
                            AccountTransferDTO payload = sagaInfo.getAccountTransferPayload();
                            Boolean same = Stubs.bankCompare(bank, payload.getToAccountNumber());
                            if (Stubs.verifyUserForTransaction(conn, payload)) {
                                JsonObject jsonObject;
                                try (JsonReader reader = Json.createReader(new StringReader(payload.toString()))) {
                                    jsonObject = reader.readObject();
                                }

                                if (same.equals(Boolean.TRUE)) {
                                    var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject).add(
                                            Stubs.OPERATIONTYPE,
                                            "TRANSACT");
                                    if (bank != null) {
                                        if (bank.equalsIgnoreCase(Stubs.BANK_A)) {
                                            saga.sendRequest(Stubs.BANK_A, jsonObjectBuildertemp.build().toString());
                                        } else {
                                            saga.sendRequest(Stubs.BANK_B, jsonObjectBuildertemp.build().toString());
                                        }
                                    }
                                } else {
                                    if (bank != null) {
                                        if (bank.equalsIgnoreCase(Stubs.BANK_A)) {
                                            var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject)
                                                    .add(Stubs.OPERATIONTYPE, Stubs.DEPOSIT).add(Stubs.TRANSACTIONTYPE,
                                                            "CREDIT");
                                            saga.sendRequest(Stubs.BANK_B, jsonObjectBuildertemp.build().toString());
                                            var jsonObjectBuildertemp1 = Json.createObjectBuilder(jsonObject)
                                                    .add(Stubs.OPERATIONTYPE, Stubs.WITHDRAW).add(Stubs.TRANSACTIONTYPE,
                                                            "DEBIT");
                                            saga.sendRequest(Stubs.BANK_A, jsonObjectBuildertemp1.build().toString());
                                        } else {
                                            var jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject)
                                                    .add(Stubs.OPERATIONTYPE, Stubs.DEPOSIT).add(Stubs.TRANSACTIONTYPE,
                                                            "CREDIT");
                                            saga.sendRequest(Stubs.BANK_A, jsonObjectBuildertemp.build().toString());
                                            var jsonObjectBuildertemp1 = Json.createObjectBuilder(jsonObject)
                                                    .add(Stubs.OPERATIONTYPE, Stubs.WITHDRAW).add(Stubs.TRANSACTIONTYPE,
                                                            "DEBIT");
                                            saga.sendRequest(Stubs.BANK_B, jsonObjectBuildertemp1.build().toString());
                                        }
                                    }

                                }
                                logBookUpdateCLoudBank(sagaInfo, Stubs.ONGOING, Stubs.TRANSFER);
                            }
                        } catch (SagaException | SQLException e1) {
                            logger.error("TRANSFER ERROR");
                            logBookUpdateCLoudBank(sagaInfo, Stubs.FAILED, Stubs.TRANSFER);
                            saga.rollbackSaga();
                            sagaInfo.setRollbackPerformed(true);
                        }

                    }

                } else {
                    logger.debug("{}: replies:{}, getRollbackPerformed[{}] ",
                            info.getSagaId(), sagaInfo.getReplies(), sagaInfo.isRollbackPerformed());
                }

            } catch (SagaException e1) {
                logger.error("Unknown error");
                try {
                    saga.rollbackSaga();
                } catch (SagaException e) {
                    logger.error("Unable to rollback after encountering error");
                }
                cachedSagaInfo.remove(info.getSagaId());
            }
        } else {
            logger.error("Saga is null for: {} ", info.getSagaId());
        }
    }

    /**
     * logBookUpdateCLoudBank is used to insert / update changes in the cloudbank_book
     */
    private void logBookUpdateCLoudBank(CloudBankSagaInfo sagaInfo, String state, String operationType) {

        var queryInsert = "INSERT INTO cloudbank_book (saga_id, ucid, operationType, operation_status, created_at, transfer_type) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP,?)";
        var queryUpdate = "UPDATE cloudbank_book set operation_status = ? where saga_id = ? and operationType = ?";

        try (var conn = ConnectionPools.getCloudBankConnection()){

            if(state.equals(Stubs.PENDING)){
                try (PreparedStatement insertStmt = conn.prepareStatement(queryInsert)) {
                    insertStmt.setString(1, sagaInfo.getSagaId());
                    switch (operationType) {
                        case Stubs.NEW_ACCOUNT:
                            insertStmt.setString(2, sagaInfo.getRequestAccounts().getUcid());
                            break;
                        case Stubs.TRANSFER:
                            insertStmt.setString(2, sagaInfo.getAccountTransferPayload().getUcid());
                            break;
                        case Stubs.WITHDRAWAL_CHECK:
                            insertStmt.setString(2, sagaInfo.getAccountTransferPayload().getUcid());
                            break;
                        default:
                            break;
                    }

                    insertStmt.setString(3, operationType);
                    insertStmt.setString(4, state);
                    if(operationType.equalsIgnoreCase(Stubs.TRANSFER) || operationType
                            .equalsIgnoreCase(Stubs.WITHDRAWAL_CHECK)){
                        if(sagaInfo.getFromBank().equalsIgnoreCase(sagaInfo.getToBank())){
                            insertStmt.setString(5, "INTRA-BANK");
                        }else{
                            insertStmt.setString(5, "INTER-BANK");
                        }
                    }else{
                        insertStmt.setString(5, "null");
                    }

                    insertStmt.executeUpdate();
                }
            }else {
                try (PreparedStatement updateStmt = conn.prepareStatement(queryUpdate)) {
                    updateStmt.setString(1, state);
                    updateStmt.setString(2, sagaInfo.getSagaId());
                    updateStmt.setString(3, operationType);

                    int rowsAffected = updateStmt.executeUpdate();

                    if(rowsAffected!=1){
                        logger.error("Unable to update sags status in cloudbank_book");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Connection error logbookUpdateCloudBank");
        }
    }

    private Span initializeTracingSpan(String sagaid, String sender, String spanName, String operationType){

        String traceId = convertSagaIdToTraceId(sagaid);
        String spanId = generateSpanId();
        SpanContext customContext = SpanContext.createFromRemoteParent(
                traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
        Context customRootContext = Context.root().with(Span.wrap(customContext));

        Span span = otelTracer.spanBuilder(spanName)
                .setParent(customRootContext)
                .startSpan();

        span.setAllAttributes(Attributes.builder()
                .put(AttributeKey.stringKey(Stubs.SPAN_SAGA_ID), 
                        sagaid)
                .put(AttributeKey.stringKey(Stubs.SPAN_ENTITY_NAME), Stubs.SPAN_CLOUDBANK)
                .put(AttributeKey.stringKey(Stubs.SPAN_OPERATION_TYPE), operationType)
                .put(AttributeKey.stringKey(Stubs.SPAN_SENDER), sender)
                .build());
        return span;
    }
}

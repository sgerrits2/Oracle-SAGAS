/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.bankb.controller;

import com.oracle.saga.cloudbank.bankb.data.BankBLogDTO;
import com.oracle.saga.cloudbank.bankb.data.CompensationData;
import com.oracle.saga.cloudbank.bankb.data.ViewBADTO;
import com.oracle.saga.cloudbank.bankb.exception.AccountsException;
import com.oracle.saga.cloudbank.bankb.stubs.AccountsService;
import com.oracle.saga.cloudbank.bankb.util.ConnectionPools;
import com.oracle.saga.cloudbank.bankb.util.PropertiesHelper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
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
import oracle.saga.SagaException;
import oracle.saga.SagaMessageContext;
import oracle.saga.SagaParticipant;
import oracle.saga.annotation.*;
import oracle.sql.json.OracleJsonException;
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

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * AccountsController is the controller class for the respective Participant.
 * @Participant annotation needs to be mentioned to map the class to the respective participant.
 * SagaParticipant needs to be extended for this class to support Oracle Saga Annotations.
 */
@Path("/")
@Singleton
@Participant(name = "BankMex")
public class AccountsController extends SagaParticipant {

    private static final String REG_EXP_REMOVE_QUOTES = "(^\")|(\"$)";
    private static final String FAILURE = "{\"result\":\"failure\"}";
    private static final Logger logger = LoggerFactory.getLogger(AccountsController.class);
    final CacheManager cacheManager;
    public static final String RESULTSUCCESS = "{\"result\":\"success\",\"balance_amount\":\"";
    public static final String ERROR = "Error viewing accounts!!!";
    public static final String RESPONSE_IS = "The response: {}";

    private static final Tracer otelTracer =
            GlobalOpenTelemetry.getTracer("com.oracle.saga.cloudbank.bankB");

    /**
     * Constructor to initialize the cache and set different values.
     */
    public AccountsController() throws SagaException {
        Properties p = PropertiesHelper.loadProperties();
        int cacheSize = Integer.parseInt(p.getProperty("cacheSize", "100000"));
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        cacheManager.createCache("bankBCompensationData",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class,
                        ArrayList.class, ResourcePoolsBuilder.heap(cacheSize)));
    }

    @SagaConnection
    public static Connection getAccountsConnection() throws SQLException {
        return ConnectionPools.getAccountsConnection();
    }

    /**
     * The PreDestroy annotation is used on a method as a callback notification to
     * signal that the instance is in the process of being removed by the container.
     */
    @Override
    @PreDestroy
    public void close() {
        try {
            logger.debug("Shutting down Bank B Controller");
            super.close();
        } catch (SagaException e) {
            logger.error("Unable to shutdown Bank B initiator");
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
     * If a resource method executes in the context of an LRA and if the containing class has a method annotated
     * with @Compensate then this method will be invoked if the LRA is cancelled.
     */
    @Compensate
    public void onPostRollback(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-post-rollback", "compensate");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("BankMex post-rollback processing started");

            Connection connection = null;
            connection = info.getConnection();
            span.addEvent("Database connection obtained");

            Cache<String, ArrayList> cachedCompensationInfo = cacheManager
                    .getCache("bankBCompensationData", String.class, ArrayList.class);

            if (cachedCompensationInfo.containsKey(info.getSagaId())) {
                span.addEvent("Compensation data found in cache");
                ArrayList<CompensationData> accountCompensationInfo = cachedCompensationInfo.get(info.getSagaId());
                span.setAttribute(AttributeKey.longKey("compensation.accounts.count"),
                        (long) accountCompensationInfo.size());

                AccountsService as = new AccountsService(connection, this.cacheManager);

                for (CompensationData account : accountCompensationInfo) {
                    if (account.getOperationType().equals("NEW_BANK_ACCOUNT")) {
                        span.addEvent("Compensating account deletion: " + account.getAccountNumber());
                        boolean check = as.deleteAccount(account);
                        if (!check) {
                            logger.error("Unable to remove account {} from accounts.", account.getAccountNumber());
                            span.addEvent("Failed to delete account: " + account.getAccountNumber());
                        } else {
                            logger.debug("Account {} was successfully removed from accounts.",
                                    account.getAccountNumber());
                            span.addEvent("Successfully deleted account: " + account.getAccountNumber());
                        }
                    }
                    as.updateOperationStatus(info.getSagaId(), AccountsService.FAILED);
                }
                span.addEvent("All compensation operations completed");
            } else {
                span.addEvent("No compensation data found in cache");
            }

            span.addEvent("BankMex post-rollback processing completed");
            span.setStatus(StatusCode.OK);

        } catch (AccountsException e) {
            logger.error("Bank B Response");
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } catch (SagaException e) {
            logger.error("Unable to get database connection for accounts service");
            span.addEvent("Failed to get database connection");
            span.recordException(e);
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

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-pre-commit", "pre-commit");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("BankMex pre-commit processing started");

            logger.debug("Before Commit(SMC) from {} for {}", info.getSender(), info.getSagaId());
            Connection connection = null;

            long start = System.currentTimeMillis();
            long end;

            connection = info.getConnection();
            span.addEvent("Database connection obtained");

            span.addEvent("Updating operation status to COMPLETED");
            AccountsService as = new AccountsService(connection, this.cacheManager);
            as.updateOperationStatus(info.getSagaId(), AccountsService.COMPLETED);
            span.addEvent("Operation status updated successfully");

            end = System.currentTimeMillis();
            long duration = end - start;

            span.setAttribute(AttributeKey.longKey("processing.duration.ms"), duration);
            span.addEvent("BankMex pre-commit processing completed");

            logger.debug("Status of compensation, rt: {}", duration);
            span.setStatus(StatusCode.OK);

        } catch (AccountsException e) {
            logger.error("Bank B Response");
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } catch (SagaException e) {
            logger.error("Unable to get database connection for accounts service");
            span.addEvent("Failed to get database connection");
            span.recordException(e);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
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
     * @interface InviteToJoin
     * Defined by a participant, this method will be invoked when the initiator requests that this participant join a given saga (via Saga.sendRequest(java.lang.String, java.lang.String)).
     * If the method returns true, the participant joins the saga.
     * Otherwise, a negative acknowledgement is returned, and Reject is invoked.
     * The use of @InviteToJoin is optional
     */
    @InviteToJoin
    public boolean onInviteToJoin(SagaMessageContext info) {
        
        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-invite-to-join", "invite-to-join");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("Processing invite to join saga");
            logger.info("Joining saga: {}", info.getSagaId());

            boolean result = true;

            span.setAttribute(AttributeKey.booleanKey("invite.accepted"), result);
            span.addEvent("BankMex accepted invite" );
            span.setStatus(StatusCode.OK);

            return result;

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return false;
        } finally {
            span.end();
        }
    }

    /**
     * If a resource method executes in the context of an LRA and if the containing class has a method annotated
     * with @Complete (as well as method annotated with @Compensate) then this Complete method will be invoked
     * if the LRA is closed.
     */
    @Complete
    public void onPostCommit(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-post-commit", "complete");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("BankMex post-commit processing started");
            logger.debug("After Commit from {} for {}", info.getSender(), info.getSagaId());
            span.addEvent("BankMex post-commit processing completed");
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

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-pre-rollback", "pre-rollback");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("BankMex pre-rollback processing started");
            logger.debug("Before Rollback from {} for {}", info.getSender(), info.getSagaId());
            span.addEvent("BankMex pre-rollback processing completed");
            span.setStatus(StatusCode.OK);

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("viewAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewAll(ViewBADTO payload) {
        
        Response response;
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection()) {
            details = AccountsService.viewAllAccounts(conn, payload, "ALL");
        } catch (SQLException ex) {
            logger.error(ERROR);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug(RESPONSE_IS, response);
        return response;
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("viewBAC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewBAC(ViewBADTO payload) {

        Response response;
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection()) {
            details = AccountsService.viewAllAccounts(conn, payload,"CHECKING");
        } catch (SQLException ex) {
            logger.error(ERROR);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug(RESPONSE_IS, response);
        return response;
    }

    /**
     * Indicates that the annotated method responds to HTTP POST requests.
     */
    @POST
    @Path("viewBAS")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewBAS(ViewBADTO payload) {

        Response response;
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection()) {
            details = AccountsService.viewAllAccounts(conn, payload,"SAVING");
        } catch (SQLException ex) {
            logger.error(ERROR);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug(RESPONSE_IS, response);
        return response;
    }

    /**
     * @interface Request
     * The @Request annotation is used to annotate a method that receives incoming requests from saga initiators.
     * The saga framework provides a SagaMessageContext object as an input to the annotated method.
     * If the participant is working with multiple initiators, an optional sender attribute can be specified (regular expressions are allowed) to differentiate between them.
     */
    @Request(sender = "CloudBank")
    public String onRequest(SagaMessageContext info) {

        Span span = initializeTracingSpan(info.getSagaId(), info.getSender(), "bank-b-request-processing", "request");

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("BankMex request processing started");

            Connection connection = null;
            String status = FAILURE;

            connection = info.getConnection();
            span.addEvent("Database connection obtained");

            AccountsService account;
            account = new AccountsService(connection, this.cacheManager);

            String accountsAction = parseAccountsAction(info.getPayload());
            span.setAttribute(AttributeKey.stringKey("accounts.action"), accountsAction);
            span.addEvent("Parsed accounts action: " + accountsAction);

            switch (accountsAction) {
                case "new_bank_account":
                    span.addEvent("Processing new bank account creation");
                    String newAccount = account.newBankAccount(info.getPayload(), info.getSagaId());

                    if (newAccount != null) {
                        status = "{\"result\":\"success\",\"account_number\":\"" + newAccount + "\"}";
                        span.setAttribute(AttributeKey.stringKey("new.account.number"), newAccount);
                        span.addEvent("New bank account created successfully: " + newAccount);
                    } else {
                        span.addEvent("Failed to create new bank account");
                    }
                    break;
                case "deposit":
                    span.addEvent("Processing deposit operation");
                    String depositStatus = account.depositMoney(info.getPayload(), info.getSagaId());
                    status = "{\"result\":\"failure\",\"operationType\":\"DEPOSIT\"}";
                    if (Double.parseDouble(depositStatus) != -1) {
                        status = RESULTSUCCESS + depositStatus + "\",\"operationType\":\"DEPOSIT\"}";
                        span.setAttribute(AttributeKey.stringKey("deposit.amount"), depositStatus);
                        span.addEvent("Deposit successful: " + depositStatus);
                    } else {
                        span.addEvent("Deposit failed");
                    }
                    break;
                case "withdraw":
                    span.addEvent("Processing withdraw operation");
                    String withdrawStatus = account.withdrawMoney(info.getPayload(), info.getSagaId());
                    status = "{\"result\":\"failure\",\"operationType\":\"WITHDRAW\"}";
                    if (Double.parseDouble(withdrawStatus) != -1) {
                        status = RESULTSUCCESS + withdrawStatus + "\",\"operationType\":\"WITHDRAW\"}";
                        span.setAttribute(AttributeKey.stringKey("withdraw.amount"), withdrawStatus);
                        span.addEvent("Withdraw successful: " + withdrawStatus);
                    } else {
                        span.addEvent("Withdraw failed");
                    }
                    break;
                case "withdrawal_check":
                    span.addEvent("Processing withdraw_check operation");
                    String withdrawalStatus = account.withdrawMoneyCheck(info.getPayload(), info.getSagaId());
                    status = "{\"result\":\"failure\",\"operationType\":\"WITHDRAWAL_CHECK\"}";
                    if (Double.parseDouble(withdrawalStatus) >= 0) {
                        status = RESULTSUCCESS + withdrawalStatus
                                + "\",\"operationType\":\"WITHDRAWAL_CHECK\"}";
                        span.setAttribute(AttributeKey.stringKey("withdraw.amount"), withdrawalStatus);
                        span.addEvent("Withdrawal_check successful: " + withdrawalStatus);
                    } else {
                        span.addEvent("Withdraw_check failed");
                    }
                    break;
                case "transact":
                    span.addEvent("Processing intra-bank transaction");
                    String transactionStatus = account.transactIntraMoney(info.getPayload(), info.getSagaId());
                    status = "{\"result\":\"failure\",\"operationType\":\"TRANSACT\"}";
                    if (Double.parseDouble(transactionStatus) != -1) {
                        status = RESULTSUCCESS + transactionStatus + "\",\"operationType\":\"TRANSACT\"}";
                        span.setAttribute(AttributeKey.stringKey("transaction.amount"), transactionStatus);
                        span.addEvent("Intra-bank transaction successful: " + transactionStatus);
                    } else {
                        span.addEvent("Intra-bank transaction failed");
                    }
                    break;
                default:
                    logger.error("Invalid Bank B action specified: {}", accountsAction);
                    span.addEvent("Invalid action specified: " + accountsAction);
                    span.setAttribute(AttributeKey.stringKey("error.type"), "invalid_action");
            }

            JsonObject jsonObject;
            try (JsonReader reader = Json.createReader(new StringReader(status))) {
                jsonObject = reader.readObject();
            }
            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder(jsonObject).add("participant", "BankMex");
            JsonObject updatedJsonObject = jsonObjectBuilder.build();
            status = updatedJsonObject.toString();

            span.setAttribute(AttributeKey.stringKey("response.status"), status);
            span.addEvent("Response prepared and participant added");

            logger.info("RESPONSE {}", status);
            span.addEvent("BankMex request processing completed");
            span.setStatus(StatusCode.OK);

            return status;

        } catch (AccountsException e) {
            logger.error("Unable to create new entry in bank A");
            span.addEvent("AccountsException occurred");
            span.recordException(e);
            return FAILURE;
        } catch (SagaException e) {
            logger.error("Unable to get database connection for Bank B");
            span.addEvent("Failed to get database connection");
            span.recordException(e);
            return FAILURE;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return FAILURE;
        } finally {
            span.end();
        }
    }

    /**
     * parseAccountsAction is used to fetch requested account action from the request JSON.
     */
    private String parseAccountsAction(String payload) {
        Reader inputReader = new StringReader(payload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        String accountsAction = "";

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject currentJsonObj = parser.getObject();
            accountsAction = currentJsonObj.get("operationType").toString()
                    .replaceAll(REG_EXP_REMOVE_QUOTES, "").toLowerCase();
        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload");
        }
        return accountsAction;
    }

    /**
     * Fetches top 10 rows from bankA_book table.
     */
    @GET
    @Path("logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRecentBankBLogs(@QueryParam("ucid") String ucid, @QueryParam("accountNumbers") List<String> acctNums) {

        if (ucid == null || ucid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing required query-param `ucid`")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        try (Connection conn = ConnectionPools.getAccountsConnection()) {
            List<BankBLogDTO> logs = AccountsService.fetchRecentBankBLogs(conn, ucid, acctNums);
            Map<String,List<BankBLogDTO>> wrapper = Map.of("data", logs);
            return Response.ok(wrapper).build();
        } catch (SQLException ex) {
            logger.error("Error fetching BankMex logs", ex);
            return Response.serverError()
                    .entity("Could not fetch logs: " + ex.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    private Span initializeTracingSpan(String sagaid, String sender, String spanName, String operationType) {

        String traceId = convertSagaIdToTraceId(sagaid);
        String spanId = generateSpanId();
        SpanContext customContext = SpanContext.createFromRemoteParent(
                traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
        Context customRootContext = Context.root().with(Span.wrap(customContext));

        Span span = otelTracer.spanBuilder(spanName)
                .setParent(customRootContext)
                .startSpan();

        span.setAllAttributes(Attributes.builder()
                .put(AttributeKey.stringKey(AccountsService.SPAN_SAGA_ID),
                        sagaid)
                .put(AttributeKey.stringKey(
                        AccountsService.SPAN_ENTITY_NAME),
                        AccountsService.SPAN_BANK_B)
                .put(AttributeKey.stringKey(
                        AccountsService.SPAN_OPERATION_TYPE), operationType)
                .put(AttributeKey.stringKey(
                        AccountsService.SPAN_SENDER), sender)
                .build());
        return span;
    }

}

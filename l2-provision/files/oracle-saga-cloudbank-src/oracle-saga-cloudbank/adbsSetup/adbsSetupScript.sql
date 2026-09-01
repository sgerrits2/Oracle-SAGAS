-- Arguments mapping:
-- &1 = TNS_ALIAS_CONTAINER
-- &2 = BROKER_USERNAME  
-- &3 = BROKER_PASSWORD
-- &4 = ORCHESTRATOR_USERNAME
-- &5 = ORCHESTRATOR_PASSWORD
-- &6 = OSAGA_BANKA_USERNAME
-- &7 = OSAGA_BANKA_PASSWORD
-- &8 = OSAGA_BANKB_USERNAME
-- &9 = OSAGA_BANKB_PASSWORD

WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DEFINE tns_alias = '&1'
DEFINE broker_username = '&2'
DEFINE broker_password = '&3'
DEFINE orchestrator_username = '&4'
DEFINE orchestrator_password = '&5'
DEFINE banka_username = '&6'
DEFINE banka_password = '&7'
DEFINE bankb_username = '&8'
DEFINE bankb_password = '&9'

-- THIS SETUP SCRIPT ADD'S PARTICIPANT'S, BROKER AND COORDINATOR ON SINGLE PDB
set echo on
set serveroutput on
SET DEFINE ON;

-- Cannot change JOB QUEUE PROCESSES ON ADB ENVIRONMENT
alter system set JOB_QUEUE_PROCESSES=30;



connect &orchestrator_username/&orchestrator_password@&tns_alias

CREATE SEQUENCE SEQ_CLOUDBANK_CUSTOMER_ID
  START WITH 1
  INCREMENT BY 1
  NOMAXVALUE;

CREATE SEQUENCE SEQ_CLOUDBANK_LOG_ID
  START WITH 1
  INCREMENT BY 1
  NOMAXVALUE;

-- TABLE TO HOLD THE CUSTOMER DETAILS
CREATE TABLE cloudbank_customer (
  customer_id VARCHAR2(50) UNIQUE,
  password VARCHAR2(50),
  full_name VARCHAR2(100),
  address VARCHAR2(255),
  phone VARCHAR2(20),
  email VARCHAR2(100) UNIQUE,
  ossn VARCHAR2(10) NOT NULL,
  bank VARCHAR2(20) NOT NULL CHECK (bank IN ('BankChicago', 'BankMex')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP,
  PRIMARY KEY(email,ossn)
);

CREATE OR REPLACE TRIGGER trg_customer_id
  BEFORE INSERT ON cloudbank_customer
    FOR EACH ROW
  BEGIN
      :NEW.customer_id := 'ORACLE' || TO_CHAR(TO_NUMBER(:NEW.customer_id), 'FM000');
  END;
/

-- TABLE TO HOLD ALL THE SAGA STATUS UPDATES PERTAINING TO THIS PARTICIPANT
CREATE TABLE cloudbank_book (
  log_id NUMBER DEFAULT SEQ_CLOUDBANK_LOG_ID.NEXTVAL PRIMARY KEY,
  saga_id VARCHAR2(50),
  ucid VARCHAR2(50) REFERENCES cloudbank_customer(customer_id),
  operationType VARCHAR2(18) CHECK (operationType IN ('VIEW', 'TRANSFER', 'NEW_ACCOUNT','NEW_CREDIT_CARD', 'WITHDRAWAL_CHECK')),
  transfer_type VARCHAR2(20) CHECK (transfer_type IN ('INTER-BANK', 'INTRA-BANK', 'null')),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10)  DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- MOCK LIST OF CUSTOMERS FOR THE CLOUDBANK
INSERT INTO cloudbank_customer (customer_id, password, full_name, address, phone, email, ossn, bank, created_at)
VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL,'cb1', 'CUSTOMER 1', 'CUSTOMER 1 HOME, CALIFORNIA', '555-1234', 'customer1@example.com', 'OSN001', 'BankChicago', DEFAULT);

INSERT INTO cloudbank_customer (customer_id, password, full_name, address, phone, email, ossn, bank, created_at)
VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL,'cb2', 'CUSTOMER 2', 'CUSTOMER 2 HOME, CALIFORNIA', '555-5678', 'customer2@example.com', 'OSN002', 'BankMex', DEFAULT);

INSERT INTO cloudbank_customer (customer_id, password, full_name, address, phone, email, ossn, bank, created_at)
VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL,'cb3', 'CUSTOMER 3', 'CUSTOMER 3 HOME, CALIFORNIA', '555-9012', 'customer3@example.com', 'OSN003', 'BankMex', DEFAULT);

INSERT INTO cloudbank_customer (customer_id, password, full_name, address, phone, email, ossn, bank, created_at)
VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL,'cb4', 'CUSTOMER 4', 'CUSTOMER 4 HOME, CALIFORNIA', '555-3456', 'customer4@example.com', 'OSN004', 'BankChicago', DEFAULT);

DECLARE
  seeded_customer_count PLS_INTEGER;
BEGIN
  SELECT COUNT(*)
  INTO seeded_customer_count
  FROM cloudbank_customer
  WHERE customer_id IN ('ORACLE001', 'ORACLE002', 'ORACLE003', 'ORACLE004');

  IF seeded_customer_count != 4 THEN
    RAISE_APPLICATION_ERROR(-20001, 'CloudBank customer seed validation failed.');
  END IF;
END;
/

commit;



connect &bankb_username/&bankb_password@&tns_alias

CREATE SEQUENCE SEQ_ACCOUNTS_BANK_B_LOGS
  START WITH 1
  INCREMENT BY 1
  NOMAXVALUE;

CREATE SEQUENCE SEQ_ACCOUNT_NUMBER_BANK_B
  START WITH 1234560301
  INCREMENT BY 1
  NOMAXVALUE;

--TABLE TO HOLD ACCOUNT DETAILS AND THEIR RESPECTIVE BALANCE
CREATE TABLE bankB (
  ucid VARCHAR2(50),
  account_number NUMBER(20) PRIMARY KEY,
  account_type VARCHAR2(15) CHECK (account_type IN ('CHECKING', 'SAVING')),
  balance_amount decimal(10,2) reservable constraint balance_conB check(balance_amount >= 0),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- TABLE TO HOLD ALL THE SAGA STATUS UPDATES PERTAINING TO THIS PARTICIPANT
CREATE TABLE bankB_book (
  log_id NUMBER DEFAULT SEQ_ACCOUNTS_BANK_B_LOGS.NEXTVAL PRIMARY KEY,
  saga_id VARCHAR2(100),
  ucid VARCHAR2(50),
  operationType VARCHAR2(30) CHECK (operationType IN ('VIEW_BALANCE_BA', 'WITHDRAW', 'DEPOSIT', 'NEW_BANK_ACCOUNT', 'TRANSACT', 'WITHDRAWAL_CHECK')),
  transactionType VARCHAR2(10) CHECK (transactionType IN ('CREDIT', 'DEBIT','null')),
  transaction_amount decimal(10,2),
  account_number VARCHAR2(100),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10)  DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);


--MOCK LIST OF ACCOUNTS UNDER THIS PARTICULAR BANK
INSERT INTO bankB (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE002', SEQ_ACCOUNT_NUMBER_BANK_B.NEXTVAL, 'SAVING', 2000.00);

INSERT INTO bankB (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE003', SEQ_ACCOUNT_NUMBER_BANK_B.NEXTVAL, 'CHECKING', 2000.00);

DECLARE
  seeded_account_count PLS_INTEGER;
BEGIN
  SELECT COUNT(*)
  INTO seeded_account_count
  FROM bankB
  WHERE account_number IN (1234560301, 1234560302);

  IF seeded_account_count != 2 THEN
    RAISE_APPLICATION_ERROR(-20002, 'BankB account seed validation failed.');
  END IF;
END;
/

commit;


connect &banka_username/&banka_password@&tns_alias

CREATE SEQUENCE SEQ_ACCOUNTS_BANK_A_LOGS
  START WITH 1
  INCREMENT BY 1
  NOMAXVALUE;

CREATE SEQUENCE SEQ_ACCOUNT_NUMBER_BANK_A
  START WITH 1234560001
  INCREMENT BY 1
  NOMAXVALUE;

--TABLE TO HOLD ACCOUNT DETAILS AND THEIR RESPECTIVE BALANCE
CREATE TABLE bankA (
  ucid VARCHAR2(50),
  account_number NUMBER(20) PRIMARY KEY,
  account_type VARCHAR2(15) CHECK (account_type IN ('CHECKING', 'SAVING')),
  balance_amount decimal(10,2) reservable constraint balance_conA check(balance_amount >= 0),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- TABLE TO HOLD ALL THE SAGA STATUS UPDATES PERTAINING TO THIS PARTICIPANT
CREATE TABLE bankA_book (
  log_id NUMBER DEFAULT SEQ_ACCOUNTS_BANK_A_LOGS.NEXTVAL PRIMARY KEY,
  saga_id VARCHAR2(100),
  ucid VARCHAR2(50),
  operationType VARCHAR2(30) CHECK (operationType IN ('VIEW_BALANCE_BA', 'WITHDRAW', 'DEPOSIT', 'NEW_BANK_ACCOUNT', 'TRANSACT', 'WITHDRAWAL_CHECK')),
  transactionType VARCHAR2(10) CHECK (transactionType IN ('CREDIT', 'DEBIT','null')),
  transaction_amount decimal(10,2),
  account_number VARCHAR2(100),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10)  DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

--MOCK LIST OF ACCOUNTS UNDER THIS PARTICULAR BANK
INSERT INTO bankA (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE001', SEQ_ACCOUNT_NUMBER_BANK_A.NEXTVAL, 'CHECKING', 2000.00);

INSERT INTO bankA (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE004', SEQ_ACCOUNT_NUMBER_BANK_A.NEXTVAL, 'SAVING', 2000.00);

DECLARE
  seeded_account_count PLS_INTEGER;
BEGIN
  SELECT COUNT(*)
  INTO seeded_account_count
  FROM bankA
  WHERE account_number IN (1234560001, 1234560002);

  IF seeded_account_count != 2 THEN
    RAISE_APPLICATION_ERROR(-20003, 'BankA account seed validation failed.');
  END IF;
END;
/

commit;

PROMPT Business schema setup: OK

UNDEFINE tns_alias
UNDEFINE broker_username
UNDEFINE broker_password
UNDEFINE orchestrator_username
UNDEFINE orchestrator_password
UNDEFINE banka_username
UNDEFINE banka_password
UNDEFINE bankb_username
UNDEFINE bankb_password

EXIT;

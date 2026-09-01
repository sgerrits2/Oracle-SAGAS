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

DEFINE tns_alias = '&1'
DEFINE broker_username = '&2'
DEFINE broker_password = '&3'
DEFINE orchestrator_username = '&4'
DEFINE orchestrator_password = '&5'
DEFINE banka_username = '&6'
DEFINE banka_password = '&7'
DEFINE bankb_username = '&8'
DEFINE bankb_password = '&9'

set serveroutput on
SET DEFINE ON;

connect &banka_username/&banka_password@&tns_alias

drop sequence SEQ_ACCOUNTS_BANK_A_LOGS;
drop sequence SEQ_ACCOUNT_NUMBER_BANK_A;
drop table bankA cascade constraints;
drop table bankA_book cascade constraints;
exec dbms_saga_adm.drop_participant('BankChicago');

connect &bankb_username/&bankb_password@&tns_alias

drop sequence SEQ_ACCOUNTS_BANK_B_LOGS;
drop sequence SEQ_ACCOUNT_NUMBER_BANK_B;
drop table bankB cascade constraints;
drop table bankB_book cascade constraints;
exec dbms_saga_adm.drop_participant('BankMex');

connect &orchestrator_username/&orchestrator_password@&tns_alias
drop sequence SEQ_CLOUDBANK_CUSTOMER_ID;
drop trigger trg_customer_id;
drop table cloudbank_customer cascade constraints;
drop table cloudbank_book cascade constraints;
exec dbms_saga_adm.drop_participant('CloudBank');
exec dbms_saga_adm.drop_coordinator('CloudBankCoordinator');	

connect &broker_username/&broker_password@&tns_alias
exec dbms_saga_adm.drop_broker(broker_name => 'CloudBankBroker');

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

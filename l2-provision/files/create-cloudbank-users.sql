-- CloudBank Demo - application user creation
-- Run as ADMIN. Existing users are left unchanged so this script can be rerun.

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET SERVEROUTPUT ON

DECLARE
  PROCEDURE create_user_if_missing(p_username VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE 'CREATE USER ' || p_username || ' IDENTIFIED BY Welcome_123#';
    DBMS_OUTPUT.PUT_LINE(p_username || ' created.');
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE = -1920 THEN
        DBMS_OUTPUT.PUT_LINE(p_username || ' already exists; skipping.');
      ELSE
        RAISE;
      END IF;
  END;
BEGIN
  create_user_if_missing('brokerhub');
  create_user_if_missing('orchestratorhub');
  create_user_if_missing('brokermex');
  create_user_if_missing('orchestratormex');
  create_user_if_missing('bankchicago');
  create_user_if_missing('bankmex');
  create_user_if_missing('banklondon');
  create_user_if_missing('banktokyo');
END;
/

GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_CONNECT_ROLE
TO brokerhub, brokermex;

GRANT CONNECT, RESOURCE, SAGA_ADM_ROLE, SAGA_PARTICIPANT_ROLE
TO orchestratorhub, orchestratormex,
bankchicago, bankmex, banklondon, banktokyo;

ALTER USER brokerhub QUOTA 500M ON DATA;
ALTER USER brokermex QUOTA 500M ON DATA;
ALTER USER orchestratorhub QUOTA 500M ON DATA;
ALTER USER orchestratormex QUOTA 500M ON DATA;
ALTER USER bankchicago QUOTA 500M ON DATA;
ALTER USER bankmex QUOTA 500M ON DATA;
ALTER USER banklondon QUOTA 500M ON DATA;
ALTER USER banktokyo QUOTA 500M ON DATA;

SELECT username
FROM dba_users
WHERE username IN (
  'BROKERHUB', 'ORCHESTRATORHUB', 'BROKERMEX', 'ORCHESTRATORMEX',
  'BANKCHICAGO', 'BANKMEX', 'BANKLONDON', 'BANKTOKYO'
)
ORDER BY username;

PROMPT SUCCESS: All eight CloudBank database users are configured and ready.

EXIT

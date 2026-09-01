/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.bankb.util;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ConnectionPools is a class which helps configure data source using the application.properties file.
 */
public class ConnectionPools {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPools.class);
    private static final Properties properties = PropertiesHelper.loadProperties();
    public static final String BANKB  = "bankb";

    private static PoolDataSource configureDataSource() {
        final var OSAGA = "osaga.";
        final var ADBS_TYPE = "adbs";
        final var LOCALDB_TYPE = "localdb";
        String applicationType = ConnectionPools.properties.getProperty(OSAGA+BANKB+".conn.type");
        final String url;
        if (applicationType.equalsIgnoreCase(ADBS_TYPE)) {

            String tnsAdmin = System.getenv("TNS_ADMIN");
            if (tnsAdmin == null) {
                tnsAdmin = ConnectionPools.properties.getProperty(OSAGA+BANKB + ".tns.admin");
            }
            if (tnsAdmin != null && !tnsAdmin.isBlank()) {
                System.setProperty("oracle.net.tns_admin", tnsAdmin);
            }
            String tnsAlias = System.getenv("TNS_ALIAS");
            if (tnsAlias == null) {
                tnsAlias = ConnectionPools.properties.getProperty(OSAGA+BANKB + ".tns.alias");
            }

            url = "jdbc:oracle:thin:@" + tnsAlias;

        } else if (applicationType.equalsIgnoreCase(LOCALDB_TYPE)) {
            url = "jdbc:oracle:thin:@//" + ConnectionPools.properties.getProperty(OSAGA + BANKB + ".host")+":"+ConnectionPools.properties.getProperty(OSAGA + BANKB + ".port")+"/"+ConnectionPools.properties.getProperty(OSAGA + BANKB + ".serviceName");
        } else{
            logger.error("Unable to add connection for {} pool as OSAGA_BANKB_CONN_TYPE ENV VAR NOT PRESENT", BANKB);
            return null;
        }

        String maxpool = ConnectionPools.properties.getProperty(OSAGA + BANKB + ".maxpool");
        String initialPool = ConnectionPools.properties.getProperty(OSAGA + BANKB + ".initialPoolSize");

        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();

        try {
            pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            pds.setInitialPoolSize(Integer.parseInt(maxpool));
            pds.setMaxPoolSize(Integer.parseInt(maxpool));
            pds.setMinPoolSize(Integer.parseInt(initialPool));
            pds.setInitialPoolSize(Integer.parseInt(initialPool));
            pds.setURL(url);
            pds.setConnectionPoolName(BANKB);
            pds.setUser(ConnectionPools.properties.getProperty(OSAGA + BANKB + ".username"));
            pds.setPassword(ConnectionPools.properties.getProperty(OSAGA + BANKB + ".password"));
        } catch (SQLException e) {
            logger.error("Unable to add connection for {} pool for app type {}", BANKB, applicationType);
            pds = null;
        }

        return pds;
    }

    private enum Accounts {
        INSTANCE();

        private final PoolDataSource ds;

        Accounts() {
            this.ds = configureDataSource();
        }

        public Connection getConnection() throws SQLException {
            return this.ds.getConnection();
        }
    }

    public static Connection getAccountsConnection() throws SQLException {
        return Accounts.INSTANCE.getConnection();
    }

    public static Properties getProperties() {
        return ConnectionPools.properties;
    }

}

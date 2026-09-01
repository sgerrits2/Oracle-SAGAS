/**
 * Copyright (c) 2025 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.banka.util;

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

    public static final String BANKA = "banka";

    private static PoolDataSource configureDataSource() {
        final var OSAGA = "osaga.";
        final var ADBS_TYPE = "adbs";
        final var LOCALDB_TYPE = "localdb";
        String applicationType = ConnectionPools.properties.getProperty(OSAGA+BANKA+".conn.type");
        final String url;
        if (applicationType.equalsIgnoreCase(ADBS_TYPE)) {

            String tnsAdmin = System.getenv("TNS_ADMIN");
            if (tnsAdmin == null) {
                tnsAdmin = ConnectionPools.properties.getProperty(OSAGA+BANKA + ".tns.admin");
            }
            if (tnsAdmin != null && !tnsAdmin.isBlank()) {
                System.setProperty("oracle.net.tns_admin", tnsAdmin);
            }
            String tnsAlias = System.getenv("TNS_ALIAS");
            if (tnsAlias == null) {
                tnsAlias = ConnectionPools.properties.getProperty(OSAGA+BANKA + ".tns.alias");
            }

            url = "jdbc:oracle:thin:@" + tnsAlias;

        } else if (applicationType.equalsIgnoreCase(LOCALDB_TYPE)) {
            url = "jdbc:oracle:thin:@//" + ConnectionPools.properties.getProperty(OSAGA + BANKA + ".host")+":"+ConnectionPools.properties.getProperty(OSAGA + BANKA + ".port")+"/"+ConnectionPools.properties.getProperty(OSAGA + BANKA + ".serviceName");
        } else{
            logger.error("Unable to add connection for {} pool as OSAGA_BANKA_CONN_TYPE ENV VAR NOT PRESENT", BANKA);
            return null;
        }

        String maxpool = ConnectionPools.properties.getProperty(OSAGA + BANKA + ".maxpool");
        String initialPool = ConnectionPools.properties.getProperty(OSAGA + BANKA + ".initialPoolSize");

        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();

        try {
            pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            pds.setInitialPoolSize(Integer.parseInt(maxpool));
            pds.setMaxPoolSize(Integer.parseInt(maxpool));
            pds.setMinPoolSize(Integer.parseInt(initialPool));
            pds.setInitialPoolSize(Integer.parseInt(initialPool));
            pds.setURL(url);
            pds.setConnectionPoolName(BANKA);
            pds.setUser(ConnectionPools.properties.getProperty(OSAGA + BANKA + ".username"));
            pds.setPassword(ConnectionPools.properties.getProperty(OSAGA + BANKA + ".password"));
        } catch (SQLException e) {
            logger.error("Unable to add connection for {} pool for app type {}", BANKA, applicationType);
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

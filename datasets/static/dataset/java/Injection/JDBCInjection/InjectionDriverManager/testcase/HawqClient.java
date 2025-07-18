<filename>anger-plugin/admin-plugin/src/main/java/org/apache/hawq/ranger/service/HawqClient.java<fim_prefix>

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hawq.ranger.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hawq.ranger.model.HawqProtocols;
import org.apache.ranger.plugin.client.BaseClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.*;

import org.apache.ranger.audit.utils.InMemoryJAASConfiguration;


public class HawqClient extends BaseClient {

    private static final Log LOG = LogFactory.getLog(HawqClient.class);

    public static final String CONNECTION_SUCCESSFUL_MESSAGE = "ConnectionTest Successful";
    public static final String CONNECTION_FAILURE_MESSAGE = "Unable to retrieve any databases using given parameters.";

    public static final String DATABASE_LIST_QUERY = "SELECT datname from pg_database WHERE " +
            "datname NOT IN ('template0', 'template1', 'hcatalog') and datname LIKE ?";
    public static final String TABLESPACE_LIST_QUERY = "SELECT spcname from pg_tablespace WHERE spcname LIKE ?";
    public static final String PROTOCOL_LIST_QUERY = "SELECT ptcname from pg_catalog.pg_extprotocol WHERE ptcname LIKE ?";
    public static final String SCHEMA_LIST_QUERY = "SELECT schema_name from information_schema.schemata WHERE " +
            "schema_name NOT IN ('pg_catalog', 'information_schema', 'hawq_toolkit', 'pg_bitmapindex', 'pg_aoseg') AND schema_name NOT LIKE 'pg_toast%' AND schema_name LIKE ?";
    public static final String LANGUAGE_LIST_QUERY = "SELECT lanname from pg_language WHERE lanname LIKE ?";
    public static final String SEQUENCE_LIST_QUERY = "SELECT schemaname, relname from pg_statio_all_sequences WHERE relname LIKE ?";
    public static final String FUNCTION_LIST_QUERY = "SELECT n.nspname, p.proname FROM pg_catalog.pg_proc p " +
            "LEFT JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname NOT IN " +
            "('pg_catalog', 'information_schema', 'hawq_toolkit', 'pg_bitmapindex', 'pg_aoseg') AND n.nspname NOT LIKE 'pg_toast%' AND p.proname LIKE ?";
    public static final String TABLE_LIST_QUERY = "SELECT c.relname, n.nspname FROM pg_catalog.pg_class c " +
            "LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE c.relkind IN ('r','','v') AND n.nspname NOT IN " +
            "('pg_catalog', 'information_schema', 'hawq_toolkit', 'pg_bitmapindex', 'pg_aoseg') AND n.nspname NOT LIKE 'pg_toast%' AND c.relname LIKE ?";

    public static final String DATNAME = "datname";
    public static final String SPCNAME = "spcname";
    public static final String PTCNAME = "ptcname";
    public static final String SCHEMA_NAME = "schema_name";
    public static final String LANGUAGE_NAME = "lanname";
    public static final String RELNAME = "relname";
    public static final String SCHEMANAME = "schemaname";
    public static final String PRONAME = "proname";
    public static final String NSPNAME = "nspname";
    public static final String WILDCARD = "*";
    public static final String KERBEROS = "kerberos";
    public static final String AUTHENTICATION = "authentication";

    public static final List<String> INTERNAL_PROTOCOLS = HawqProtocols.getAllProtocols();
    private static final String DEFAULT_DATABASE = "postgres";
    private static final String DEFAULT_DATABASE_TEMPLATE = "DBTOBEREPLACEDINJDBCURL";
    private static final String JDBC_DRIVER_CLASS = "org.postgresql.Driver";
    private static final String JAAS_APPLICATION_NAME = "pgjdbc";

    // we need to load class for the Postgres Driver directly to allow it to register with DriverManager
    // since DriverManager's classloader will not be able to find it by itself due to plugin's special classloaders
    static {
        try {
            Class.forName(JDBC_DRIVER_CLASS);
        } catch (Throwable e) {
            LOG.error("<== HawqClient.initializer : Unable to load JDBC driver " + JDBC_DRIVER_CLASS + " : " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }

    public HawqClient(String serviceName, Map<String, String> connectionProperties) throws Exception {
        super(serviceName, connectionProperties);
        this.connectionProperties = connectionProperties;
    }

    /**
     * clone a new Properties for debug logging:
     *  1. remove all password fields for preventing plain password leak in log
     *  2. add _password_length fields for debug
     *
     * @param connectionProperties
     * @return a new cloned Map for debug logging
     */
    private Map<String, String> removePassword(Map<String, String> connectionProperties) {
        Map<String, String> new_property = new HashMap<String, String>(connectionProperties);

        String pass_fields[] = {"password", "password_jdbc"};
        for (int i = 0; i < pass_fields.length; i++) {
            String field = pass_fields[i];
            if (new_property.containsKey(field)) {
                String password = new_property.get(field);
                new_property.remove(field);
                new_property.put("_"+field+"_length", Integer.toString(password.length()));
            }
        }

        return new_property;
    }

    private Connection getConnection(Map<String, String> connectionProperties) throws SQLException {
        return getConnection(connectionProperties, null);
    }

    private Connection getConnection(Map<String, String> connectionProperties, String database) throws SQLException {

        String db = database != null ? database : DEFAULT_DATABASE;
        Properties props = new Properties();

        if (LOG.isDebugEnabled()) {
            Map<String, String> debugProperties = removePassword(connectionProperties);
            LOG.debug("<== HawqClient.checkConnection configuration" + debugProperties );
        }

        if (connectionProperties.containsKey(AUTHENTICATION) && connectionProperties.get(AUTHENTICATION).equals(KERBEROS)) {

            Properties props_jaas = new Properties();
            props_jaas.put("xasecure.audit.jaas."+ JAAS_APPLICATION_NAME +".loginModuleName", "com.sun.security.auth.module.Krb5LoginModule");
            props_jaas.put("xasecure.audit.jaas."+ JAAS_APPLICATION_NAME +".loginModuleControlFlag", "required");

            try {
                InMemoryJAASConfiguration.init(props_jaas);
            } catch (Exception e) {
                LOG.error("InMemoryJAASConfiguration failed: " + e.getMessage());
                e.printStackTrace();
            }

            //kerberos mode
            props.setProperty("kerberosServerName", connectionProperties.get("principal"));
            props.setProperty("jaasApplicationName", JAAS_APPLICATION_NAME);

        }

        String password = connectionProperties.get("password");
        if (connectionProperties.containsKey("password_jdbc"))
            password = connectionProperties.get("password_jdbc");

            <fim_suffix>

    }


    /**
     * Uses the connectionProperties and attempts to connect to Hawq.
     * Returns a message depending on success or failure.
     *
     * @param connectionProperties Map which contains hostname, port, username, and password
     * @return Map which contains connectivityStatus, message, description, objectId, and fieldName
     */

    public HashMap<String, Object> checkConnection(Map<String, String> connectionProperties) throws Exception {

        boolean isConnected = false;
        HashMap<String, Object> result = new HashMap<>();

        String description = CONNECTION_FAILURE_MESSAGE;

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== HawqClient.checkConnection Starting connection to hawq");
        }

        Connection conn = null;
        try {
            conn = getConnection(connectionProperties);
            if (conn.getCatalog() != null) {
                isConnected = true;
                description = CONNECTION_SUCCESSFUL_MESSAGE;
            }
        } catch (SQLException e) {
            LOG.error("<== HawqClient.checkConnection Error: Failed to connect" + e);
            description = e.getMessage();
        } finally {
            closeConnection(conn);
        }

        String message = isConnected ? CONNECTION_SUCCESSFUL_MESSAGE : CONNECTION_FAILURE_MESSAGE;
        generateResponseDataMap(isConnected, message, description, null, null, result);

        return result;
    }

    public List<String> getDatabaseList(String userInput) throws SQLException {
        return queryHawq(userInput, DATNAME, DATABASE_LIST_QUERY, null);
    }

    public List<String> getTablespaceList(String userInput) throws SQLException {
        return queryHawq(userInput, SPCNAME, TABLESPACE_LIST_QUERY, null);
    }

    public List<String> getProtocolList(String userInput) throws SQLException {
        Set<String> allProtocols = new HashSet<>();
        for (String protocol : INTERNAL_PROTOCOLS) {
            if (protocol.startsWith(userInput) || userInput.equals(WILDCARD)) {
                allProtocols.add(protocol);
            }
        }
        allProtocols.addAll(queryHawq(userInput, PTCNAME, PROTOCOL_LIST_QUERY, null));
        return new ArrayList<>(allProtocols);
    }

    public List<String> getSchemaList(String userInput, Map<String, List<String>> resources) throws SQLException {
        return queryHawqPerDb(userInput, resources.get("database"), SCHEMA_NAME, SCHEMA_LIST_QUERY);
    }

    public List<String> getLanguageList(String userInput, Map<String, List<String>> resources) throws SQLException {
        return queryHawqPerDb(userInput, resources.get("database"), LANGUAGE_NAME, LANGUAGE_LIST_QUERY);
    }

    public List<String> getTableList(String userInput, Map<String, List<String>> resources) throws SQLException {
        return queryHawqPerDbAndSchema(userInput, resources, NSPNAME, RELNAME, TABLE_LIST_QUERY);
    }

    public List<String> getSequenceList(String userInput, Map<String, List<String>> resources) throws SQLException {
        return queryHawqPerDbAndSchema(userInput, resources, SCHEMANAME, RELNAME, SEQUENCE_LIST_QUERY);
    }

    public List<String> getFunctionList(String userInput, Map<String, List<String>> resources) throws SQLException {
        return queryHawqPerDbAndSchema(userInput, resources, NSPNAME, PRONAME, FUNCTION_LIST_QUERY);
    }

    private List<String> queryHawqPerDb(String userInput, List<String> databases, String columnName, String query) throws SQLException {
        Set<String> uniqueResults = new HashSet<>();

        //do for all databases
        if (databases.contains(WILDCARD)) {
            databases = getDatabaseList(WILDCARD);
        }

        for (String db : databases) {
            uniqueResults.addAll(queryHawq(userInput, columnName, query, db));
        }
        return new ArrayList<>(uniqueResults);
    }

    private List<String> queryHawqPerDbAndSchema(String userInput, Map<String, List<String>> resources, String schemaColumnName, String resultColumnName, String query) throws SQLException {
        Set<String> uniqueResults = new HashSet<>();
        List<String> databases = resources.get("database");
        List<String> schemas = resources.get("schema");

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        Connection conn = null;

        if (databases.contains(WILDCARD)) {
            databases = getDatabaseList(WILDCARD);
        }

        for (String db : databases) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("<== HawqClient.queryHawqPerDbAndSchema: Connecting to db: " + db);
            }

            try {
                conn = getConnection(connectionProperties, db);
                preparedStatement = handleWildcardPreparedStatement(userInput, query, conn);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("<== HawqClient.queryHawqPerDbAndSchema Starting query: " + preparedStatement.toString());
                }

                resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    if (schemas.contains(resultSet.getString(schemaColumnName)) || schemas.contains(WILDCARD)) {
                        uniqueResults.add(resultSet.getString(resultColumnName));
                    }
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("<== HawqClient.queryHawqPerDbAndSchema Query result: " + uniqueResults.toString());
                }

            } catch (SQLException e) {
                LOG.error("<== HawqClient.queryHawqPerDbAndSchema Error: Failed to get results for query: " + query + ", Error: " + e);
            } finally {
                closeResultSet(resultSet);
                closeStatement(preparedStatement);
                closeConnection(conn);
            }

        }
        return new ArrayList<>(uniqueResults);
    }

    private List<String> queryHawq(String userInput, String columnName, String query, String database) throws SQLException {
        List<String> result = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        Connection conn = null;

        try {
            conn = getConnection(connectionProperties, database);
            preparedStatement = handleWildcardPreparedStatement(userInput, query, conn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("<== HawqClient.queryHawq Starting query: " + preparedStatement.toString());
            }

            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                result.add(resultSet.getString(columnName));
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("<== HawqClient.queryHawq Query result: " + result.toString());
            }

        } catch (SQLException e) {
            LOG.error("<== HawqClient.queryHawq Error: Failed to get result from query: " + query + ", Error: " + e);
            throw e;
        } finally {
            closeResultSet(resultSet);
            closeStatement(preparedStatement);
            closeConnection(conn);
        }

        return result;
    }

    private PreparedStatement handleWildcardPreparedStatement(String userInput, String query, Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(query);
        preparedStatement.setString(1, userInput.equals(WILDCARD) ? "%" : (userInput + "%"));
        return preparedStatement;
    }

    private void closeResultSet(ResultSet rs) {
        try {
            if (rs != null) rs.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private void closeStatement(PreparedStatement st) {
        try {
            if (st != null) st.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private void closeConnection(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }

}
<fim_middle>
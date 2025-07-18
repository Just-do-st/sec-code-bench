<filename>flink-connector-jdbc-core/src/main/java/org/apache/flink/connector/jdbc/core/database/catalog/AbstractJdbcCatalog.java<fim_prefix>

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.connector.jdbc.core.database.catalog;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.connector.jdbc.core.table.JdbcDynamicTableFactory;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.catalog.AbstractCatalog;
import org.apache.flink.table.catalog.CatalogBaseTable;
import org.apache.flink.table.catalog.CatalogDatabase;
import org.apache.flink.table.catalog.CatalogDatabaseImpl;
import org.apache.flink.table.catalog.CatalogFunction;
import org.apache.flink.table.catalog.CatalogPartition;
import org.apache.flink.table.catalog.CatalogPartitionSpec;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.catalog.UniqueConstraint;
import org.apache.flink.table.catalog.exceptions.CatalogException;
import org.apache.flink.table.catalog.exceptions.DatabaseAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotEmptyException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotExistException;
import org.apache.flink.table.catalog.exceptions.FunctionAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.FunctionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionAlreadyExistsException;
import org.apache.flink.table.catalog.exceptions.PartitionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionSpecInvalidException;
import org.apache.flink.table.catalog.exceptions.TableAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.TableNotExistException;
import org.apache.flink.table.catalog.exceptions.TableNotPartitionedException;
import org.apache.flink.table.catalog.exceptions.TablePartitionedException;
import org.apache.flink.table.catalog.stats.CatalogColumnStatistics;
import org.apache.flink.table.catalog.stats.CatalogTableStatistics;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.factories.Factory;
import org.apache.flink.table.types.DataType;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.StringUtils;
import org.apache.flink.util.TemporaryClassLoaderContext;

import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;

import static org.apache.flink.connector.jdbc.JdbcConnectionOptions.PASSWORD_KEY;
import static org.apache.flink.connector.jdbc.JdbcConnectionOptions.USER_KEY;
import static org.apache.flink.connector.jdbc.JdbcConnectionOptions.getBriefAuthProperties;
import static org.apache.flink.connector.jdbc.core.table.JdbcConnectorOptions.PASSWORD;
import static org.apache.flink.connector.jdbc.core.table.JdbcConnectorOptions.TABLE_NAME;
import static org.apache.flink.connector.jdbc.core.table.JdbcConnectorOptions.URL;
import static org.apache.flink.connector.jdbc.core.table.JdbcConnectorOptions.USERNAME;
import static org.apache.flink.connector.jdbc.core.table.JdbcDynamicTableFactory.IDENTIFIER;
import static org.apache.flink.table.factories.FactoryUtil.CONNECTOR;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Abstract catalog for any JDBC catalogs. */
@PublicEvolving
public abstract class AbstractJdbcCatalog extends AbstractCatalog implements JdbcCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJdbcCatalog.class);

    protected final ClassLoader userClassLoader;
    protected final String baseUrl;
    protected final String defaultUrl;
    protected final Properties connectionProperties;

    @Deprecated
    public AbstractJdbcCatalog(
            ClassLoader userClassLoader,
            String catalogName,
            String defaultDatabase,
            String username,
            String pwd,
            String baseUrl) {
        this(
                userClassLoader,
                catalogName,
                defaultDatabase,
                baseUrl,
                getBriefAuthProperties(username, pwd));
    }

    public AbstractJdbcCatalog(
            ClassLoader userClassLoader,
            String catalogName,
            String defaultDatabase,
            String baseUrl,
            Properties connectionProperties) {
        super(catalogName, defaultDatabase);

        checkNotNull(userClassLoader);
        checkArgument(!StringUtils.isNullOrWhitespaceOnly(baseUrl));

        validateJdbcUrl(baseUrl);

        this.userClassLoader = userClassLoader;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.defaultUrl = getDatabaseUrl(defaultDatabase);
        this.connectionProperties = Preconditions.checkNotNull(connectionProperties);
        checkArgument(
                !StringUtils.isNullOrWhitespaceOnly(connectionProperties.getProperty(USER_KEY)));
        checkArgument(
                !StringUtils.isNullOrWhitespaceOnly(
                        connectionProperties.getProperty(PASSWORD_KEY)));
    }

    protected String getDatabaseUrl(String databaseName) {
        return baseUrl + databaseName;
    }

    @Override
    public void open() throws CatalogException {
        // load the Driver use userClassLoader explicitly, see FLINK-15635 for more detail
        try (TemporaryClassLoaderContext ignored =
                TemporaryClassLoaderContext.of(userClassLoader)) {
            // test connection, fail early if we cannot connect to database
            <fim_suffix>
            LOG.info("Catalog {} established connection to {}", getName(), defaultUrl);
        }
    }

    @Override
    public void close() throws CatalogException {
        LOG.info("Catalog {} closing", getName());
    }

    // ----- getters ------

    public String getUsername() {
        return connectionProperties.getProperty(USER_KEY);
    }

    public String getPassword() {
        return connectionProperties.getProperty(PASSWORD_KEY);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ------ retrieve PK constraint ------

    protected Optional<UniqueConstraint> getPrimaryKey(
            DatabaseMetaData metaData, String database, String schema, String table)
            throws SQLException {

        // According to the Javadoc of java.sql.DatabaseMetaData#getPrimaryKeys,
        // the returned primary key columns are ordered by COLUMN_NAME, not by KEY_SEQ.
        // We need to sort them based on the KEY_SEQ value.
        // In the currently supported database dialects MySQL and Postgres,
        // the database term is equivalent to catalog term.
        // We need to pass the database name as catalog parameter for retrieving primary keys by
        // full table identifier.
        ResultSet rs = metaData.getPrimaryKeys(database, schema, table);

        Map<Integer, String> keySeqColumnName = new HashMap<>();
        String pkName = null;
        while (rs.next()) {
            String columnName = rs.getString("COLUMN_NAME");
            pkName = rs.getString("PK_NAME"); // all the PK_NAME should be the same
            int keySeq = rs.getInt("KEY_SEQ");
            Preconditions.checkState(
                    !keySeqColumnName.containsKey(keySeq - 1),
                    "The field(s) of primary key must be from the same table.");
            keySeqColumnName.put(keySeq - 1, columnName); // KEY_SEQ is 1-based index
        }
        List<String> pkFields =
                Arrays.asList(new String[keySeqColumnName.size()]); // initialize size
        keySeqColumnName.forEach(pkFields::set);
        if (!pkFields.isEmpty()) {
            // PK_NAME maybe null according to the javadoc, generate an unique name in that case
            pkName = pkName == null ? "pk_" + String.join("_", pkFields) : pkName;
            return Optional.of(UniqueConstraint.primaryKey(pkName, pkFields));
        }
        return Optional.empty();
    }

    // ------ table factory ------

    @Override
    public Optional<Factory> getFactory() {
        return Optional.of(new JdbcDynamicTableFactory());
    }

    // ------ databases ------

    @Override
    public boolean databaseExists(String databaseName) throws CatalogException {
        checkArgument(!StringUtils.isNullOrWhitespaceOnly(databaseName));

        return listDatabases().contains(databaseName);
    }

    @Override
    public void createDatabase(String name, CatalogDatabase database, boolean ignoreIfExists)
            throws DatabaseAlreadyExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dropDatabase(String name, boolean ignoreIfNotExists, boolean cascade)
            throws DatabaseNotExistException, DatabaseNotEmptyException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterDatabase(String name, CatalogDatabase newDatabase, boolean ignoreIfNotExists)
            throws DatabaseNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CatalogDatabase getDatabase(String databaseName)
            throws DatabaseNotExistException, CatalogException {

        Preconditions.checkState(
                !StringUtils.isNullOrWhitespaceOnly(databaseName),
                "Database name must not be blank.");
        if (listDatabases().contains(databaseName)) {
            return new CatalogDatabaseImpl(Collections.emptyMap(), null);
        } else {
            throw new DatabaseNotExistException(getName(), databaseName);
        }
    }

    // ------ tables and views ------

    @Override
    public CatalogBaseTable getTable(ObjectPath tablePath)
            throws TableNotExistException, CatalogException {

        if (!tableExists(tablePath)) {
            throw new TableNotExistException(getName(), tablePath);
        }

        String databaseName = tablePath.getDatabaseName();

        try (Connection conn =
                DriverManager.getConnection(getDatabaseUrl(databaseName), connectionProperties)) {
            DatabaseMetaData metaData = conn.getMetaData();
            Optional<UniqueConstraint> primaryKey =
                    getPrimaryKey(
                            metaData,
                            databaseName,
                            getSchemaName(tablePath),
                            getTableName(tablePath));

            PreparedStatement ps =
                    conn.prepareStatement(
                            String.format("SELECT * FROM %s;", getSchemaTableName(tablePath)));

            ResultSetMetaData resultSetMetaData = ps.getMetaData();

            String[] columnNames = new String[resultSetMetaData.getColumnCount()];
            DataType[] types = new DataType[resultSetMetaData.getColumnCount()];

            for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                columnNames[i - 1] = resultSetMetaData.getColumnName(i);
                types[i - 1] = fromJDBCType(tablePath, resultSetMetaData, i);
                if (resultSetMetaData.isNullable(i) == ResultSetMetaData.columnNoNulls) {
                    types[i - 1] = types[i - 1].notNull();
                }
            }

            Schema.Builder schemaBuilder = Schema.newBuilder().fromFields(columnNames, types);
            primaryKey.ifPresent(
                    pk -> schemaBuilder.primaryKeyNamed(pk.getName(), pk.getColumns()));
            Schema tableSchema = schemaBuilder.build();

            return CatalogTable.newBuilder()
                    .schema(tableSchema)
                    .options(getOptions(tablePath))
                    .build();
        } catch (Exception e) {
            throw new CatalogException(
                    String.format("Failed getting table %s", tablePath.getFullName()), e);
        }
    }

    protected Map<String, String> getOptions(ObjectPath tablePath) {
        Map<String, String> props = new HashMap<>();
        props.put(CONNECTOR.key(), IDENTIFIER);
        props.put(URL.key(), getDatabaseUrl(tablePath.getDatabaseName()));
        props.put(USERNAME.key(), connectionProperties.getProperty(USER_KEY));
        props.put(PASSWORD.key(), connectionProperties.getProperty(PASSWORD_KEY));
        props.put(TABLE_NAME.key(), getSchemaTableName(tablePath));
        return props;
    }

    @Override
    public void dropTable(ObjectPath tablePath, boolean ignoreIfNotExists)
            throws TableNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void renameTable(ObjectPath tablePath, String newTableName, boolean ignoreIfNotExists)
            throws TableNotExistException, TableAlreadyExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createTable(ObjectPath tablePath, CatalogBaseTable table, boolean ignoreIfExists)
            throws TableAlreadyExistException, DatabaseNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterTable(
            ObjectPath tablePath, CatalogBaseTable newTable, boolean ignoreIfNotExists)
            throws TableNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> listViews(String databaseName)
            throws DatabaseNotExistException, CatalogException {
        return Collections.emptyList();
    }

    // ------ partitions ------

    @Override
    public List<CatalogPartitionSpec> listPartitions(ObjectPath tablePath)
            throws TableNotExistException, TableNotPartitionedException, CatalogException {
        return Collections.emptyList();
    }

    @Override
    public List<CatalogPartitionSpec> listPartitions(
            ObjectPath tablePath, CatalogPartitionSpec partitionSpec)
            throws TableNotExistException, TableNotPartitionedException,
                    PartitionSpecInvalidException, CatalogException {
        return Collections.emptyList();
    }

    @Override
    public List<CatalogPartitionSpec> listPartitionsByFilter(
            ObjectPath tablePath, List<Expression> filters)
            throws TableNotExistException, TableNotPartitionedException, CatalogException {
        return Collections.emptyList();
    }

    @Override
    public CatalogPartition getPartition(ObjectPath tablePath, CatalogPartitionSpec partitionSpec)
            throws PartitionNotExistException, CatalogException {
        throw new PartitionNotExistException(getName(), tablePath, partitionSpec);
    }

    @Override
    public boolean partitionExists(ObjectPath tablePath, CatalogPartitionSpec partitionSpec)
            throws CatalogException {
        return false;
    }

    @Override
    public void createPartition(
            ObjectPath tablePath,
            CatalogPartitionSpec partitionSpec,
            CatalogPartition partition,
            boolean ignoreIfExists)
            throws TableNotExistException, TableNotPartitionedException,
                    PartitionSpecInvalidException, PartitionAlreadyExistsException,
                    CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dropPartition(
            ObjectPath tablePath, CatalogPartitionSpec partitionSpec, boolean ignoreIfNotExists)
            throws PartitionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterPartition(
            ObjectPath tablePath,
            CatalogPartitionSpec partitionSpec,
            CatalogPartition newPartition,
            boolean ignoreIfNotExists)
            throws PartitionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    // ------ functions ------

    @Override
    public List<String> listFunctions(String dbName)
            throws DatabaseNotExistException, CatalogException {
        return Collections.emptyList();
    }

    @Override
    public CatalogFunction getFunction(ObjectPath functionPath)
            throws FunctionNotExistException, CatalogException {
        throw new FunctionNotExistException(getName(), functionPath);
    }

    @Override
    public boolean functionExists(ObjectPath functionPath) throws CatalogException {
        return false;
    }

    @Override
    public void createFunction(
            ObjectPath functionPath, CatalogFunction function, boolean ignoreIfExists)
            throws FunctionAlreadyExistException, DatabaseNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterFunction(
            ObjectPath functionPath, CatalogFunction newFunction, boolean ignoreIfNotExists)
            throws FunctionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dropFunction(ObjectPath functionPath, boolean ignoreIfNotExists)
            throws FunctionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    // ------ stats ------

    @Override
    public CatalogTableStatistics getTableStatistics(ObjectPath tablePath)
            throws TableNotExistException, CatalogException {
        return CatalogTableStatistics.UNKNOWN;
    }

    @Override
    public CatalogColumnStatistics getTableColumnStatistics(ObjectPath tablePath)
            throws TableNotExistException, CatalogException {
        return CatalogColumnStatistics.UNKNOWN;
    }

    @Override
    public CatalogTableStatistics getPartitionStatistics(
            ObjectPath tablePath, CatalogPartitionSpec partitionSpec)
            throws PartitionNotExistException, CatalogException {
        return CatalogTableStatistics.UNKNOWN;
    }

    @Override
    public CatalogColumnStatistics getPartitionColumnStatistics(
            ObjectPath tablePath, CatalogPartitionSpec partitionSpec)
            throws PartitionNotExistException, CatalogException {
        return CatalogColumnStatistics.UNKNOWN;
    }

    @Override
    public void alterTableStatistics(
            ObjectPath tablePath, CatalogTableStatistics tableStatistics, boolean ignoreIfNotExists)
            throws TableNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterTableColumnStatistics(
            ObjectPath tablePath,
            CatalogColumnStatistics columnStatistics,
            boolean ignoreIfNotExists)
            throws TableNotExistException, CatalogException, TablePartitionedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterPartitionStatistics(
            ObjectPath tablePath,
            CatalogPartitionSpec partitionSpec,
            CatalogTableStatistics partitionStatistics,
            boolean ignoreIfNotExists)
            throws PartitionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void alterPartitionColumnStatistics(
            ObjectPath tablePath,
            CatalogPartitionSpec partitionSpec,
            CatalogColumnStatistics columnStatistics,
            boolean ignoreIfNotExists)
            throws PartitionNotExistException, CatalogException {
        throw new UnsupportedOperationException();
    }

    protected List<String> extractColumnValuesBySQL(
            String connUrl,
            String sql,
            int columnIndex,
            Predicate<String> filterFunc,
            Object... params) {

        try (Connection conn = DriverManager.getConnection(connUrl, connectionProperties);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            return extractColumnValuesByStatement(ps, columnIndex, filterFunc, params);

        } catch (Exception e) {
            throw new CatalogException(
                    String.format(
                            "The following SQL query could not be executed (%s): %s", connUrl, sql),
                    e);
        }
    }

    protected static List<String> extractColumnValuesByStatement(
            PreparedStatement ps, int columnIndex, Predicate<String> filterFunc, Object... params)
            throws SQLException {
        List<String> columnValues = Lists.newArrayList();
        if (Objects.nonNull(params) && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String columnValue = rs.getString(columnIndex);
                if (Objects.isNull(filterFunc) || filterFunc.test(columnValue)) {
                    columnValues.add(columnValue);
                }
            }
        }
        return columnValues;
    }

    protected DataType fromJDBCType(ObjectPath tablePath, ResultSetMetaData metadata, int colIndex)
            throws SQLException {
        throw new UnsupportedOperationException();
    }

    protected String getTableName(ObjectPath tablePath) {
        throw new UnsupportedOperationException();
    }

    protected String getSchemaName(ObjectPath tablePath) {
        throw new UnsupportedOperationException();
    }

    protected String getSchemaTableName(ObjectPath tablePath) {
        throw new UnsupportedOperationException();
    }

    /**
     * URL has to be without database, like "jdbc:dialect://localhost:1234/" or
     * "jdbc:dialect://localhost:1234" rather than "jdbc:dialect://localhost:1234/db".
     */
    protected static void validateJdbcUrl(String url) {
        String[] parts = url.trim().split("\\/+");

        checkArgument(parts.length == 2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractJdbcCatalog)) {
            return false;
        }
        AbstractJdbcCatalog that = (AbstractJdbcCatalog) o;
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getDefaultDatabase(), that.getDefaultDatabase())
                && Objects.equals(connectionProperties, that.connectionProperties)
                && Objects.equals(baseUrl, that.baseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(), getDefaultDatabase(), userClassLoader, connectionProperties, baseUrl);
    }
}
<fim_middle>
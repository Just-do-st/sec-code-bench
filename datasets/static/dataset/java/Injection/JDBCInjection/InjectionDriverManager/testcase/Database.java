<filename>platforms/java/com/area9innovation/flow/Database.java<fim_prefix>

package com.area9innovation.flow;

import java.util.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class Database extends NativeHost {

    protected static Struct illegal = null;

    protected static Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    /*
      This two classes intended to store relations between
      connection and result set made with them.

      Also, for `lastInsertIdDb()` couple of things prepared:
      - LRU RS for storing RS
      - autoGeneratedRS for getting last insert id.

      Database exceptions stored at RSObject.err if possible or
      at DBObject.err if something wrong before RS available.
     */

    public static class DBObject {
        public Connection con = null;
        public Statement stmt = null;
        public String err = "";
        public RSObject lrurs = null;
        protected DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        protected HashSet<String> intOverflowFields = new HashSet<String>();
        public FlowRuntime.SingleExecutor queryExecutor = new FlowRuntime.SingleExecutor("db query");

        public DBObject() {
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        public void cleanRS() {
            if (lrurs != null) {
                lrurs.close();
                lrurs = null;
            }
        }

        public void setRS(RSObject rso) {
            if (lrurs != rso) {
                cleanRS();
                lrurs = rso;
            }
        }

        public void close() {
            cleanRS();
            if (con != null) {
                try {
                    if (stmt != null) {
                        stmt.close();
                    }
                    con.close();
                } catch (SQLException e) {
                    printException(e);
                }
            }
        }

        public void checkIntOverflow() {
            if (!intOverflowFields.isEmpty()) {
                // Hope it will never happen with id fields,
                // otherwise we should support long integer in the rule interpreter
                System.out.println("Warning! These fields " + intOverflowFields.toString() + " contains values that are out of 32-bit integers");
                intOverflowFields.clear();
            }
        }
    }

    public static class RSObject {
        public ResultSet rs = null;
        public DBObject dbObj = null;
        public ResultSet autoGeneratedRS = null;
        public String err = "";

        public void close() {
            try {
                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (autoGeneratedRS != null) {
                    autoGeneratedRS.close();
                    autoGeneratedRS = null;
                }
            } catch (SQLException e) {
                printException(e);
            }
        }

        public RSObject(DBObject dbo, String query) throws SQLException {
            err = "";
            dbObj = dbo;

            Statement stmt = dbo.con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            boolean resType = stmt.execute(query, Statement.RETURN_GENERATED_KEYS);
            if (resType) {
                rs = stmt.getResultSet();
            } else {
                autoGeneratedRS = stmt.getGeneratedKeys();
            }

            dbObj.setRS(this);
        }

        public RSObject(DBObject dbo, String query, Object[] queryParams) throws SQLException {
            err = "";
            dbObj = dbo;

            PreparedStatement stmt = dbo.con.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < queryParams.length; i++) {
                stmt.setString(i + 1, (String)queryParams[i]);
            }
            boolean resType = stmt.execute();
            if (resType) {
                rs = stmt.getResultSet();
            } else {
                autoGeneratedRS = stmt.getGeneratedKeys();
            }

            dbObj.setRS(this);
        }
    }

	public void initialize() {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver").getConstructor().newInstance();
		} catch (Exception e) {
			printException(e);
		}
		Integer strid = FlowRuntime.struct_ids.get("IllegalStruct"); // IllegalStruct() could be not used in the code
		illegal = FlowRuntime.struct_prototypes[strid];
	}

    public static final Object connectDb(String host, Integer port, String socket, String user, String password, String database) {
        DBObject db = new DBObject();
        try {
			if (socket.isEmpty()) {
				Properties props = new Properties();
				props.put("user", user);
				props.put("password", password);
                
                <fim_suffix>

				db.stmt = db.con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			} else {
				// This makes H2 work
				Class.forName("org.h2.Driver");

				// If the socket is given, we just send the socket directly as is
				db.con = DriverManager.getConnection(socket, user, password);
			}
            db.err = "";
        } catch (SQLException se) {
            db.err = getSqlErrorMessage(se);
        } catch (Exception e) {
            db.err = e.getMessage();
        }
        return db;
    }

    public static final String connectExceptionDb(Object database) {
        if (database == null) return "Empty database object";
        return ((DBObject) database).err;
    }

    public static final Object closeDb(Object database) {
        if (database != null) {
            ((DBObject) database).close();
        }
        return null;
    }

    public static final String escapeDb(Object database, String s) {
        return s.replace("\\", "\\\\")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\\x1A", "\\Z")
                .replace("\\x00", "\\0")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    public static final Object requestDb(Object database, String query) {
        Object[] params = {};
        return requestDbWithQueryParams(database, query, params);
    }

	public static final Object requestDbWithQueryParams(Object database, String query, Object[] queryParams) {
		if (database == null) return null;
		DBObject dbObj = (DBObject) database;
		try {
			RSObject rso = dbObj.queryExecutor.runAndWait(query + "\n;Thread: " + Native.getThreadId(), () -> {
				try {
					dbObj.err = "";
					if (queryParams.length == 0) {
						return new RSObject(dbObj, query);
					} else {
						return new RSObject(dbObj, query, queryParams);
					}
				} catch (SQLException se) {
					String err = getSqlErrorMessage(se);
					System.out.println("Error on request db: " + err);
					dbObj.err = err;
				} catch (Exception e) {
					dbObj.err = e.getMessage();
					printException(e);
				}
				return null;
			});
			return (Object)rso;
		} catch (Exception e) {
			dbObj.err = e.getMessage();
			return null;
		}
    }

    public static final String requestExceptionDb(Object database) {
        try {
            if (database != null && ((DBObject) database).err != null) {
                return ((DBObject) database).err;
            } else {
                return "";
            }
        } catch (Exception e) {
            printException(e);
            return "Unknown Exception";
        }
    }

    public static final Integer lastInsertIdDb(Object database) {
        DBObject db = (DBObject) database;
        if (db == null) return -1;
        if (db.lrurs == null) return -1;
        if (db.lrurs.autoGeneratedRS == null) return -1;
        try {
            if (db.lrurs.autoGeneratedRS.next()) {
                int r = db.lrurs.autoGeneratedRS.getInt(1);
                db.lrurs.autoGeneratedRS.close();
                db.lrurs.autoGeneratedRS = null;
                return r;
            } else {
                return -1;
            }
        } catch (Exception e) {
            printException(e);
            return -1;
        }
    }

    // We don't want to support it in java,
    // we prefer performance and more fast record sets (ResultSet.TYPE_FORWARD_ONLY)
    /*
    public final Integer resultLengthDb(Object result) {
        RSObject res = (RSObject) result;
        try {
            int curPosition = res.rs.getRow();
            if (res.rs.last()) {
                int length = res.rs.getRow();
                res.rs.absolute(curPosition);
                return length;
            } else {
                return 0;
            }
        } catch (Exception e) {
            printException(e);
            return 0;
        }
    }
    */

    public static final Boolean hasNextResultDb(Object result) {
        RSObject res = (RSObject) result;
        try {
            if (res == null || res.rs == null) return false;
			res.err = "";
            return notEmptyResultSet(res.rs);
        } catch (SQLException se) {
            res.err = getSqlErrorMessage(se);
            return false;
        } catch (Exception e) {
			res.err = e.getMessage();
            printException(e);
            return false;
        }
    }

    protected static Boolean notEmptyResultSet(ResultSet rs) throws SQLException {
        return (rs.isBeforeFirst() || rs.getRow() > 0) && !(rs.isLast() || rs.isAfterLast());
    }

    protected static String[] getFieldNames(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        String[] fieldNames = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            fieldNames[i] = rsmd.getColumnLabel(i + 1);
        }
        return fieldNames;
    }

    protected static int[] getFieldTypes(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        int[] fieldtypes = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            fieldtypes[i] = rsmd.getColumnType(i + 1);
        }
        return fieldtypes;
    }

    // Collect single row of nulls in order to preserve columns names
    // Can be used as a special case for empty tables
    private static Struct[] getNullRowValues(ResultSet rs, String[] fieldNames) throws SQLException {
        int columnCount = fieldNames.length;
        Struct[] values = new Struct[columnCount];
        for (int i = 0; i < columnCount; i++) {
            values[i] = FlowRuntime.makeStructValue("DbNullField", new Object[]{fieldNames[i]}, illegal);
        }
        return values;
    }

    private static Struct[] getRowValues(ResultSet rs, String[] fieldNames, int[] fieldtypes, Struct[] nulls, DBObject dbObj) throws SQLException {
        int columnCount = fieldNames.length;
        Struct[] values = new Struct[columnCount];
        for (int i = 0; i < columnCount; i++) {
            Struct value;
            int type = fieldtypes[i];
            String name = fieldNames[i];
            Struct anull = nulls[i];

            switch (type) {
                case (Types.NULL):
                    value = anull;
                    break;
                case (Types.INTEGER):
                case (Types.TINYINT):
                case (Types.SMALLINT):
                    Integer ivalue = rs.getInt(i + 1);
                    value = rs.wasNull() ? anull : FlowRuntime.makeStructValue("DbIntField", new Object[]{name, ivalue}, illegal);
                    break;
                case (Types.BIGINT):
                    long lvalue = rs.getLong(i + 1);
                    if (rs.wasNull()) {
                        value = anull;
                    } else {
                        ivalue = (int)lvalue;
                        if ((long)ivalue == lvalue) {
                            // use int type if the value fits in 32 bit integer
                            value = FlowRuntime.makeStructValue("DbIntField", new Object[]{name, ivalue}, illegal);
                        } else {
                            if ((lvalue & 0xFFFF000000000000L) == 0L) {
                                // if the value fits in double type
                                // in double 52 bits are used for the mantissa (15-16 decimal digits)
                                // We support 48 bit non-negative integers as double (14 decimal digits)
                                value = FlowRuntime.makeStructValue("DbDoubleField", new Object[]{name, (double)lvalue}, illegal);
                            } else {
                                // otherwise use string
                                value = FlowRuntime.makeStructValue("DbStringField", new Object[]{name, Long.toString(lvalue)}, illegal);
                            }
                            dbObj.intOverflowFields.add(name);
                        }
                    }
                    break;
                case (Types.DOUBLE):
                case (Types.DECIMAL):
                case (Types.REAL):
                case (Types.FLOAT):
                case (Types.NUMERIC):
                    Double dvalue = rs.getDouble(i + 1);
                    value = rs.wasNull() ? anull : FlowRuntime.makeStructValue("DbDoubleField", new Object[]{name, dvalue}, illegal);
                    break;
                case (Types.TIMESTAMP):
                    Timestamp t = rs.getTimestamp(i + 1, calendar);
                    if (t == null || rs.wasNull()) {
                        value = anull;
                    } else {
                        String svalue = dbObj.dateFormat.format(t);
                        value = FlowRuntime.makeStructValue("DbStringField", new Object[]{name, svalue}, illegal);
                    }
                    break;
                default:
                    String svalue = rs.getString(i + 1);
                    value = rs.wasNull() ? anull : FlowRuntime.makeStructValue("DbStringField", new Object[]{name, svalue}, illegal);
            }

            values[i] = value;
        }

        return values;
    }

    public static final Struct[][][] requestDbMulti(Object database, Object[] queries) {
        Struct[][][] empty = new Struct[0][][];
        if (database == null || queries.length == 0) {
			return empty;
		}

        // Do not allow empty queries at all
        for (Object query : queries) {
            if (query == "") return empty;
        }

        DBObject dbo = (DBObject) database;
        ArrayList<Struct[][]> res = new ArrayList<Struct[][]>();
        try {
            String[] q1 = new String[queries.length];

            for (int i = 0; i < queries.length; i++) {
                q1[i] = (String) (queries[i]);
            }
            String sql = String.join(";", q1);

			Pair<Boolean, Exception> pair = dbo.queryExecutor.runAndWait(sql + "\n;Thread: " + Native.getThreadId(), () -> {
				try {
					return new Pair<Boolean, Exception>(dbo.stmt.execute(sql), null);
				}
				catch (Exception e) {
					e.printStackTrace(System.out);
					return new Pair<Boolean, Exception>(null, e);
				}
			});
			if (pair.second != null) {
				throw pair.second;
			}
			Boolean isResultSet = pair.first;
            Integer updateCount = dbo.stmt.getUpdateCount();
            while (isResultSet || updateCount != -1) {
                if (isResultSet) {
                    ArrayList<Struct[]> table = new ArrayList<Struct[]>();
                    ResultSet rs = dbo.stmt.getResultSet();
                    String[] fieldNames = getFieldNames(rs);
                    int[] fieldTypes = getFieldTypes(rs);
                    Struct[] nulls = getNullRowValues(rs, fieldNames);
                    while (notEmptyResultSet(rs)) {
                        rs.next();
                        table.add(getRowValues(rs, fieldNames, fieldTypes, nulls, dbo));
                    }
                    rs.close();
                    if (table.isEmpty()) {
                        // The table is empty but we need to return columns names somehow
                        table.add(nulls);
                    }
                    res.add(table.toArray(new Struct[0][]));
                } else {
                    // We ignore updateCount here
                    res.add(new Struct[0][]);
                }
                isResultSet = dbo.stmt.getMoreResults(Statement.CLOSE_ALL_RESULTS);
                updateCount = dbo.stmt.getUpdateCount();
            }

            dbo.checkIntOverflow();
			dbo.err = "";

            return res.toArray(new Struct[res.size()][][]);
        } catch (SQLException e) {
            dbo.err = getSqlErrorMessage(e);
            return empty;
        } catch (Exception e) {
            printException(e);
            dbo.err = e.getMessage();
            return empty;
        }
    }

    public static final Struct[] nextResultDb(Object result) {
        RSObject res = (RSObject) result;

        if (res == null || res.rs == null) return new Struct[0];
        try {
            String[] fieldNames = getFieldNames(res.rs);
            int[] fieldTypes = getFieldTypes(res.rs);
            Struct[] nulls = getNullRowValues(res.rs, fieldNames);
            if (res.rs.next()) {
                res.dbObj.setRS(res);
                return getRowValues(res.rs, fieldNames, fieldTypes, nulls, res.dbObj);
            } else {
                res.dbObj.checkIntOverflow();
                return nulls;
            }
        } catch (Exception e) {
            printException(e);
            return new Struct[0];
        }
    }

    public static void printException(Exception e) {
        System.out.println("Exception: '" + e.toString() + "' at:");
        e.printStackTrace();
    }

    public static String getSqlErrorMessage(SQLException e) {
        String msg = e.getMessage();
        if (msg == null || msg == "") {
            msg = "Error state: " + e.getSQLState();
        }
        if (e.getCause() != null) {
            msg = msg + "\nCause: " + e.getCause().getMessage();
        }
        return msg;
    }
}
<fim_middle>
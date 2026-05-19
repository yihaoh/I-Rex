package edu.duke.cs.irex.sqlanalyzer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.SqlWriterConfig;
import org.apache.calcite.sql.dialect.PostgresqlSqlDialect;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.pretty.SqlPrettyWriter;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorImpl;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextMilestoneExecd;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextPageExecd;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DSelectContextCode;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DSetOpContextCode;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext.DContextCode;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextEvalExecd;

public class Analyzer {
    public final String url;
    public final String username;
    public final String password;

    public final SchemaPlus schema;
    public final RelDataTypeFactory sqlTypeFactory;
    public final CalciteCatalogReader calciteCatalogReader;
    public final SqlStdOperatorTable sqlStdOperatorTable;
    public final SqlWriterConfig sqlWriterConfig;
    public final SqlWriterConfig sqlWriterConfigCompact;

    public final Map<String, List<String>> mapTableToColumns;
    public final Map<String, List<Integer>> mapTableToPKColumnIndexes;
    public final Map<String, List<Integer>> mapTableToIdxColumnsIndexes;

    public static final Gson defaultGson = getDefaultGsonBuilder().create();
    public static final Gson prettyPrintGson = getDefaultGsonBuilder().setPrettyPrinting().create();

    public Analyzer(String host, String db, String username, String password) throws SQLException {
        this.url = String.format("jdbc:postgresql://%s/%s?caseSensitive=false", host, db);
        this.username = username;
        this.password = password;
        // Hook into Calcite the PostgresSQL database schema:
        CalciteConnection calciteConnection = DriverManager.getConnection("jdbc:calcite:")
                .unwrap(CalciteConnection.class);
        SchemaPlus rootSchema = calciteConnection.getRootSchema();
        rootSchema.add(db,
                JdbcSchema.create(rootSchema, db,
                        JdbcSchema.dataSource(url, "org.postgresql.Driver", username, password),
                        null, null));
        this.schema = rootSchema.getSubSchema(db);
        // Set up other useful Calcite objects:
        this.sqlTypeFactory = new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
        Properties properties = new Properties();
        properties.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
        this.calciteCatalogReader = new CalciteCatalogReader(
                CalciteSchema.from(this.schema),
                CalciteSchema.from(this.schema).path(null),
                this.sqlTypeFactory,
                new CalciteConnectionConfigImpl(properties));
        this.sqlStdOperatorTable = SqlStdOperatorTable.instance();
        // In the following, we'd rather not use withAlwaysUseParentheses(true) but without it
        // Calcite will produce incorrect SQL: NOT EXISTS SELECT...
        // For compact writer, it's okay because the string is for display only, and there would be
        // too many parentheses otherwise.
        this.sqlWriterConfig = SqlPrettyWriter.config().withDialect(PostgresqlSqlDialect.DEFAULT)
                .withQuoteAllIdentifiers(false).withAlwaysUseParentheses(true);
        this.sqlWriterConfigCompact = SqlPrettyWriter.config().withDialect(PostgresqlSqlDialect.DEFAULT)
                .withQuoteAllIdentifiers(false).withClauseStartsLine(false);
        // Grab some key constraints through JDBC --- too hard to get them from Calcite:
        this.mapTableToColumns = new HashMap<>();
        this.mapTableToPKColumnIndexes = new HashMap<>();
        this.mapTableToIdxColumnsIndexes = new HashMap<>();
        this.setupMapsForTables();
        calciteConnection.close();
        return;
    }

    public Connection getJDBCConnection() throws SQLException {
        return DriverManager.getConnection(this.url, this.username, this.password);
    }

    private void setupMapsForTables() throws SQLException {
        Connection conn = DriverManager.getConnection(this.url, this.username, this.password);
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet tables = meta.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                final String catalog = tables.getString("TABLE_CAT");
                final String schema = tables.getString("TABLE_SCHEM");
                final String tableName = tables.getString("TABLE_NAME");
                SortedMap<Integer, String> columnMap = new TreeMap<Integer, String>();
                try (ResultSet columns = meta.getColumns(catalog, schema, tableName, null)) {
                    while (columns.next()) {
                        columnMap.put(columns.getInt("ORDINAL_POSITION"),
                                columns.getString("COLUMN_NAME"));
                    }
                }
                List<String> columnNames = new ArrayList<>(columnMap.values());
                SortedMap<Integer, Integer> primaryKeyColumnMap = new TreeMap<Integer, Integer>();
                try (ResultSet primaryKeys = meta.getPrimaryKeys(catalog, schema, tableName)) {
                    while (primaryKeys.next()) {
                        primaryKeyColumnMap.put(primaryKeys.getInt("KEY_SEQ"),
                                columnNames.indexOf(primaryKeys.getString("COLUMN_NAME")));
                    }
                }
                List<Integer> keyColumnIndexes = new ArrayList<>(primaryKeyColumnMap.values());
                this.mapTableToColumns.put(tableName, columnNames);
                if (keyColumnIndexes.size() > 0) {
                    this.mapTableToPKColumnIndexes.put(tableName, keyColumnIndexes);
                }

                SortedMap<Integer, Integer> indexedColumnMap = new TreeMap<Integer, Integer>();
                try (ResultSet indexedCols = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
                    while (indexedCols.next()) {
                        indexedColumnMap.put(indexedCols.getInt("ORDINAL_POSITION"),
                                columnNames.indexOf(indexedCols.getString("COLUMN_NAME")));
                    }
                }
                List<Integer> idxColumnIndexes = new ArrayList<>(indexedColumnMap.values());
                if (idxColumnIndexes.size() > 0) {
                    this.mapTableToIdxColumnsIndexes.put(tableName, idxColumnIndexes);
                }

            }
        }
        conn.close();
        return;
    }

    public QueryContext analyze(String query, int pageSize) throws AnalyzerException, SqlParseException {
        SqlParser parser = SqlParser.create(query, SqlParser.Config.DEFAULT.withCaseSensitive(false)
                .withUnquotedCasing(Casing.UNCHANGED));
        SqlValidatorImpl validator = (SqlValidatorImpl) SqlValidatorUtil.newValidator(
                this.sqlStdOperatorTable,
                this.calciteCatalogReader,
                this.sqlTypeFactory,
                SqlValidator.Config.DEFAULT);
        // SqlValidator.Config.DEFAULT.withColumnReferenceExpansion(false)); // still
        // doesn't seem
        // to help (with ORDER BY)
        // SqlValidator.Config.DEFAULT.withColumnReferenceExpansion(true)); // still
        // doesn't seem to
        // help (with ORDER BY)
        QueryContext qc = new QueryContext(this, parser, validator, query, pageSize);

        // DEBUG: COMPILE/EXECUTE, this is for DEV ONLY:
        // for (DContext d : qc.xNodeToDContext.values()) {
        // System.out.printf("===== Debugging Context %s =====\n", d.xnode.id);
        // if (d == qc.dContextRoot) {
        // System.out.println("***** this is the root debugging context *****");
        // }
        // System.out.println("*** executing ***");
        // JsonObject codeJson = d.code.toJsonObject();

        // DEBUG: test milestone
        // int tableIdx = 6;
        // JsonObject mstSqlJson =
        // codeJson.get("milestoneSQLs").getAsJsonArray().get(tableIdx).getAsJsonObject();
        // JsonArray bindings = mstSqlJson.get("expected_bindings").getAsJsonArray();
        // System.out.println(this.executeMilestoneToJson(prettyPrintGson.toJson(mstSqlJson),
        // prettyPrintGson.toJson(bindings)));

        // DEBUG: test page fetch
        // JsonArray contents =
        // fromJsonByDefault(this.executeMilestoneToJson(prettyPrintGson.toJson(mstSqlJson),
        // prettyPrintGson.toJson(bindings)),
        // JsonObject.class).get("contents").getAsJsonArray();
        // JsonObject pageSqlJson =
        // codeJson.get("pageSQLs").getAsJsonArray().get(tableIdx).getAsJsonObject();
        // JsonObject pageSqlFilter = pageSqlJson.get("expected_filters").getAsJsonObject().deepCopy();
        // int pgNum = 0;
        // pageSqlFilter.get(d.id).getAsJsonObject().add("blm_value", null);
        // pageSqlFilter.get(d.id).getAsJsonObject().add("iid_lower",
        // contents.get(pgNum).getAsJsonArray().get(2));
        // pageSqlFilter.get(d.id).getAsJsonObject().add("iid_upper", contents.get(pgNum +
        // 1).getAsJsonArray().get(2));
        // if (contents.size() > 3) {
        // JsonArray row = contents.get(pgNum).getAsJsonArray();
        // pageSqlFilter.get(d.id).getAsJsonObject().add("sarg_values", IntStream.range(3, row.size())
        // .mapToObj(row::get)
        // .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
        // } else {
        // pageSqlFilter.get(d.id).getAsJsonObject().add("sarg_values", null);
        // }
        // System.out.println(prettyPrintGson.toJson(pageSqlFilter));
        // System.out.println(prettyPrintGson.toJson(pageSqlJson));
        // System.out.println(this.executePageToJson(prettyPrintGson.toJson(pageSqlJson),
        // prettyPrintGson.toJson(bindings),
        // prettyPrintGson.toJson(pageSqlFilter)));

        // DEBUG: test onWhereEval
        // JsonObject evalSqlJson = codeJson.get("onWhereEvalSQL").getAsJsonObject();
        // JsonArray evalBindings = evalSqlJson.get("expected_bindings").getAsJsonArray();
        // JsonArray rows = evalSqlJson.get("expected_rows").getAsJsonArray().deepCopy();
        // for (int i = 0; i < rows.size(); i++) {
        // JsonArray jsonArr = new JsonArray();
        // if (rows.get(i).getAsJsonObject().get("table_name").getAsString().equals("f")) {
        // jsonArr.add("Amy");
        // jsonArr.add("Apex");
        // jsonArr.add(3);
        // rows.get(i).getAsJsonObject().add("value", jsonArr);
        // } else if (rows.get(i).getAsJsonObject().get("table_name").getAsString().equals("l")) {
        // jsonArr.add("Amy");
        // jsonArr.add("Corona");
        // rows.get(i).getAsJsonObject().add("value", jsonArr);
        // } else if (rows.get(i).getAsJsonObject().get("table_name").getAsString().equals("s")) {
        // jsonArr.add("Apex");
        // jsonArr.add("Corona");
        // jsonArr.add(2.5);
        // rows.get(i).getAsJsonObject().add("value", jsonArr);
        // }
        // }
        // System.out.println(this.executeEvalToJson(prettyPrintGson.toJson(evalSqlJson),
        // prettyPrintGson.toJson(evalBindings),
        // prettyPrintGson.toJson(rows)));
        // }
        return qc;
    }

    public String analyzeToJson(String query, int pageSize) {
        try {
            QueryContext qc = this.analyze(query, pageSize);
            return prettyPrintGson.toJson(qc.toJsonObject());
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error",
                    (e instanceof SqlParseException) ? "error in parsing SQL query"
                            : "error in analyzing SQL query");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    // public String executeMilestoneToJson(String jsonSql, String jsonBindings) {
    //     try {
    //         ParameterizedSQL mstQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
    //         Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
    //                 fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
    //         DContextMilestoneExecd execd = mstQuery.executeMilestoneQuery(this, bindings, new HashMap<>());
    //         return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
    //     } catch (Exception e) {
    //         JsonObject json = new JsonObject();
    //         json.addProperty("error", "error in executing milestone SQL query.");
    //         json.addProperty("message", e.getMessage());
    //         StringWriter sw = new StringWriter();
    //         e.printStackTrace(new PrintWriter(sw));
    //         json.addProperty("stack_trace", sw.toString());
    //         System.out.println(sw.toString());
    //         return prettyPrintGson.toJson(json);
    //     }
    // }

    public String executeMilestoneToJson(String jsonSql, String jsonBindings, String jsonPins) {
        try {
            ParameterizedSQL mstQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
            Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
                    fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins = fromJsonByDefault(jsonPins, new TypeToken<Map<ParameterizedSQL.SerializedPin, List<Object>>>() {}.getType());
            DContextMilestoneExecd execd = mstQuery.executeMilestoneQuery(this, bindings, pins);
            return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "error in executing milestone SQL query.");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    public String executeMilestoneToJson(String jsonRequest) {
        try {
            Gson gson = new GsonBuilder().serializeNulls().create();
            JsonObject req = gson.fromJson(jsonRequest, JsonObject.class);
            String jsonSql = gson.toJson(req.get("sql"));
            String jsonBindings = gson.toJson(req.get("bindings"));
            String jsonPins = req.has("pins") ? gson.toJson(req.get("pins")) : "[]";
            return this.executeMilestoneToJson(jsonSql, jsonBindings, jsonPins);
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "error in executing milestone SQL query.");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    public String executePageToJson(String jsonSql, String jsonBindings, String jsonFilters, String jsonPins) {
        try {
            ParameterizedSQL pgQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
            Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
                    fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
            Map<String, Map<String, Object>> filters = fromJsonByDefault(jsonFilters, new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins = fromJsonByDefault(jsonPins, new TypeToken<Map<ParameterizedSQL.SerializedPin, List<Object>>>() {}.getType());
            DContextPageExecd execd = pgQuery.executePageQuery(this, bindings, filters, pins);
            return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "error in executing page SQL query.");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    // public String executePageToJson(String jsonSql, String jsonBindings, String jsonFilters) {
    //     try {
    //         ParameterizedSQL pgQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
    //         Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
    //                 fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
    //         Map<String, Map<String, Object>> filters = fromJsonByDefault(jsonFilters, new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
    //         DContextPageExecd execd = pgQuery.executePageQuery(this, bindings, filters, new HashMap<>());
    //         return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
    //     } catch (Exception e) {
    //         JsonObject json = new JsonObject();
    //         json.addProperty("error", "error in executing page SQL query.");
    //         json.addProperty("message", e.getMessage());
    //         StringWriter sw = new StringWriter();
    //         e.printStackTrace(new PrintWriter(sw));
    //         json.addProperty("stack_trace", sw.toString());
    //         System.out.println(sw.toString());
    //         return prettyPrintGson.toJson(json);
    //     }
    // }

    public String executePageToJson(String jsonRequest) {
        try {
            Gson gson = new GsonBuilder().serializeNulls().create();
            JsonObject req = gson.fromJson(jsonRequest, JsonObject.class);
            String jsonSql = gson.toJson(req.get("sql"));
            String jsonBindings = gson.toJson(req.get("bindings"));
            String jsonFilters = gson.toJson(req.get("filters"));
            String jsonPins = req.has("pins") ? gson.toJson(req.get("pins")) : "[]";
            ParameterizedSQL pgQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
            Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
                    fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
            Map<String, Map<String, Object>> filters = fromJsonByDefault(jsonFilters, new TypeToken<Map<String, Map<String, Object>>>() {}.getType());
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins = fromJsonByDefault(jsonPins, new TypeToken<Map<ParameterizedSQL.SerializedPin, List<Object>>>() {}.getType());
            DContextPageExecd execd = pgQuery.executePageQuery(this, bindings, filters, pins);
            return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "error in executing page SQL query.");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    public String executeEvalToJson(String jsonSql, String jsonBindings, String jsonRows) {
        try {
            ParameterizedSQL pgQuery = fromJsonByDefault(jsonSql, ParameterizedSQL.class);
            Map<ParameterizedSQL.SerializedColumnRef, Object> bindings =
                    fromJsonByDefault(jsonBindings, new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType());
            Map<ParameterizedSQL.SerializedRow, List<Object>> rows =
                    fromJsonByDefault(jsonRows, new TypeToken<Map<ParameterizedSQL.SerializedRow, List<Object>>>() {}.getType());
            DContextEvalExecd execd = pgQuery.executeEvalQuery(this, bindings, rows);
            return prettyPrintGson.toJson(toJsonTreeByDefault(execd));
        } catch (Exception e) {
            JsonObject json = new JsonObject();
            json.addProperty("error", "error in executing eval SQL query.");
            json.addProperty("message", e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            json.addProperty("stack_trace", sw.toString());
            System.out.println(sw.toString());
            return prettyPrintGson.toJson(json);
        }
    }

    public SqlNameMatcher getSqlNameMatcher() {
        return this.calciteCatalogReader.nameMatcher();
    }

    public List<Integer> getPKColumnIndexes(String tableName) {
        for (String key : this.mapTableToPKColumnIndexes.keySet()) {
            if (this.getSqlNameMatcher().matches(key, tableName)) {
                return this.mapTableToPKColumnIndexes.get(key);
            }
        }
        return new ArrayList<>(Arrays.asList(-1));
    }

    public List<String> getPKColumnNames(String tableName) {
        for (String key : this.mapTableToPKColumnIndexes.keySet()) {
            if (this.getSqlNameMatcher().matches(key, tableName)) {
                return this.mapTableToPKColumnIndexes.get(key).stream()
                        .map(i -> this.mapTableToColumns.get(key).get(i))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>(Arrays.asList("ctid"));
    }

    public List<Integer> getIdxColumnIndexes(String tableName) {
        // List<Integer> res = new ArrayList<>(Arrays.asList(-1)); // consider ctid
        List<Integer> res = new ArrayList<>();
        for (String key : this.mapTableToIdxColumnsIndexes.keySet()) {
            if (this.getSqlNameMatcher().matches(key, tableName)) {
                res.addAll(this.mapTableToIdxColumnsIndexes.get(key));
                break;
            }
        }
        return res;
    }

    public List<String> getIdxColumnNames(String tableName) {
        // List<String> res = new ArrayList<>(Arrays.asList("ctid")); // consider ctid
        List<String> res = new ArrayList<>();
        for (String key : this.mapTableToIdxColumnsIndexes.keySet()) {
            if (this.getSqlNameMatcher().matches(key, tableName)) {
                res.addAll(this.mapTableToIdxColumnsIndexes.get(key).stream()
                        .map(i -> this.mapTableToColumns.get(key).get(i))
                        .collect(Collectors.toList()));
            }
        }
        return res;
    }

    public int findMatchingNameInList(String name, List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            if (this.getSqlNameMatcher().matches(name, names.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public int findMatchingNameInFields(String name, List<RelDataTypeField> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (this.getSqlNameMatcher().matches(name, fields.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasDuplicateNames(List<String> names) {
        for (int i = 0; i < names.size() - 1; i++) {
            for (int j = i + 1; j < names.size(); j++) {
                if (this.getSqlNameMatcher().matches(names.get(i), names.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    public RelDataType createSqlType(SqlTypeName typeName) {
        return this.sqlTypeFactory.createSqlType(typeName);
    }

    public SqlWriter createSqlWriter() {
        return new SqlPrettyWriter(this.sqlWriterConfig);
    }

    public SqlWriter createSqlWriterCompact() {
        return new SqlPrettyWriter(this.sqlWriterConfigCompact);
    }

    public static GsonBuilder getDefaultGsonBuilder() {
        JsonDeserializer<DContextCode> customDContextDeserializer = new JsonDeserializer<DContextCode>() {
            @Override
            public DContextCode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = json.getAsJsonObject();
                String type = jsonObject.get("type").getAsString();
                if (type.equals("DSelectContextCode")) {
                    return context.deserialize(json, DSelectContextCode.class);
                } else if (type.equals("DSetOpContextCode")) {
                    return context.deserialize(json, DSetOpContextCode.class);
                } else {
                    throw new JsonParseException("DContext JSON object has unexpected type: " + type);
                }
            }
        };
        Type customBindingsType = new TypeToken<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {}.getType();
        JsonDeserializer<Map<ParameterizedSQL.SerializedColumnRef, Object>> customBindingsDeserializer =
                new JsonDeserializer<Map<ParameterizedSQL.SerializedColumnRef, Object>>() {
                    @Override
                    public Map<ParameterizedSQL.SerializedColumnRef, Object> deserialize(
                            JsonElement json, Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                        Map<ParameterizedSQL.SerializedColumnRef, Object> map = new HashMap<>();
                        JsonArray jsonArray = json.getAsJsonArray();
                        for (int i = 0; i < jsonArray.size(); i++) {
                            ParameterizedSQL.SerializedColumnRef ref = context.deserialize(jsonArray.get(i), ParameterizedSQL.SerializedColumnRef.class);
                            Object value = null;
                            JsonPrimitive jsonValue = jsonArray.get(i).getAsJsonObject().get("value").getAsJsonPrimitive();
                            if (jsonValue.isNumber()) {
                                value = jsonValue.getAsBigDecimal();
                            } else if (jsonValue.isString()) {
                                value = jsonValue.getAsString();
                            } else if (jsonValue.isBoolean()) {
                                value = jsonValue.getAsBoolean();
                            }
                            map.put(ref, value);
                        }
                        return map;
                    }
                };
        Type customFiltersType = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
        JsonDeserializer<Map<String, Map<String, Object>>> customFiltersDeserializer =
                new JsonDeserializer<Map<String, Map<String, Object>>>() {
                    @Override
                    public Map<String, Map<String, Object>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                        Map<String, Map<String, Object>> map = new HashMap<>();
                        JsonObject jsonObj = json.getAsJsonObject();
                        for (String key : jsonObj.keySet()) {
                            JsonObject obj = jsonObj.get(key).getAsJsonObject();
                            ParameterizedSQL.SerializedFilter ref = context.deserialize(obj, ParameterizedSQL.SerializedFilter.class);
                            Map<String, Object> tpMap = new HashMap<>();
                            JsonArray iidLower = obj.get("iid_lower").getAsJsonArray();
                            tpMap.put("iid_lower", jsonArraytoObjectList(iidLower));
                            if (!obj.get("iid_upper").isJsonNull()) {
                                tpMap.put("iid_upper", jsonArraytoObjectList(obj.get("iid_upper").getAsJsonArray()));
                            }
                            JsonArray sargs = obj.get("sarg_values").isJsonNull() ? null : obj.get("sarg_values").getAsJsonArray();
                            if (sargs != null) {
                                tpMap.put("sarg_lower",
                                        jsonArraytoObjectList(IntStream.range(0, sargs.size()).mapToObj(i -> sargs.get(i).getAsJsonArray().get(0))
                                                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)));
                                tpMap.put("sarg_upper",
                                        jsonArraytoObjectList(IntStream.range(0, sargs.size()).mapToObj(i -> sargs.get(i).getAsJsonArray().get(1))
                                                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)));
                            }
                            String blmVal = obj.get("blm_value").isJsonNull() ? null : obj.get("blm_value").getAsString();
                            if (blmVal != null) {
                                tpMap.put("blm_value", blmVal);
                            }
                            map.put(ref.ctxID, tpMap);
                        }
                        return map;
                    }
                };
        Type customPinsType = new TypeToken<Map<ParameterizedSQL.SerializedPin, List<Object>>>() {}.getType();
        JsonDeserializer<Map<ParameterizedSQL.SerializedPin, List<Object>>> customPinsDeserializer =
                new JsonDeserializer<Map<ParameterizedSQL.SerializedPin, List<Object>>>() {
                    @Override
                    public Map<ParameterizedSQL.SerializedPin, List<Object>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                        Map<ParameterizedSQL.SerializedPin, List<Object>> map = new HashMap<>();
                        JsonArray jsonArr = json.getAsJsonArray();
                        for (int i = 0; i < jsonArr.size(); i++) {
                            JsonObject obj = jsonArr.get(i).getAsJsonObject();
                            ParameterizedSQL.SerializedPin pin = context.deserialize(obj, ParameterizedSQL.SerializedPin.class);
                            if (!obj.get("value").isJsonNull()) {
                                map.put(pin, jsonArraytoObjectList(obj.get("value").getAsJsonArray()));
                            }
                        }
                        return map;
                    }
                };
        Type customRowsType = new TypeToken<Map<ParameterizedSQL.SerializedRow, List<Object>>>() {}.getType();
        JsonDeserializer<Map<ParameterizedSQL.SerializedRow, List<Object>>> customRowsDesrializer =
                new JsonDeserializer<Map<ParameterizedSQL.SerializedRow, List<Object>>>() {
                    @Override
                    public Map<ParameterizedSQL.SerializedRow, List<Object>> deserialize(JsonElement json, Type typeOfT,
                            JsonDeserializationContext context) throws JsonParseException {
                        Map<ParameterizedSQL.SerializedRow, List<Object>> map = new LinkedHashMap<>();
                        JsonArray jsonArr = json.getAsJsonArray();
                        for (int i = 0; i < jsonArr.size(); i++) {
                            JsonObject obj = jsonArr.get(i).getAsJsonObject();
                            ParameterizedSQL.SerializedRow row = context.deserialize(obj, ParameterizedSQL.SerializedRow.class);
                            List<Object> values = new ArrayList<>();
                            JsonArray jsonVals = obj.get("value").getAsJsonArray();
                            for (int j = 0; j < jsonVals.size(); j++) {
                                values.add(jsonVals.get(j).getAsString());
                            }
                            map.put(row, values);
                        }
                        return map;
                    }
                };
        return new GsonBuilder().serializeNulls()
                .registerTypeAdapter(DContextCode.class, customDContextDeserializer)
                .registerTypeAdapter(customBindingsType, customBindingsDeserializer)
                .registerTypeAdapter(customFiltersType, customFiltersDeserializer)
                .registerTypeAdapter(customRowsType, customRowsDesrializer)
                .registerTypeAdapter(customPinsType, customPinsDeserializer);
    }

    public static List<Object> jsonArraytoObjectList(JsonArray jsonArr) {
        List<Object> res = new ArrayList<>();
        for (JsonElement x : jsonArr) {
            if (x.isJsonPrimitive()) {
                JsonPrimitive val = x.getAsJsonPrimitive();
                if (val.isNumber()) {
                    res.add(val.getAsBigDecimal());
                } else if (val.isBoolean()) {
                    res.add(val.getAsBoolean());
                } else {
                    res.add(val.getAsString());
                }
            } else if (x.isJsonArray()) {
                res.add(jsonArraytoObjectList(x.getAsJsonArray()));
            } else if (x.isJsonNull()) {
                res.add(null);
            }
        }
        return res;
    }

    public static JsonElement toJsonTreeByDefault(Object src) {
        return defaultGson.toJsonTree(src);
    }

    public static <T> T fromJsonByDefault(String json, Class<T> classOfT) {
        return defaultGson.fromJson(json, classOfT);
    }

    public static <T> T fromJsonByDefault(String json, Type typeOfT) {
        return defaultGson.fromJson(json, typeOfT);
    }

}

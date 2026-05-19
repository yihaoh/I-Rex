package edu.duke.cs.irex.sqlanalyzer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.gson.annotations.SerializedName;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextEvalExecd;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextMilestone;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextMilestoneExecd;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContextPageExecd;
import edu.duke.cs.irex.sqlanalyzer.xnode.XColumnRefNode;

public class ParameterizedSQL {
    public static Set<String> numTypes = new HashSet<>(Arrays.asList("TINYINT", "SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "FLOAT", "DOUBLE"));
    public static Set<String> strTypes = new HashSet<>(Arrays.asList("CHAR", "VARCHAR", "BINARY", "VARBINARY"));
    public static Set<String> dateTypes = new HashSet<>(Arrays.asList("DATE", "DATETIME", "TIMESTAMP"));

    @SerializedName(value = "sql")
    public final String sql;

    public static class SerializedColumnRef extends Object {
        @SerializedName(value = "xSelectNode_id")
        public final String xSelectNodeId;
        @SerializedName(value = "xSelectNode_input_index")
        public final int inputIndex;
        @SerializedName(value = "xSelectNode_input_column_index")
        public final int inputColumnIndex;
        @SerializedName(value = "data_type")
        public final String type;

        public SerializedColumnRef(String xSelectNodeId, int inputIndex, int inputColumnIndex,
                String type) {
            this.xSelectNodeId = xSelectNodeId;
            this.inputIndex = inputIndex;
            this.inputColumnIndex = inputColumnIndex;
            this.type = type;
            return;
        }

        public SerializedColumnRef(XColumnRefNode c) throws AnalyzerException {
            this(c.selectNode.id, c.getSelectInputIndex(), c.getSelectInputColumnIndex(),
                    c.selectNode.inputTables.get(c.getSelectInputIndex()).getRecordType()
                            .getFieldList().get(c.getSelectInputColumnIndex()).getType()
                            .toString());
            return;
        }

        @Override
        public String toString() {
            return String.format("%s[%d][%d](%s)", this.xSelectNodeId, this.inputIndex,
                    this.inputColumnIndex, this.type);
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SerializedColumnRef)) {
                return false;
            } else {
                SerializedColumnRef o = (SerializedColumnRef) obj;
                return this.xSelectNodeId.equals(o.xSelectNodeId)
                        && this.inputIndex == o.inputIndex
                        && this.inputColumnIndex == o.inputColumnIndex;
            }
        }
    }

    @SerializedName(value = "expected_bindings")
    public final SerializedColumnRef[] columnRefs;

    public static class SerializedFilter extends Object {
        @SerializedName(value = "iid_columns")
        public final List<String> iidCols;
        @SerializedName(value = "iid_types")
        public final List<String> iidTypes;
        @SerializedName(value = "iid_dirs")
        public final List<Boolean> iidDirs;
        @SerializedName(value = "sargable_columns")
        public final List<String> sargCols;
        @SerializedName(value = "sargable_types")
        public final List<String> sargTypes;
        @SerializedName(value = "bloom_columns")
        public final List<String> blmCols;
        @SerializedName(value = "bloom_types")
        public final List<String> blmTypes;
        @SerializedName(value = "milestone_index")
        public final int mstIndex;
        @SerializedName(value = "context_id")
        public final String ctxID;

        public SerializedFilter(DContextMilestone mst) {
            this.iidCols = mst.iid.cols;
            this.iidTypes = mst.iid.types;
            this.iidDirs = mst.iid.dirs;
            this.sargCols = mst.sarg == null ? null : mst.sarg.cols;
            this.sargTypes = mst.sarg == null ? null : mst.sarg.types;
            this.blmCols = mst.blm == null ? null : mst.blm.cols;
            this.blmTypes = mst.blm == null ? null : mst.blm.types;
            this.mstIndex = mst.mstIndex;
            this.ctxID = mst.ctxID;
            if (this.iidTypes != null) {
                for (int i = 0; i < this.iidTypes.size(); i++) {
                    if (this.iidTypes.get(i).equals("DOUBLE")) {
                        this.iidTypes.set(i, "DOUBLE PRECISION");
                    }
                }
            }
            if (this.sargTypes != null) {
                for (int i = 0; i < this.sargTypes.size(); i++) {
                    if (this.sargTypes.get(i).equals("DOUBLE")) {
                        this.sargTypes.set(i, "DOUBLE PRECISION");
                    }
                }
            }
            if (this.blmTypes != null) {
                for (int i = 0; i < this.blmTypes.size(); i++) {
                    if (this.blmTypes.get(i).equals("DOUBLE")) {
                        this.blmTypes.set(i, "DOUBLE PRECISION");
                    }
                }
            }
            return;
        }

        @Override
        public String toString() {
            return String.format("Filter: %s[%d]", this.ctxID, this.mstIndex);
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SerializedFilter)) {
                return false;
            } else {
                SerializedFilter o = (SerializedFilter) obj;
                return this.ctxID == o.ctxID && this.mstIndex == o.mstIndex;
            }
        }
    }

    @SerializedName(value = "expected_filters")
    public final Map<String, SerializedFilter> filters;

    public static class SerializedPin extends Object {
        @SerializedName(value = "iid_columns")
        public final List<String> iidCols;
        @SerializedName(value = "iid_types")
        public final List<String> iidTypes;
        @SerializedName(value = "iid_dirs")
        public final List<Boolean> iidDirs;
        @SerializedName(value = "milestone_index")
        public final int mstIndex;
        @SerializedName(value = "context_id")
        public final String ctxID;

        public SerializedPin(DContextMilestone mst) {
            this.iidCols = mst.iid.cols;
            this.iidTypes = mst.iid.types;
            this.iidDirs = mst.iid.dirs;
            this.mstIndex = mst.mstIndex;
            this.ctxID = mst.ctxID;
            if (this.iidTypes != null) {
                for (int i = 0; i < this.iidTypes.size(); i++) {
                    if (this.iidTypes.get(i).equals("DOUBLE")) {
                        this.iidTypes.set(i, "DOUBLE PRECISION");
                    }
                }
            }
        }

        @Override
        public String toString() {
            return String.format("Pin: %s[%d]", this.ctxID, this.mstIndex);
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SerializedPin)) {
                return false;
            }
            SerializedPin o = (SerializedPin) obj;
            return this.ctxID == o.ctxID && this.mstIndex == o.mstIndex;
        }
    }

    @SerializedName(value = "expected_pins")
    public final List<SerializedPin> pins;

    public static class SerializedRow extends Object {
        @SerializedName(value = "table_name")
        public final String name;
        @SerializedName(value = "columns")
        public final List<String> columns;
        @SerializedName(value = "types")
        public final List<String> types;

        public SerializedRow(String name, List<String> columns, List<String> types) {
            this.name = name;
            this.columns = columns;
            this.types = types;
            if (this.types != null) {
                for (int i = 0; i < this.types.size(); i++) {
                    if (this.types.get(i).equals("DOUBLE")) {
                        this.types.set(i, "DOUBLE PRECISION");
                    }
                }
            }
        }

        @Override
        public String toString() {
            List<String> exprs = new ArrayList<>();
            exprs.add(this.name);
            exprs.add(this.columns.toString());
            exprs.add(this.types.toString());
            return String.join(" | ", exprs);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SerializedRow)) {
                return false;
            }
            SerializedRow o = (SerializedRow) obj;
            return this.name.equals(o.name) && this.columns.equals(o.columns) && this.types.equals(o.types);
        }
    }

    @SerializedName(value = "expected_rows")
    public final List<SerializedRow> rows;

    public ParameterizedSQL(String sql, SerializedColumnRef[] columnRefs, Map<String, SerializedFilter> filters, List<SerializedRow> rows,
            List<SerializedPin> pins) {
        this.sql = sql;
        this.columnRefs = columnRefs;
        this.filters = filters;
        this.rows = rows;
        this.pins = pins;
        return;
    }

    public ParameterizedSQL(String sql, List<XColumnRefNode> xColumnRefs, Map<String, DContextMilestone> filters, List<SerializedRow> rows,
            List<SerializedPin> pins)
            throws AnalyzerException {
        this.sql = sql;
        this.columnRefs = new SerializedColumnRef[xColumnRefs.size()];
        for (int i = 0; i < this.columnRefs.length; i++) {
            this.columnRefs[i] = new SerializedColumnRef(xColumnRefs.get(i));
        }

        this.filters = new HashMap<>();
        for (String key : filters.keySet()) {
            this.filters.put(key, new SerializedFilter(filters.get(key)));
        }

        this.rows = rows;
        this.pins = pins;
        return;
    }

    public ParameterizedSQL(String sql) {
        this(sql, new SerializedColumnRef[0], new HashMap<>(), new ArrayList<>(), new ArrayList<>());
        return;
    }

    public ParameterizedSQL() {
        this("");
        return;
    }

    public ParameterizedSQL parenthesized() {
        return new ParameterizedSQL("(" + this.sql + ")", this.columnRefs, this.filters, this.rows, this.pins);
    }

    public ParameterizedSQL parenthesizedWithCaution() {
        return new ParameterizedSQL("(\n" + this.sql + "\n)", this.columnRefs, this.filters, this.rows, this.pins);
    }

    public ParameterizedSQL concat(String sql) {
        return new ParameterizedSQL(this.sql + sql, this.columnRefs, this.filters, this.rows, this.pins);
    }

    public ParameterizedSQL concat(final ParameterizedSQL parSql) {
        return new ParameterizedSQL(
                this.sql + parSql.sql,
                Stream.of(this.columnRefs, parSql.columnRefs).flatMap(Stream::of)
                        .toArray(SerializedColumnRef[]::new),
                Stream.concat(this.filters.entrySet().stream(), parSql.filters.entrySet().stream())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (v1, v2) -> v2)),
                Stream.concat(this.rows.stream(), parSql.rows.stream()).collect(Collectors.toList()),
                Stream.concat(this.pins.stream(), parSql.pins.stream()).collect(Collectors.toList())); // retain a copy from v1 when duplicate key
    }

    public String toString() {
        String out = this.sql + "\n[";
        out += Stream.of(this.columnRefs).map(r -> r.toString()).collect(Collectors.joining(", "));
        out += "]";
        return out;
    }

    public static ParameterizedSQL join(String separator, List<ParameterizedSQL> list) {
        ParameterizedSQL result = new ParameterizedSQL();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                result = result.concat(separator);
            }
            result = result.concat(list.get(i));
        }
        return result;
    }

    private static String constructPinFilterString(ParameterizedSQL.SerializedPin pin, List<Object> values) {
        String vars = "(" + String.join(", ", pin.iidCols) + ")";
        String vals = String.format("(%s)", String.join(", ", IntStream.range(0, values.size())
                .mapToObj(i -> values.get(i) == null ? "NULL"
                        : String.format("'%s'::%s", values.get(i).toString().replace("'", "''"), pin.iidTypes.get(i)))
                .collect(Collectors.toList())));
        // return String.format("%s = %s", vars, vals);
        return String.format("IS_IID_EQUAL(%s, %s)", vars, vals);
    }

    public DContextMilestoneExecd executeMilestoneQuery(final Analyzer analyzer, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins) throws AnalyzerException {
        return new DContextMilestoneExecd(analyzer, this, bindings, pins);
    }

    public PreparedStatement prepareMilestoneQuery(Connection conn, Map<SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins) throws SQLException, AnalyzerException {
        Pattern pat = Pattern.compile("!\\(([a-z0-9]+),(pin)\\)s");
        Matcher matcher = pat.matcher(this.sql);
        StringBuffer newSql = new StringBuffer();
        while (matcher.find()) {
            // String id = matcher.group(1);
            // String filterType = matcher.group(2);
            StringBuffer pinStr = new StringBuffer();
            pinStr.append("(");
            for (ParameterizedSQL.SerializedPin pin : pins.keySet()) {
                List<Object> values = pins.get(pin);
                if (pinStr.length() > 1) {
                    pinStr.append(" AND ");
                }
                pinStr.append(constructPinFilterString(pin, values));
            }
            pinStr.append(")");

            matcher.appendReplacement(newSql, Matcher.quoteReplacement(pinStr.toString()));
        }
        matcher.appendTail(newSql);
        // System.out.println(newSql.toString());

        PreparedStatement st = conn.prepareStatement(newSql.toString());
        for (int i = 0; i < this.columnRefs.length; i++) {
            if (!bindings.containsKey(this.columnRefs[i])) {
                throw new AnalyzerException(
                        "missing binding for column reference " + this.columnRefs[i].toString());
            }
            Object value = bindings.get(this.columnRefs[i]);
            // String type = this.columnRefs[i].type;
            st.setObject(i + 1, value);
        }
        return st;
    }

    public DContextEvalExecd executeEvalQuery(final Analyzer analyzer, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedRow, List<Object>> rows) throws AnalyzerException {
        return new DContextEvalExecd(analyzer, this, bindings, rows);
    }

    public PreparedStatement prepareEvalQuery(Connection conn, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedRow, List<Object>> rows) throws SQLException, AnalyzerException {
        String newSql = this.sql;
        for (Map.Entry<SerializedRow, List<Object>> entry : rows.entrySet()) {
            SerializedRow row = entry.getKey();
            List<Object> values = entry.getValue();
            // Pattern pat = Pattern.compile(String.format("!\\(([a-z0-9]+,%s)\\)s",
            // row.name));
            String tp = String.join(", ", IntStream.range(0, values.size()).mapToObj(i -> String.format("'%s'::%s", values.get(i), row.types.get(i)))
                    .collect(Collectors.toList()));
            newSql = newSql.replaceAll(String.format("!\\(([a-z0-9]+,%s)\\)s", row.name), "(" + tp + ")");
        }

        PreparedStatement st = conn.prepareStatement(newSql);
        for (int i = 0; i < this.columnRefs.length; i++) {
            if (!bindings.containsKey(this.columnRefs[i])) {
                throw new AnalyzerException(
                        "missing binding for column reference " + this.columnRefs[i].toString());
            }
            Object value = bindings.get(this.columnRefs[i]);
            st.setObject(i + 1, value);
        }
        return st;
    }

    public DContextPageExecd executePageQuery(final Analyzer analyzer, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<String, Map<String, Object>> filters, Map<ParameterizedSQL.SerializedPin, List<Object>> pins)
            throws AnalyzerException {
        return new DContextPageExecd(analyzer, this, bindings, filters, pins);
    }

    public PreparedStatement preparePageQuery(Connection conn, Map<SerializedColumnRef, Object> bindings, Map<String, Map<String, Object>> filters,
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins) throws SQLException, AnalyzerException {
        Pattern pat = Pattern.compile("!\\(([a-z0-9]+),(iid|sarg|all|pin)\\)s");
        Matcher matcher = pat.matcher(this.sql);
        StringBuffer newSql = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            String filterType = matcher.group(2);
            StringBuffer filterStr = new StringBuffer();

            // construct pin filter string
            if (filterType.equals("pin")) {
                // pins are required for pin filter type
                if (pins == null || pins.isEmpty()) {
                    throw new AnalyzerException("No pins found for filter type pin");
                }
                filterStr.append("(");
                for (ParameterizedSQL.SerializedPin pin : pins.keySet()) {
                    List<Object> values = pins.get(pin);
                    if (filterStr.length() > 1) {
                        filterStr.append(" AND ");
                    }
                    filterStr.append(constructPinFilterString(pin, values));
                }
                filterStr.append(")");
                matcher.appendReplacement(newSql, Matcher.quoteReplacement(filterStr.toString()));
                continue;
            }

            if (filters == null || !filters.containsKey(id)) {
                // if the filter is not found, we proceed without any filter
                // this might happen for contexts under bag operations as we do not have good ways to track bag ops IID
                filterStr.append("TRUE");
                matcher.appendReplacement(newSql, Matcher.quoteReplacement(filterStr.toString()));
                continue;
            }

            // construct iid filter string
            if (filterType.equals("iid") || filterType.equals("all")) {
                List<Object> iidLower = (List<Object>) filters.get(id).get("iid_lower");
                List<Object> iidUpper = filters.get(id).containsKey("iid_upper") ? (List<Object>) filters.get(id).get("iid_upper") : null;
                if (iidLower.size() != this.filters.get(id).iidCols.size()) {
                    throw new AnalyzerException(String.format("Filter ID: %s should have %d iid column values but got %d", id,
                            this.filters.get(id).iidCols.size(), iidLower.size()));
                }

                String res = "";
                String vars = "(" + String.join(", ", this.filters.get(id).iidCols) + ")";
                String lo = String.format("(%s)",
                        String.join(", ",
                                IntStream.range(0, this.filters.get(id).iidTypes.size())
                                        .mapToObj(i -> iidLower.get(i) == null ? String.format("NULL::%s", this.filters.get(id).iidTypes.get(i))
                                                : String.format("'%s'::%s", iidLower.get(i).toString().replace("'", "''"),
                                                        this.filters.get(id).iidTypes.get(i)))
                                        .collect(Collectors.toList())));
                // res += this.filters.get(id).iidDirs.get(0) ? String.format("%s <= %s", vars, lo) : String.format("%s >= %s", vars, lo);
                res += String.format("IS_IID_GE(%s, %s, %s)", vars, lo,
                        "ARRAY[" + this.filters.get(id).iidDirs.stream().map(b -> b ? "true" : "false").collect(Collectors.joining(", ")) + "]");
                if (iidUpper != null) {
                    String up = String.format("(%s)",
                            String.join(", ",
                                    IntStream.range(0, this.filters.get(id).iidTypes.size())
                                            .mapToObj(i -> iidUpper.get(i) == null ? String.format("NULL::%s", this.filters.get(id).iidTypes.get(i))
                                                    : String.format("'%s'::%s", iidUpper.get(i).toString().replace("'", "''"),
                                                            this.filters.get(id).iidTypes.get(i)))
                                            .collect(Collectors.toList())));
                    // res += this.filters.get(id).iidDirs.get(0) ? String.format(" AND %s > %s", vars, up)
                    //         : String.format(" AND %s < %s", vars, up);
                    res += String.format(" AND IS_IID_LESS(%s, %s, %s)", vars, up,
                            "ARRAY[" + this.filters.get(id).iidDirs.stream().map(b -> b ? "true" : "false").collect(Collectors.joining(", ")) + "]");
                }
                filterStr.append(res);
            }

            // construct sargable filter string
            if (this.filters.get(id).sargCols != null && !filterType.equals("iid")) {
                List<Object> sargLower = (List<Object>) filters.get(id).get("sarg_lower");
                List<Object> sargUpper = (List<Object>) filters.get(id).get("sarg_upper");
                if (this.filters.get(id).sargCols.size() != sargLower.size()) {
                    throw new AnalyzerException(String.format("Filter ID: %s should have %d sargable column values but got %d", id,
                            this.filters.get(id).sargCols.size(), sargLower.size()));
                }
                List<String> sargs = new ArrayList<>();
                for (int i = 0; i < this.filters.get(id).sargCols.size(); i++) {
                    if (this.filters.get(id).sargTypes.get(i).equals("ANY")) {
                        // don't really need to use the IID sargable, not really helpful
                        continue;
                    }
                    String expr = this.filters.get(id).sargCols.get(i);
                    String lo = String.format("'%s'::%s", sargLower.get(i).toString().replace("'", "''"), this.filters.get(id).sargTypes.get(i));
                    String up = String.format("'%s'::%s", sargUpper.get(i).toString().replace("'", "''"), this.filters.get(id).sargTypes.get(i));
                    sargs.add(String.format("%s BETWEEN %s AND %s", expr, lo, up));
                }
                if (filterStr.length() > 0 && sargs.size() > 0) {
                    // only need this when iid precedes sargables
                    filterStr.append(" AND ");
                }
                filterStr.append(String.join(" AND ", sargs));
            }

            // construct bloom filter string
            if (this.filters.get(id).blmCols != null && !filterType.equals("iid")) {
                List<String> exprs = new ArrayList<>();
                for (int i = 0; i < this.filters.get(id).blmCols.size(); i++) {
                    String expr = this.filters.get(id).blmCols.get(i);
                    String type = this.filters.get(id).blmTypes.get(i);
                    if (numTypes.contains(type)) {
                        exprs.add("numeric_send(" + expr + ")");
                    } else if (strTypes.contains(type)) {
                        exprs.add("textsend(" + expr + ")");
                    } else if (dateTypes.contains(type)) {
                        exprs.add("date_send(" + expr + ")");
                    } else {
                        exprs.add("textsend(" + expr + "::text)");
                    }
                }
                String blmVal = (String) filters.get(id).get("blm_value");
                exprs.add("'" + blmVal + "'");
                filterStr.append(" AND ");
                filterStr.append(String.format("BLMFL_TEST(%s)", String.join(", ", exprs)));
            }

            // Use quoteReplacement to avoid issues with special regex characters in the
            // replacement
            matcher.appendReplacement(newSql, Matcher.quoteReplacement(filterStr.toString()));
        }
        matcher.appendTail(newSql);
        // System.out.println(newSql.toString());

        PreparedStatement st = conn.prepareStatement(newSql.toString());
        for (int i = 0; i < this.columnRefs.length; i++) {
            if (!bindings.containsKey(this.columnRefs[i])) {
                throw new AnalyzerException("missing binding for column reference " + this.columnRefs[i].toString());
            }
            Object value = bindings.get(this.columnRefs[i]);
            // String type = this.columnRefs[i].type;
            st.setObject(i + 1, value);
        }
        return st;
    }
}

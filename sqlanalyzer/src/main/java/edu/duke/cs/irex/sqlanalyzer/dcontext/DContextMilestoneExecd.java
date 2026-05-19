package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.NestedArrayList;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext.DContextExecd;

public class DContextMilestoneExecd extends DContextExecd {

    public DContextMilestoneExecd(final Analyzer analyzer, ParameterizedSQL sql, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedPin, List<Object>> pins)
            throws AnalyzerException {
        super();
        Connection conn = null;
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            conn = analyzer.getJDBCConnection();
            conn.setAutoCommit(false);
            st = sql.prepareMilestoneQuery(conn, bindings, pins);
            rs = st.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                NestedArrayList curRow = new NestedArrayList();
                // we know seq, count and IID lead the way
                curRow.add(rs.getInt(1));
                curRow.add(rs.getInt(2));
                curRow.add(iidJsonStringToList(Analyzer.fromJsonByDefault(rs.getString(3), JsonElement.class).getAsJsonObject(), 2));

                // process any sargable
                for (int i = 4; i <= meta.getColumnCount(); i++) {
                    // try getting it as Array, if not Array, it should be String (bloom filter)
                    try {
                        Array arr = rs.getArray(i);
                        if (meta.getColumnLabel(i).endsWith("_iid")) {
                            Object[] tp = (Object[]) arr.getArray();
                            JsonObject lower = Analyzer.fromJsonByDefault(tp[0].toString(), JsonElement.class).getAsJsonObject();
                            JsonObject upper = Analyzer.fromJsonByDefault(tp[1].toString(), JsonElement.class).getAsJsonObject();
                            curRow.add(new NestedArrayList(Arrays.asList(iidJsonStringToList(lower, 1), iidJsonStringToList(upper, 1))));
                        } else if (meta.getColumnLabel(i).endsWith("_iid_mix")) {
                            Object[] tp = (Object[]) arr.getArray();
                            JsonObject lower = Analyzer.fromJsonByDefault(tp[0].toString(), JsonElement.class).getAsJsonObject();
                            JsonObject upper = Analyzer.fromJsonByDefault(tp[1].toString(), JsonElement.class).getAsJsonObject();
                            curRow.add(new NestedArrayList(Arrays.asList(iidJsonStringToList(lower, 2), iidJsonStringToList(upper, 2))));
                        } else {
                            curRow.add(new NestedArrayList(Arrays.asList((Object[]) arr.getArray())));
                        }
                    } catch (SQLException e) {
                        curRow.add(rs.getString(i));
                    }
                }
                this.contents.add(curRow);
            }
            rs.close();
            st.close();
            conn.close();
        } catch (SQLException e) {
            try {
                rs.close();
            } catch (Exception x) {
                /* Ignored */ }
            try {
                st.close();
            } catch (Exception x) {
                /* Ignored */ }
            try {
                conn.close();
            } catch (Exception x) {
                /* Ignored */ }
            throw new AnalyzerException("backend database error", e);
        }
    }
}

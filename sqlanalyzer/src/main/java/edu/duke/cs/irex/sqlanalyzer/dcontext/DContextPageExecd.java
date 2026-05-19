package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.NestedArrayList;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext.DContextExecd;

public class DContextPageExecd extends DContextExecd {

    public DContextPageExecd(final Analyzer analyzer, ParameterizedSQL sql, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<String, Map<String, Object>> filters, Map<ParameterizedSQL.SerializedPin, List<Object>> pins) throws AnalyzerException {
        super();
        Connection conn = null;
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            conn = analyzer.getJDBCConnection();
            conn.setAutoCommit(false);
            st = sql.preparePageQuery(conn, bindings, filters, pins);
            // System.out.println(st.toString());
            rs = st.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                NestedArrayList curRow = new NestedArrayList();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    // simply add the object to it and let Gson does the type conversion
                    if (meta.getColumnName(i).contains("iid")) {
                        curRow.add(iidJsonStringToList(Analyzer.fromJsonByDefault(rs.getString(i), JsonElement.class).getAsJsonObject(), 1));
                    } else {
                        curRow.add(rs.getObject(i));
                    }
                }
                this.contents.add(curRow);
            }
            rs.close();
            st.close();
            conn.close();
        }
        catch (SQLException e) {
            try {
                rs.close();
            }
            catch (Exception x) { /* Ignored */ }
            try {
                st.close();
            }
            catch (Exception x) { /* Ignored */ }
            try {
                conn.close();
            }
            catch (Exception x) {
                /* Ignored */ }
            // System.out.println(st.toString());
            throw new AnalyzerException("backend database error", e);
        }
    }
}

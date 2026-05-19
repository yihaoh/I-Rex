package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.NestedArrayList;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext.DContextExecd;


public class DContextEvalExecd extends DContextExecd {
    public DContextEvalExecd(final Analyzer analyzer, ParameterizedSQL sql, Map<ParameterizedSQL.SerializedColumnRef, Object> bindings,
            Map<ParameterizedSQL.SerializedRow, List<Object>> rows) throws AnalyzerException {
        super();
        Connection conn = null;
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            conn = analyzer.getJDBCConnection();
            conn.setAutoCommit(false);
            st = sql.prepareEvalQuery(conn, bindings, rows);
            rs = st.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                NestedArrayList curRow = new NestedArrayList();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    curRow.add(rs.getObject(i));
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


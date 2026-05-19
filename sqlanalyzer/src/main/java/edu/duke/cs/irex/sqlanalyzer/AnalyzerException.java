package edu.duke.cs.irex.sqlanalyzer;

import org.apache.calcite.sql.SqlNode;

import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;

public class AnalyzerException extends Exception {
    public AnalyzerException() {
        super("internal error");
    }
    public AnalyzerException(String message) {
        super(message);
    }
    public AnalyzerException(QueryContext qc, SqlNode sqlNode, String message) {
        super(String.format("%s: %s\n%s", sqlNode.getParserPosition(), XNode.substringAtSqlParserPos(qc.query, sqlNode.getParserPosition()), message));
    }
    public AnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}

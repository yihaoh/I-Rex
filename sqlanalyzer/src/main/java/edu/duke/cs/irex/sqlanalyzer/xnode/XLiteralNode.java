package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XLiteralNode extends XCellValuedNode {
    public final String displayString;
    public XLiteralNode(QueryContext qc, SqlLiteral sqlLiteral, XNode parent, SqlValidatorScope sqlScope) {
        super(qc, sqlLiteral, parent, sqlScope);
        this.displayString = sqlLiteral.toString(); // TODO: consider SqlWriter?
        // TODO: potentially consider sqlLiteral.getValue() as well
        return;
    }
    @Override public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("display_string", this.displayString);
        return json;
    }
}

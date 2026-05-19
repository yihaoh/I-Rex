package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlAsOperator;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * Renaming an output column or expression, using the AS construct in the SELECT clause.
 * Note: We don't use this class to capture input column renaming in FROM or WITH table definitions.
 */
public class XColumnRenameNode extends XCellValuedNode {
    public final String name;
    public XColumnRenameNode(QueryContext qc, SqlBasicCall sqlBasicCall, XNode parent, SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlBasicCall, parent, sqlScope);
        assert sqlBasicCall.getOperator() instanceof SqlAsOperator;
        qc.transform(sqlBasicCall.operand(0), this, sqlScope, false);
        assert sqlBasicCall.operand(1) instanceof SqlIdentifier;
        this.name = ((SqlIdentifier)(sqlBasicCall.operand(1))).names.get(0);
        return;
    }
    @Override public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("new_name", this.name);
        json.add("operand", this.children.get(0).toJsonObject());
        return json;
    }
}

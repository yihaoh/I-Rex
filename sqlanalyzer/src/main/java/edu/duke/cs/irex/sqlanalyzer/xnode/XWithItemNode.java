package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XWithItemNode extends XTableValuedNode {
    public final String tableName; // there may be column names too; look into getRecordType()
    public final XTableValuedNode query;
    public XWithItemNode(QueryContext qc, SqlWithItem sqlWithItem, XNode parent, SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlWithItem, parent, sqlScope);
        this.tableName = sqlWithItem.name.getSimple();
        this.query = (XTableValuedNode)(qc.transform(sqlWithItem.query, this, sqlScope, false));
        return;
    }
    @Override public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("table_name", this.tableName);
        json.add("query", this.query.toJsonObject());
        return json;
    }
}

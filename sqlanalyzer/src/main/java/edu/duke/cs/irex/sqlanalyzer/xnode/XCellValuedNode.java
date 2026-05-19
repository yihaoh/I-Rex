package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * An expression that returns a "singleton" value, for lack of a better name (as opposed to
 * {@link XTableValuedNode}, which returns a collection of rows). That said, even a "singleton"
 * value can be as complex as a row, e.g., {@code (x.a, y.b)}.
 */
public class XCellValuedNode extends XNode {
    public final RelDataType relDataType; // type for the value of this expression

    public XCellValuedNode(QueryContext qc, SqlNode sqlNode, XNode parent,
            SqlValidatorScope sqlScope) {
        super(qc, sqlNode, parent, sqlScope);
        RelDataType relDataType = null;
        try {
            relDataType = qc.validator.getValidatedNodeTypeIfKnown(sqlNode);
        } catch (UnsupportedOperationException e) {
            // This case happens with unqualified column references in ORDER BY:
            // to get the type of the new (qualified) column reference Calcite looks up the original
            // one,
            // but apparently no type inference took place there.
            System.err.println("WARNING: error getting type for " + sqlNode.toString());
            relDataType = null;
        }
        // TODO: Besdies the ORDER BY case above, Calcite also seems to have trouble inferring type
        // for COALESCE,
        // which arises in natural joins, will silently return null.
        // We might want to look for a better solution there.
        this.relDataType = relDataType;
        return;
    }

    /**
     * Useful for constructing something that doesn't come from the original query's
     * parsed/validated tree. The data type for the value must be specified, because we can't rely
     * on Calcite to validate it.
     * 
     * @param qc
     * @param sqlType
     * @param parent
     */
    public XCellValuedNode(QueryContext qc, RelDataType sqlType, XNode parent) {
        super(qc, null, parent, null);
        this.relDataType = sqlType;
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("data_type",
                (this.relDataType == null) ? null : this.relDataType.toString());
        if (this.sqlNode != null) {
            SqlWriter writer = this.qc.analyzer.createSqlWriterCompact();
            this.sqlNode.unparse(writer, 0, 0);
            json.addProperty("sql_string", writer.toString());
        }
        return json;
    }

    public XCellValuedNode withoutRename() {
        if (this instanceof XColumnRenameNode) {
            return (XCellValuedNode) (this.children.get(0));
        } else {
            return this;
        }
    }

    @Override
    public String toString() {
        if (this.sqlNode != null) {
            SqlWriter writer = this.qc.analyzer.createSqlWriterCompact();
            this.sqlNode.unparse(writer, 0, 0);
            return writer.toString();
        }
        return "";
    }
}

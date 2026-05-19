package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.List;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.validate.OrderByScope;
import org.apache.calcite.sql.validate.SelectScope;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.apache.calcite.sql.validate.SqlQualified;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * A reference to a column from one of the input tables in some FROM clause. Note: This is not
 * intended to handle the wildcard "*" (which may expand to a list of columns).
 */
public class XColumnRefNode extends XCellValuedNode {
    public final SqlQualified sqlQualified;
    public final SelectScope resolvedSelectScope;
    public final XSelectNode selectNode;
    public final boolean isScopeLocal;

    public XColumnRefNode(QueryContext qc, SqlIdentifier sqlIdentifier, XNode parent,
            SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlIdentifier, parent, sqlScope);
        // Note that the following will not work for *, so make sure any * is already expanded:
        this.sqlQualified = sqlScope.fullyQualify(sqlIdentifier);
        final SqlNameMatcher matcher = this.qc.analyzer.getSqlNameMatcher();
        final SqlValidatorScope.ResolvedImpl resolved = new SqlValidatorScope.ResolvedImpl();
        sqlScope.resolve(sqlIdentifier.names, matcher, true, resolved);
        if (resolved.count() != 1) {
            throw new AnalyzerException(qc, sqlIdentifier, "column reference cannot be resolved");
        }
        this.resolvedSelectScope = (SelectScope) (resolved.only().scope);
        this.isScopeLocal = (this.resolvedSelectScope == sqlScope
                || (sqlScope instanceof OrderByScope
                        && ((OrderByScope) sqlScope).getParent() == this.resolvedSelectScope));
        this.selectNode = (XSelectNode) (this.qc.sqlToXNode.get(resolvedSelectScope.getNode()));
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("XSelectNode_id", this.selectNode.id);
        json.addProperty("is_scope_local", this.isScopeLocal);
        this.checkStage(2);
        json.addProperty("XSelectNode_input_index", this.getSelectInputIndex());
        json.addProperty("XSelectNode_input_column_index", this.getSelectInputColumnIndex());
        return json;
    }

    public boolean isInternalTo(XNode n) throws AnalyzerException {
        this.checkStage(1);
        return this.selectNode.isDescendantOf(n);
    }

    private int selectInputIndex = -1;
    private int selectInputColumnIndex = -1;

    @Override
    protected void refineImpl() throws AnalyzerException {
        // Search within this.selectNode for the indexes for the correct input table and column:
        List<String> names = this.sqlQualified.identifier.names;
        String tableName = names.get(names.size() - 2);
        String columnName = names.get(names.size() - 1);
        this.selectInputIndex =
                qc.analyzer.findMatchingNameInList(tableName, this.selectNode.inputTableAliases);
        if (this.selectInputIndex == -1) {
            throw new AnalyzerException(this.qc, this.sqlNode, "cannot find table " + tableName);
        }
        List<String> columnNames =
                this.selectNode.inputTables.get(this.selectInputIndex).relRowType.getFieldNames();
        this.selectInputColumnIndex = qc.analyzer.findMatchingNameInList(columnName, columnNames);
        if (this.selectInputColumnIndex == -1) {
            throw new AnalyzerException(this.qc, this.sqlNode, "cannot find column " + columnName);
        }
        return;
    }

    public int getSelectInputIndex() throws AnalyzerException {
        this.checkStage(2);
        return this.selectInputIndex;
    }

    public int getSelectInputColumnIndex() throws AnalyzerException {
        this.checkStage(2);
        return this.selectInputColumnIndex;
    }
}

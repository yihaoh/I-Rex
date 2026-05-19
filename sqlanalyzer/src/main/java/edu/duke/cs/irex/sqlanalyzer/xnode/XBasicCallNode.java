package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSelectKeyword;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * Pretty much any SQL operator/function that returns a "singleton" value can be captured by this node.
 * Examples include {@code +}, {@code -}, {@code NOT IN}, {@code AND}, {@code LIKE}, {@code COALESCE}, {@code SUM}, etc.
 * Use {@link XNode.children} to access operands.
 * In general, operands may involve subqueries.
 * Two special types of calls worth mentioning are aggregates,
 * and scalar subqueries (which converts subquery results to a singleton value).
 */
public class XBasicCallNode extends XCellValuedNode {
    public final SqlOperator sqlOperator;
    public final boolean isAggregate;
    public final boolean isAggregateDistinct;
    public final boolean isScalarSubquery;
    public XBasicCallNode(QueryContext qc, SqlBasicCall sqlBasicCall, XNode parent, SqlValidatorScope sqlScope, boolean isVisitingSelectFrom) throws AnalyzerException {
        super(qc, sqlBasicCall, parent, sqlScope);
        this.sqlOperator = sqlBasicCall.getOperator();
        this.isAggregate = (this.sqlOperator instanceof SqlAggFunction);
        if (this.isAggregate) {
            SqlLiteral keyword = sqlBasicCall.getFunctionQuantifier();
            this.isAggregateDistinct = (keyword != null) && (keyword.getValue().equals(SqlSelectKeyword.DISTINCT));
        } else {
            this.isAggregateDistinct = false;
        }
        this.isScalarSubquery = (this.sqlOperator.getKind() == SqlKind.SCALAR_QUERY);
        if (sqlBasicCall.isCountStar()) {
            // Replace COUNT(*) with COUNT(1) because * doesn't really refer to "all columns" in this case.
            SqlNode literal1 = qc.validator.validate(SqlLiteral.createExactNumeric("1", sqlBasicCall.getOperands()[0].getParserPosition()));
            qc.transform(literal1, this, sqlScope, isVisitingSelectFrom);
        } else {
            for (SqlNode n : sqlBasicCall.getOperandList()) {
                qc.transform(n, this, sqlScope, isVisitingSelectFrom);
            }
        }
        return;
    }

    @Override
    public String toString() {
        if (this.sqlNode != null) {
            SqlWriter writer = this.qc.analyzer.createSqlWriterCompact();
            this.sqlNode.unparse(writer, 0, 0);
            if (this.sqlOperator.getKind() == SqlKind.ROW) {
                return "ROW(" + writer.toString() + ")";
            }
            return writer.toString();
        }
        return "";
    }

    @Override public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("operator_name", this.sqlOperator.getName());
        json.addProperty("is_aggregate", this.isAggregate);
        json.addProperty("is_aggregate_distinct", this.isAggregateDistinct);
        json.addProperty("is_scalar_subquery", this.isScalarSubquery);
        json.add("operands", toJsonArrayOfXNodes(this.children));
        return json;
    }
}
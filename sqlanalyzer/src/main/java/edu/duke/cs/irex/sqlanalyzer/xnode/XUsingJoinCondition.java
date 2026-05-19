package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.type.SqlTypeName;

import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * A special boolean-valued {@link XCellValuedNode} to capture a USING join condition, attached
 * under an XJoinNode and specified as a list of commonly named columns to be joined. For a NATURAL
 * XJoinNode, we will infer the USING condition ourselves, so the resulting
 * {@link XUsingJoinCondition} node may not have any associated SqlNodes.
 */
/*
 * One might wonder why we do not represent USING (col) as a regular XBasicCallNode condition such
 * as t1.col = t2.col, and treat both kinds of conditions uniformly. The trouble is that, for
 * example, col in one of the input tables may in fact have come from two different tables (say with
 * NATURAL FULL OUTER JOIN), and prefixing col with either table would not give the intended result.
 * Here is concrete example to illustrate: -- Q1: supported by PostgreSQL but not Calcite
 * ("Column name 'J' in USING clause is not unique on one side of join") -- Calcite would be fine
 * with NATURAL JOIN dd though WITH dd(j) AS (SELECT name FROM Drinker UNION SELECT address FROM
 * Drinker) SELECT * FROM Drinker d1(name, j) NATURAL FULL OUTER JOIN Drinker d2 (j, address) JOIN
 * dd USING (j) -- Q2: WITH dd(j) AS (SELECT name FROM Drinker UNION SELECT address FROM Drinker)
 * SELECT * FROM Drinker d1(name, j) NATURAL FULL OUTER JOIN Drinker d2 (j, address) JOIN dd ON d1.j
 * = dd.j -- Q3: WITH dd(j) AS (SELECT name FROM Drinker UNION SELECT address FROM Drinker) SELECT *
 * FROM Drinker d1(name, j) NATURAL FULL OUTER JOIN Drinker d2 (j, address) JOIN dd ON d2.j = dd.j
 * Guess what? These three queries in PostgreSQL return different answers!
 * 
 * As a side note, the last JOIN dd can be written as a common "comma" join with condition in WHERE.
 * In that case, we need to rename dd to make it work, but the distinction among j, d1.j, and d2.j
 * still remains: -- Q4: supported by PostgreSQL but not Calcite WITH dd(j) AS (SELECT name FROM
 * Drinker UNION SELECT address FROM Drinker) SELECT * FROM Drinker d1(name, j) NATURAL FULL OUTER
 * JOIN Drinker d2 (j, address), dd d(k) WHERE j = k Calcite, on the other hand, will complain about
 * j in WHERE being ambiguous, because it tries to resolve it against a single input table in FROM.
 * 
 * Our goal here is to support Q1-Q3, giving USING the same flexibility of NATURAL (hence going
 * beyond Calcite), but not to support Q4 because that will probably requiring too much working
 * around Calcite.
 */
public class XUsingJoinCondition extends XCellValuedNode {
    public final List<String> columnNames;
    public final List<Integer> leftColumnIndexes;
    public final List<Integer> rightColumIndexes;

    public XUsingJoinCondition(QueryContext qc, XJoinNode parent) throws AnalyzerException {
        super(qc, qc.analyzer.createSqlType(SqlTypeName.BOOLEAN), parent);
        SqlJoin sqlJoin = (SqlJoin) (parent.sqlNode);
        this.columnNames = qc.validator.usingNames(sqlJoin);
        this.leftColumnIndexes = new ArrayList<>();
        this.rightColumIndexes = new ArrayList<>();
        for (String columnName : this.columnNames) {
            int left = qc.analyzer.findMatchingNameInList(columnName,
                    parent.left.getRecordType().getFieldNames());
            if (left == -1) {
                throw new AnalyzerException(qc, parent.sqlNode,
                        "cannot locate column " + columnName + " in left input");
            } else {
                this.leftColumnIndexes.add(left);
            }
            int right = qc.analyzer.findMatchingNameInList(columnName,
                    parent.right.getRecordType().getFieldNames());
            if (right == -1) {
                throw new AnalyzerException(qc, parent.sqlNode,
                        "cannot locate column " + columnName + " in right input");
            } else {
                this.rightColumIndexes.add(right);
            }
        }
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        if (this.sqlNode == null) {
            json.addProperty("sql_string", "USING(" + String.join(", ", this.columnNames) + ")");
        }
        json.add("column_names", Analyzer.toJsonTreeByDefault(this.columnNames));
        json.add("left_column_indexes", Analyzer.toJsonTreeByDefault(this.leftColumnIndexes));
        json.add("right_column_indexes", Analyzer.toJsonTreeByDefault(this.rightColumIndexes));
        return json;
    }

    @Override
    public String toString() {
        return "USING(" + String.join(", ", this.columnNames) + ")";
    }
}

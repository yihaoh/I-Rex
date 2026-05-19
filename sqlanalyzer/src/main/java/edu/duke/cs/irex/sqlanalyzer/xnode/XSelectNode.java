package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext;

public class XSelectNode extends XTableValuedNode {
    public final boolean isDistinct;
    public final boolean isAggregate;
    public final XTableValuedNode fromExpr;
    public final List<XTableValuedNode> inputTables;
    public final List<String> inputTableAliases;
    public final List<XCellValuedNode> onExprs;
    public final XBasicCallNode whereExpr;
    public final List<XCellValuedNode> groupByExprs;
    public final XBasicCallNode havingExpr;
    public final SqlNodeList selectListWithStarsExpanded;
    public final List<XCellValuedNode> selectExprs;
    public final List<XCellValuedNode> sortExprs;
    public final List<Boolean> sortDirs;
    public final List<Integer> sortCols;

    public XSelectNode(QueryContext qc, SqlSelect sqlSelect, XNode parent,
            SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlSelect, parent, sqlScope);
        this.isDistinct = sqlSelect.isDistinct();
        this.isAggregate = qc.validator.isAggregate(sqlSelect);
        // Process FROM:
        this.fromExpr = (XTableValuedNode) (qc.transform(sqlSelect.getFrom(), this,
                qc.validator.getFromScope(sqlSelect), true));
        this.inputTables = new ArrayList<>();
        collectInputTables(this.fromExpr, this.inputTables);
        this.inputTableAliases = this.collectInputAliases(this.inputTables);
        if (qc.analyzer.hasDuplicateNames(this.inputTableAliases)) {
            throw new AnalyzerException(qc, this.fromExpr.sqlNode, "duplicate table names in FROM");
        }
        this.onExprs = new ArrayList<>();
        collectOnExprs(this.fromExpr, this.onExprs);
        // Process WHERE:
        this.whereExpr = (sqlSelect.getWhere() == null) ? null
                : (XBasicCallNode) (qc.transform(sqlSelect.getWhere(), this,
                        qc.validator.getWhereScope(sqlSelect), false));
        // Process GROUP BY:
        if (!this.isAggregate) {
            this.groupByExprs = null;
        } else {
            this.groupByExprs = new ArrayList<>(); // for an aggregate query, omitted GROUP BY means
                                                   // GROUP BY no column
            if (sqlSelect.getGroup() != null) {
                for (SqlNode n : sqlSelect.getGroup()) {
                    this.groupByExprs.add((XCellValuedNode) (qc.transform(n, this,
                            qc.validator.getGroupScope(sqlSelect), false)));
                }
            }
        }
        // Process HAVING:
        this.havingExpr = (sqlSelect.getHaving() == null) ? null
                : (XBasicCallNode) (qc.transform(sqlSelect.getHaving(), this,
                        qc.validator.getHavingScope(sqlSelect), false));
        // Process SELECT:
        this.selectExprs = new ArrayList<>();
        // Note that expanding * below will
        // 1) automatically rename columns with duplicate names per Calcite rules (which
        // may differ
        // from other DBMS), and
        // 2) NOT replace the old SELECT list inside sqlSelect.
        this.selectListWithStarsExpanded = qc.validator.expandStar(sqlSelect.getSelectList(), sqlSelect, false);
        for (SqlNode n : this.selectListWithStarsExpanded) {
            patchExpandedSelectExpr(n); // to handle a Calcite bug that, when generating COALESCE
                                        // for natural/USING join, adds a rename to the second
                                        // operand
            this.selectExprs.add((XCellValuedNode) (qc.transform(n, this,
                    qc.validator.getSelectScope(sqlSelect), false)));
        }
        // Process ORDER BY:
        if (sqlSelect.getOrderList() == null) {
            this.sortExprs = null;
            this.sortDirs = null;
            this.sortCols = null;
        } else {
            this.sortExprs = new ArrayList<>();
            this.sortDirs = new ArrayList<>();
            for (SqlNode n : sqlSelect.getOrderList()) {
                // Note that expansion will
                // 1) convert ordinals to expressions for corresponding output columns,
                // 2) cause these expressions to be validated and type-checked
                // (otherwise Calcite apparently misses really simple ORDER BY column
                // references).
                n = qc.validator.expandOrderExpr(sqlSelect, n);
                if (n.getKind() == SqlKind.DESCENDING) {
                    n = ((SqlCall) n).getOperandList().get(0);
                    this.sortDirs.add(true);
                } else {
                    this.sortDirs.add(false);
                }
                this.sortExprs.add((XCellValuedNode) (qc.transform(n, this,
                        qc.validator.getOrderScope(sqlSelect), false)));
            }
            // Okay, after going through all that above, for now we are going to insist that
            // every ORDER BY component be an output column anyway.
            this.sortCols = new ArrayList<>();
            for (XCellValuedNode sortExpr : this.sortExprs) {
                int found = this.findExprAsOutputCol(sortExpr);
                if (found == -1) {
                    throw new AnalyzerException(qc, sortExpr.sqlNode,
                            "cannot sort by something that is not an output column");
                } else {
                    this.sortCols.add(found);
                }
            }
        }
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("is_distinct", this.isDistinct);
        json.addProperty("is_aggregate", this.isAggregate);
        json.add("from_expr", this.fromExpr.toJsonObject());
        json.add("input_XTableValuedNode_ids", toJsonArrayOfXNodeIds(this.inputTables));
        json.add("input_table_aliases", Analyzer.toJsonTreeByDefault(this.inputTableAliases));
        json.add("on_conds", toJsonArrayOfXNodes(this.onExprs));
        json.add("where_cond", (this.whereExpr == null) ? null : this.whereExpr.toJsonObject());
        json.add("group_by_exprs", toJsonArrayOfXNodes(this.groupByExprs));
        json.add("having_cond", (this.havingExpr == null) ? null : this.havingExpr.toJsonObject());
        json.add("select_exprs", toJsonArrayOfXNodes(this.selectExprs));
        json.add("sort_exprs", toJsonArrayOfXNodes(this.sortExprs));
        json.add("sort_output_column_indexes", Analyzer.toJsonTreeByDefault(this.sortCols));
        json.add("sort_output_orders_descending", Analyzer.toJsonTreeByDefault(this.sortDirs));
        return json;
    }

    private static void patchExpandedSelectExpr(SqlNode n) {
        // Ignore top-level AS:
        if (n instanceof SqlBasicCall && ((SqlBasicCall) n).getOperator().getKind() == SqlKind.AS) {
            n = ((SqlBasicCall) n).getOperandList().get(0);
        }
        // Look for COALESCE:
        if (n instanceof SqlBasicCall
                && ((SqlBasicCall) n).getOperator().getKind() == SqlKind.COALESCE) {
            SqlBasicCall call = (SqlBasicCall) n;
            SqlNode second_operand = call.getOperandList().get(1);
            if (second_operand instanceof SqlBasicCall
                    && ((SqlBasicCall) second_operand).getOperator().getKind() == SqlKind.AS) {
                // Short-circuit (remove) the extra AS:
                SqlNode new_operand = ((SqlBasicCall) second_operand).getOperandList().get(0);
                call.setOperand(1, new_operand);
            }
        }
        return;
    }

    private static void collectInputTables(XNode n, List<XTableValuedNode> tables) {
        if (n instanceof XTableRenameNode || n instanceof XTableRefNode || n instanceof XSelectNode
                || n instanceof XSetOpNode) {
            tables.add((XTableValuedNode) n);
        } else {
            for (XNode c : n.children) {
                collectInputTables(c, tables);
            }
        }
        return;
    }

    private List<String> collectInputAliases(List<XTableValuedNode> tables)
            throws AnalyzerException {
        List<String> aliases = new ArrayList<>();
        for (XTableValuedNode n : tables) {
            String alias;
            if (n instanceof XTableRenameNode) {
                alias = ((XTableRenameNode) n).name;
            } else if (n instanceof XTableRefNode) {
                alias = ((XTableRefNode) n).name;
            } else {
                throw new AnalyzerException(this.qc, this.fromExpr.sqlNode,
                        "unsupported form of FROM");
            }
            aliases.add(alias);
        }
        return aliases;
    }

    private static void collectOnExprs(XTableValuedNode x, List<XCellValuedNode> onExprs)
            throws AnalyzerException {
        if (x instanceof XTableRenameNode) {
            collectOnExprs(x.withoutRename(), onExprs);
        } else if (x instanceof XJoinNode) {
            XJoinNode j = (XJoinNode) x;
            collectOnExprs(j.left, onExprs);
            collectOnExprs(j.right, onExprs);
            if (j.condition != null && !(j.condition instanceof XUsingJoinCondition)) {
                onExprs.add(j.condition);
            }
        }
        return;
    }

    public List<Integer> findInputTableIndexes(XNode n) throws AnalyzerException {
        List<XTableValuedNode> tableSubset = new ArrayList<>();
        collectInputTables(n, tableSubset);
        List<Integer> indexes = new ArrayList<>();
        for (XTableValuedNode t : tableSubset) {
            int index = this.inputTables.indexOf(t);
            if (index == -1) {
                throw new AnalyzerException(this.qc, t.sqlNode,
                        "unexpected error: input table not found");
            }
            indexes.add(index);
        }
        Collections.sort(indexes);
        return indexes;
    }

    public List<XJoinNode> getFancyJoinNodes() {
        List<XJoinNode> fancyJoins = new ArrayList<>();
        getFancyJoinNodesHelper(this.fromExpr, fancyJoins);
        return fancyJoins;
    }

    private static void getFancyJoinNodesHelper(XTableValuedNode x, List<XJoinNode> fancyJoins) {
        if (x instanceof XTableRenameNode) {
            getFancyJoinNodesHelper(x.withoutRename(), fancyJoins);
        } else if (x instanceof XJoinNode) {
            XJoinNode j = (XJoinNode) x;
            getFancyJoinNodesHelper(j.left, fancyJoins);
            getFancyJoinNodesHelper(j.right, fancyJoins);
            if (j.isFancy()) {
                fancyJoins.add(j);
            }
        }
        return;
    }

    public XColumnRefNode exprIsMostlyLocalColumnRef(XCellValuedNode e) {
        e = e.withoutRename();
        if (e instanceof XColumnRefNode && ((XColumnRefNode) e).isScopeLocal) {
            return (XColumnRefNode) e;
        } else if (e instanceof XBasicCallNode &&
                ((XBasicCallNode) e).sqlOperator.getKind() == SqlKind.COALESCE &&
                ((XBasicCallNode) e).children.get(0) instanceof XColumnRefNode &&
                ((XColumnRefNode) (((XBasicCallNode) e).children.get(0))).isScopeLocal) {
            // This may seem like a really corner case, but actually happens
            // naturally when SELECT * get expanded into COALESCE calls for a natural/USING
            // join.
            return (XColumnRefNode) (((XBasicCallNode) e).children.get(0));
        } else {
            return null;
        }
    }

    public boolean isExprLiteral(XCellValuedNode e) {
        e = e.withoutRename();
        return (e instanceof XLiteralNode);
    }

    public int findExprAsOutputCol(XCellValuedNode e) {
        for (int i = 0; i < this.selectExprs.size(); i++) {
            if (this.qc.cellValuedExprEqualsDeep(e, this.selectExprs.get(i).withoutRename())) {
                return i;
            }
        }
        return -1;
    }

    public int inputColumnToJoinResult(int inputIndex, int inputColumnIndex) {
        return this.inputColumnToJoinResult(this.inputTables.get(inputIndex), inputColumnIndex);
    }

    public int inputColumnToJoinResult(XTableValuedNode n, int columnIndex) {
        if (n.parent == this) {
            return columnIndex;
        } else if (n.parent instanceof XJoinNode) {
            XJoinNode join = (XJoinNode) (n.parent);
            int outputIndex = (join.left == n) ? join.leftColumnToOutputIndex.get(columnIndex)
                    : join.rightColumnToOutputIndex.get(columnIndex);
            return this.inputColumnToJoinResult(join, outputIndex);
        } else {
            return this.inputColumnToJoinResult((XTableValuedNode) (n.parent), columnIndex);
        }
    }

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        IID newIID = null;
        String ctxId = qc.xNodeToDContext.get(this).refWithContext != null ? qc.xNodeToDContext.get(this).refWithContext.id
                : qc.xNodeToDContext.get(this).id;
        // 1) If we have an ORDER BY, we definitely go with these first:
        if (this.sortCols != null) {
            List<String> cols = new ArrayList<>();
            List<Boolean> dirs = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<String> xnodes = new ArrayList<>();
            for (int i = 0; i < this.sortCols.size(); i++) {
                XCellValuedNode selectExpr = this.selectExprs.get(this.sortCols.get(i)).withoutRename();
                cols.add(selectExpr.toString());
                dirs.add(this.sortDirs.get(i));
                types.add(selectExpr.relDataType.getSqlTypeName().getName());
                xnodes.add(selectExpr.id);
            }
            newIID = new IID(cols, types, dirs, xnodes, ctxId);
        }
        // 2) If DISTINCT, add remaining columns and done
        if (this.isDistinct) {
            List<String> cols = new ArrayList<>();
            List<Boolean> dirs = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<String> xnodes = new ArrayList<>();
            for (int i = 0; i < this.selectExprs.size(); i++) {
                // make sure sortCols is not null or the i-th SELECT expr has not been added
                if (this.sortCols == null || !this.sortCols.contains(i)) {
                    XCellValuedNode selectExpr = this.selectExprs.get(i).withoutRename();
                    cols.add(selectExpr.toString());
                    dirs.add(false);
                    types.add(selectExpr.relDataType.getSqlTypeName().getName());
                    xnodes.add(selectExpr.id);
                }
            }
            IID tpIID = new IID(cols, types, dirs, xnodes, ctxId);
            return newIID == null ? tpIID : newIID.combineIID(tpIID, 0, ctxId);
        }
        // 2) Add remaining GROUP BY exprs to ensure uniqueness
        if (this.groupByExprs != null && this.groupByExprs.size() > 0) {
            List<String> cols = new ArrayList<>();
            List<Boolean> dirs = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<String> xnodes = new ArrayList<>();
            for (XCellValuedNode e : this.groupByExprs) {
                cols.add(e.toString());
                dirs.add(false);
                types.add(e.relDataType.getSqlTypeName().getName());
                xnodes.add(e.id);
            }
            IID tpIID = new IID(cols, types, dirs, xnodes, ctxId);
            // safely return, we got our unique IID
            return newIID == null ? tpIID : newIID.combineIID(tpIID, 0, ctxId);
        }
        // 3) No GROUP BY or aggregate, go with the Join&Filter IID
        if (this.fromExpr != null && !this.isAggregate) {
            IID joinFilterIID = this.fromExpr.getIID();
            if (newIID == null) {
                newIID = new IID(joinFilterIID.cols, joinFilterIID.types, joinFilterIID.dirs, joinFilterIID.xnodes, ctxId);
                return newIID;
            }
            return newIID.combineIID(joinFilterIID, 0, ctxId);
        }
        // 4) well... the fallback is always going with just SELECT exprs
        List<String> cols = new ArrayList<>();
        List<Boolean> dirs = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> xnodes = new ArrayList<>();
        for (XCellValuedNode e : this.selectExprs) {
            cols.add(e.withoutRename().toString());
            dirs.add(false);
            types.add(e.relDataType.getSqlTypeName().getName());
            xnodes.add(e.id);
        }
        return new IID(cols, types, dirs, xnodes, ctxId);
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        // have to concate everything into a ROW, otherwise will have ordering issue
        IID iid = this.getIID();
        List<String> newCols = iid.convertColsToAlias();
        boolean mixOrder = this.sortDirs == null ? false : !this.sortDirs.stream().allMatch(x -> !x);
        if (mixOrder) {
            newCols.add(0, this.qc.xNodeToDContext.get(this).id + "_seq");
            return new Sargable(newCols, iid.types, new ArrayList<>(Arrays.asList(0)), new ArrayList<>(Arrays.asList(-2)), this.id); // -2 means mix
                                                                                                                                     // order
        }
        return new Sargable(new ArrayList<>(Arrays.asList(String.format("ROW(%s)", String.join(", ", newCols)))), new ArrayList<>(Arrays.asList("ANY")),
                new ArrayList<>(Arrays.asList(0)),
                new ArrayList<>(Arrays.asList(-1)), this.id); // -1 means
        // consistent order
    }

    @Override
    protected Bloom getBloomImpl() throws AnalyzerException {
        if (this.hasCorrelatedSub == false) {
            return null;
        }
        List<XColumnRefNode> blmCols = DContext.collectCorrelatedColumnRefsBelongTo(whereExpr, this, this);
        return new Bloom(blmCols);
    }

    public boolean hasCorrelatedSub = false;

    @Override
    protected void refineImpl() throws AnalyzerException {
        // mark if this query block is correlated subquery
        this.hasCorrelatedSub = !DContext.collectColumnRefsExternalTo(this, this).isEmpty();
        for (XNode c : this.children) {
            c.refine();
        }
        return;
    }
}

package edu.duke.cs.irex.sqlanalyzer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlAsOperator;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlSetOperator;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.SqlValidatorImpl;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Litmus;

import edu.duke.cs.irex.sqlanalyzer.dcontext.DContext;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DSelectContext;
import edu.duke.cs.irex.sqlanalyzer.dcontext.DSetOpContext;
import edu.duke.cs.irex.sqlanalyzer.xnode.XBasicCallNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XCellValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XColumnRefNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XColumnRenameNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XJoinNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XLiteralNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSetOpNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableRefNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableRenameNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.IID;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithItemNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithNode;

/**
 * For each query being analyzed, there is a QueryContext object that holds
 * useful query-specific
 * and query-wide information.
 */
public class QueryContext {
    public final Analyzer analyzer;
    public final SqlParser parser;
    public final SqlValidatorImpl validator;
    public final String query;
    public final SqlNode validated;
    public final Map<SqlNode, XNode> sqlToXNode;
    public final XTableValuedNode xtree;
    public final Map<XNode, DContext> xNodeToDContext;
    // public final MutableGraph<DContext> dContextGraph; // do we really need to
    // construct this
    // graph before hand? may be just discover by logic?
    public final DContext dContextRoot;
    public final int pageSize;
    private int stage; // tracks where we are in analysis; some analysis can only be done in later
                       // stages

    public QueryContext(final Analyzer analyzer, final SqlParser parser,
            final SqlValidatorImpl validator, String query, int pageSize)
            throws AnalyzerException, SqlParseException {
        this.analyzer = analyzer;
        this.parser = parser;
        this.validator = validator;
        this.query = query;
        this.pageSize = pageSize;
        SqlNode parsed = parser.parseStmt();
        this.validated = validator.validate(parsed);
        this.sqlToXNode = new HashMap<>();
        // We proceed in multiple stages:
        // certain analysis relies on information derived in earlier stages;
        // analysis methods will check the stage number to make sure sure they have
        // ready to
        // proceed.
        this.stage = 0;
        // Technically, the "root" scope shouldn't be null, see Calcite's
        // SqlValidatorImpl.validate(SqlNode),
        // but null seems to work below:
        this.xtree = (XTableValuedNode) (this.transform(this.validated, null, null, false));
        this.stage++;
        this.xtree.refine();
        this.stage++;
        this.xNodeToDContext = new HashMap<>();
        this.dContextRoot = this.contextualize(new ArrayList<>(), new ArrayList<>(), this.xtree);
        this.prepareContextCode(this.dContextRoot); // create code object with dependencies in mind
        return;
    }

    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = new JsonObject();
        json.addProperty("original_query", this.query);
        json.add("xtree", xtree.toJsonObject());
        // JsonArray array = new JsonArray();
        // this.xNodeToDContext.values().stream().forEach(context ->
        // array.add(context.toJsonObject()));
        // json.add("dcontexts", array);
        JsonObject contexts = new JsonObject();
        this.xNodeToDContext.forEach((k, v) -> contexts.add(k.id, v.toJsonObject()));
        json.add("dcontexts", contexts);
        json.add("ctree", dContextRoot.toJsonObject());
        json.addProperty("root_dcontext_id", this.dContextRoot.id);
        return json;
    }

    public int stage() {
        return this.stage;
    }

    /**
     * The first pass of the analysis --- transforming a Calcite-validated
     * {@link SqlNode} tree into an
     * {@link XNode} tree and setting up most stuff. A separate second pass
     * {@link XNode.refine} will
     * then handle certain things that are tricky to set up during the same pass,
     * e.g., it is hard to
     * figure out what a {@link XColumnRefNode} (especially iniside FROM) refers to
     * in terms of indexes
     * of input tables and columns therein when the containing {@link XSelectNode}
     * is being created.
     *
     * @param sqlNode
     * @param parent
     * @param sqlScope
     * @param isVisitingSelectFrom
     * @return
     * @throws AnalyzerException
     */
    public XNode transform(SqlNode sqlNode, XNode parent, SqlValidatorScope sqlScope,
            boolean isVisitingSelectFrom) throws AnalyzerException {
        if (sqlNode instanceof SqlWith) {
            return new XWithNode(this, (SqlWith) sqlNode, parent, sqlScope);
        } else if (sqlNode instanceof SqlWithItem) {
            return new XWithItemNode(this, (SqlWithItem) sqlNode, parent, sqlScope);
        } else if (sqlNode instanceof SqlSelect) {
            return new XSelectNode(this, (SqlSelect) sqlNode, parent, sqlScope);
        } else if (sqlNode instanceof SqlLiteral) {
            return new XLiteralNode(this, (SqlLiteral) sqlNode, parent, sqlScope);
        } else if (sqlNode instanceof SqlIdentifier) {
            if (isVisitingSelectFrom) {
                return new XTableRefNode(this, (SqlIdentifier) sqlNode, parent, sqlScope);
            } else {
                return new XColumnRefNode(this, (SqlIdentifier) sqlNode, parent, sqlScope);
            }
        } else if (sqlNode instanceof SqlJoin) {
            return new XJoinNode(this, (SqlJoin) sqlNode, parent, sqlScope, isVisitingSelectFrom);
        } else if (sqlNode instanceof SqlBasicCall) {
            SqlBasicCall sqlBasicCall = (SqlBasicCall) sqlNode;
            if (sqlBasicCall.getOperator() instanceof SqlAsOperator) {
                if (isVisitingSelectFrom) {
                    return new XTableRenameNode(this, sqlBasicCall, parent, sqlScope);
                } else {
                    return new XColumnRenameNode(this, sqlBasicCall, parent, sqlScope);
                }
            } else if (sqlBasicCall.getOperator() instanceof SqlSetOperator) {
                // throw new AnalyzerException(this, sqlNode, "SQL feature not supported");
                return new XSetOpNode(this, sqlBasicCall, parent, sqlScope);
            } else if (sqlBasicCall.getOperator().getKind() == SqlKind.ROW) {
                // TODO: consider supporting ROW later
                throw new AnalyzerException(this, sqlNode, "SQL feature not supported");
            } else {
                return new XBasicCallNode(this, sqlBasicCall, parent, sqlScope,
                        isVisitingSelectFrom);
            }
        }
        throw new AnalyzerException(this, sqlNode, "SQL feature not supported");
    }

    public DContext contextualize(List<DContext> dContextAncestors, List<XWithNode> xWithAncestors,
            XNode n) throws AnalyzerException {
        if (n instanceof XWithNode) {
            XWithNode xWith = (XWithNode) n;
            xWithAncestors.add(xWith);
            // Process WITH items: even though they are not called now (by xWith.body), they
            // may be
            // called by others:
            for (XWithItemNode item : xWith.withItems) {
                this.contextualize(dContextAncestors, xWithAncestors, item.query);
            }
            // Process WITH body:
            DContext myroot = this.contextualize(dContextAncestors, xWithAncestors, xWith.body);
            xWithAncestors.remove(xWithAncestors.size() - 1);
            return myroot;
        } else if (n instanceof XSelectNode) {
            XSelectNode xSelect = (XSelectNode) n;
            DSelectContext dSelect = new DSelectContext(this, xSelect, dContextAncestors, xWithAncestors);
            dContextAncestors.add(dSelect);
            for (XTableValuedNode c : xSelect.inputTables) {
                // These may refer to tables defined by some WITH items,
                // but we are not further expanding these references here;
                // they have been processed during WITH processing.
                this.contextualize(dContextAncestors, xWithAncestors, c);
            }
            if (xSelect.whereExpr != null) {
                this.contextualize(dContextAncestors, xWithAncestors, xSelect.whereExpr);
            }
            if (xSelect.groupByExprs != null) {
                for (XCellValuedNode c : xSelect.groupByExprs) {
                    this.contextualize(dContextAncestors, xWithAncestors, c);
                }
            }
            if (xSelect.havingExpr != null) {
                this.contextualize(dContextAncestors, xWithAncestors, xSelect.havingExpr);
            }
            // Ignore ORDER BY because we assume it is covered by SELECT.
            for (XCellValuedNode c : xSelect.selectExprs) {
                this.contextualize(dContextAncestors, xWithAncestors, c);
            }
            dContextAncestors.remove(dContextAncestors.size() - 1);
            return dSelect;
        } else if (n instanceof XSetOpNode) {
            // throw new AnalyzerException("Set Operation not supported yet");
            XSetOpNode xSetOp = (XSetOpNode) n;
            DSetOpContext dSetOp = new DSetOpContext(this, xSetOp, dContextAncestors, xWithAncestors);
            dContextAncestors.add(dSetOp);
            this.contextualize(dContextAncestors, xWithAncestors, xSetOp.left);
            this.contextualize(dContextAncestors, xWithAncestors, xSetOp.right);
            dContextAncestors.remove(dContextAncestors.size() - 1);
            return dSetOp;
        } else if (n instanceof XTableRefNode && !(((XTableRefNode) n).isInDatabase)
                && ((XTableRefNode) n).withItem != null) {
            // copy-construct the context, no need to recurse
            XTableValuedNode tp = ((XTableRefNode) n).withItem.query;
            DContext myroot = tp instanceof XSelectNode
                    ? new DSelectContext(this.xNodeToDContext.get(tp),
                            dContextAncestors.get(dContextAncestors.size() - 1))
                    : new DSetOpContext(this.xNodeToDContext.get(tp),
                            dContextAncestors.get(dContextAncestors.size() - 1));
            return myroot;
        } else {
            DContext myroot = null;
            for (XNode c : n.children) {
                myroot = this.contextualize(dContextAncestors, xWithAncestors, c);
            }
            if (myroot != null && n.children.size() == 1) {
                return myroot;
            } else {
                return null;
            }
        }
    }

    public void prepareContextCode(DContext root) throws AnalyzerException {
        if (root == null) {
            return;
        } else if (root.refWithContext != null) {
            if (root.refWithContext.code == null) {
                root.refWithContext.code = root.refWithContext.prepareCode();
                if (root.refWithContext instanceof DSelectContext) {
                    this.prepareContextCode(((DSelectContext) root.refWithContext).joinCtx);
                }
            }
            root.code = root.refWithContext.code;
        }

        for (DContext c : root.children) {
            this.prepareContextCode(c);
        }
        root.code = root.prepareCode();
        if (root instanceof DSelectContext) {
            this.prepareContextCode(((DSelectContext) root).joinCtx);
        }
    }

    public boolean cellValuedExprEqualsDeep(XCellValuedNode e1, XCellValuedNode e2) {
        return e1.sqlNode.equalsDeep(e2.sqlNode, Litmus.IGNORE);
    }

    /**
     * A subclass of Calcite {@link SqlIdentifier} for column references, such that
     * when "unparsing" an
     * external correlated column reference into SQL, we can output a "?"
     * placeholder instead of the
     * reference.
     */
    public static class SqlColumnRef extends SqlIdentifier {
        public final XColumnRefNode xColumnRef;
        public final XTableValuedNode xContext;

        public SqlColumnRef(XColumnRefNode xColumnRef, XTableValuedNode xContext) {
            super(((SqlIdentifier) (xColumnRef.sqlNode)).names,
                    ((SqlIdentifier) (xColumnRef.sqlNode)).getParserPosition());
            this.xColumnRef = xColumnRef;
            this.xContext = xContext;
            return;
        }

        @Override
        public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
            try {
                if (this.xColumnRef.isInternalTo(this.xContext)) {
                    super.unparse(writer, leftPrec, rightPrec);
                } else {
                    writer.print("?");
                    writer.setNeedWhitespace(true);
                }
                return;
            }
            catch (AnalyzerException e) {
                throw new InternalError();
            }
        }
    }

    private static SqlSelect insertSelectIID(SqlSelect sqlSelect, DSelectContext ctx) throws AnalyzerException {

        // if not correlated subquery, insert IID
        IID iid = ((XSelectNode) ctx.xnode).getIID();
        SqlNodeList selectList = sqlSelect.getSelectList();
        SqlNode iidNode = new SqlIdentifier(iid.toSelectStringWithIIDAlias(true), SqlParserPos.ZERO);
        selectList.add(iidNode);

        // if it is a inline derived table (parent is AS operator), then need to check if columns are aliased
        // if yes, the need to add extra IID and seq columns to the renamed list
        // if (parent instanceof SqlBasicCall) {
        //     SqlBasicCall sqlCall = (SqlBasicCall) parent;
        //     if (sqlCall.getOperator() instanceof SqlAsOperator && sqlCall.operandCount() == 3) {
        //         for (int i = 0; i < iid.size(); i++) {
        //             ((SqlNodeList) sqlCall.operand(2)).add(new SqlIdentifier(iid.ctxId + "_iid_" + i, SqlParserPos.ZERO));
        //         }
        //         if (addSeq) {
        //             ((SqlNodeList) sqlCall.operand(2)).add(new SqlIdentifier(iid.ctxId + "_seq", SqlParserPos.ZERO));
        //         }
        //     }
        // }
        return sqlSelect;
    }

    private static SqlSelect insertSelectPushdown(SqlSelect sqlSelect, DSelectContext ctx) {
        if (((XSelectNode) ctx.xnode).isAggregate) {
            SqlNode curWhere = sqlSelect.getWhere();
            String whereFilterMark = String.format("!(%s,sarg)s", ctx.id);
            SqlNode whereFilterNode = new SqlIdentifier(whereFilterMark, SqlParserPos.ZERO);
            if (curWhere == null) {
                sqlSelect.setWhere(whereFilterNode);
            } else {
                SqlNode newWhere = new SqlBasicCall(
                        SqlStdOperatorTable.AND,
                        new SqlNode[] { curWhere, whereFilterNode },
                        SqlParserPos.ZERO);
                sqlSelect.setWhere(newWhere);
            }

            SqlNode curHaving = sqlSelect.getHaving();
            String havingFilterMark = String.format("!(%s,iid)s", ctx.id);
            SqlNode havingFilterNode = new SqlIdentifier(havingFilterMark, SqlParserPos.ZERO);
            if (curHaving == null) {
                sqlSelect.setHaving(havingFilterNode);
            } else {
                SqlNode newHaving = new SqlBasicCall(
                        SqlStdOperatorTable.AND,
                        new SqlNode[] { curHaving, havingFilterNode },
                        SqlParserPos.ZERO);
                sqlSelect.setHaving(newHaving);
            }
        } else {
            SqlNode curWhere = sqlSelect.getWhere();
            String filterMark = String.format("!(%s,all)s", ctx.id);
            SqlNode filterNode = new SqlIdentifier(filterMark, SqlParserPos.ZERO);
            if (curWhere == null) {
                sqlSelect.setWhere(filterNode);
            } else {
                SqlNode newWhere = new SqlBasicCall(
                        SqlStdOperatorTable.AND,
                        new SqlNode[] { curWhere, filterNode },
                        SqlParserPos.ZERO);
                sqlSelect.setWhere(newWhere);
            }
        }

        return sqlSelect;
    }

    /**
     * Clone a {@link SqlNode} tree, but for each node where a mapping to a new node
     * already exists in
     * the given {@code map}, replace it with the new node. The same {@code map}
     * will be further
     * augmented with mappings from original to cloned nodes. This is a tricky
     * method to write, because
     * {@method SqlNode.clone()} only does shallow copy, so we need to manually
     * invoke deep copying on
     * them. Adding to the complication is that many fields of {@link SqlNode} are
     * not externally
     * visible, or are {@code final}.
     *
     * @param sqlNode
     *            The root of the subtree to be cloned.
     * @param map
     *            This map specifies nodes to be replaced, and will be further
     *            populated with mappings
     *            from old to cloned {@link SqlNode} nodes.
     * @return Cloned subtree.
     * @throws AnalyzerException
     */
    public SqlNode cloneSpecial(SqlNode sqlNode, SqlNode parent, Map<SqlNode, SqlNode> map, DContext curCtx,
            boolean withIID, boolean withPushdown) throws AnalyzerException {
        if (sqlNode == null) {
            return null;
        } else if (map.containsKey(sqlNode)) {
            return map.get(sqlNode);
        } else if (sqlNode instanceof SqlBasicCall) {
            DContext newCtx = (DContext) this.xNodeToDContext.get(this.sqlToXNode.get(sqlNode));
            newCtx = newCtx == null ? curCtx : newCtx;
            SqlBasicCall sqlBasicCall = (SqlBasicCall) sqlNode;
            // otherwise, nothing special
            SqlNode[] operands = (sqlBasicCall.operands == null) ? null : sqlBasicCall.operands.clone();
            if (operands != null) {
                for (int i = 0; i < operands.length; i++) {
                    operands[i] = this.cloneSpecial(operands[i], sqlNode, map, newCtx, withIID, withPushdown);
                }
            }
            SqlBasicCall newNode = new SqlBasicCall(sqlBasicCall.getOperator(), operands,
                    sqlBasicCall.getParserPosition(),
                    sqlBasicCall.isExpanded(),
                    (SqlLiteral) (this.cloneSpecial(sqlBasicCall.getFunctionQuantifier(), sqlNode, map,
                            newCtx, withIID, withPushdown)));
            map.put(sqlNode, newNode);
            if (sqlBasicCall.getOperator() instanceof SqlSetOperator && (withIID || withPushdown)) {
                // wrap the top-level set operation in a SELECT statement 
                SqlParserPos p = newNode.getParserPosition();

                // AS alias: required for derived tables
                XTableValuedNode setTable = (XSetOpNode) this.sqlToXNode.get(sqlNode);
                String newCtxId = newCtx.id;
                List<String> iidColumnNames = IntStream.range(0, setTable.getRecordType().getFieldCount()).mapToObj(i -> newCtxId + "_iid_" + i)
                        .collect(Collectors.toList());
                SqlIdentifier alias = new SqlIdentifier(newCtxId + String.format("_tmp(%s)", String.join(", ", iidColumnNames)), p); // use the context id as the alias to avoid conflict with other tables
                SqlBasicCall as = (SqlBasicCall) SqlStdOperatorTable.AS.createCall(p, newNode, alias);

                // depending if parent is AS with a renamed column list, we need to have different select list
                SqlNode star = SqlIdentifier.star(p);
                SqlNodeList selectList = new SqlNodeList(Collections.singletonList(star), p);

                for (int i = 0; i < iidColumnNames.size(); i++) {
                    String iidColumnName = iidColumnNames.get(i);
                    selectList.add(new SqlIdentifier(iidColumnName + " AS " + setTable.getCommaSeparatedColumnNames().get(i), SqlParserPos.ZERO));
                }
                selectList.add(new SqlIdentifier(
                        String.format("ROW_NUMBER() OVER (PARTITION BY %s ORDER BY %s) AS %s", String.join(", ", iidColumnNames),
                                String.join(", ", iidColumnNames),
                                newCtxId + "_iid_" + setTable.getRecordType().getFieldCount()),
                        SqlParserPos.ZERO));

                SqlNode wrappedSetOp = null;
                if (withPushdown) {
                    SqlNode inner = new SqlSelect(
                            p,
                            SqlNodeList.EMPTY, // keywords (e.g., DISTINCT) – none here
                            selectList, // *
                            as, // FROM (<set>) AS t
                            null, null, null, // WHERE, GROUP BY, HAVING
                            null, null, null, // WINDOW, ORDER BY, OFFSET
                            null, // FETCH
                            null);
                    // SqlParserPos newPos = inner.getParserPosition();
                    SqlIdentifier newAlias = new SqlIdentifier(newCtxId + "_tp", p); // use the context id as the alias to avoid conflict with other tables
                    SqlBasicCall newFrom = (SqlBasicCall) SqlStdOperatorTable.AS.createCall(p, inner, newAlias);
                    String whereFilterMark = String.format("!(%s,iid)s", newCtx.id);
                    SqlNode whereFilterNode = new SqlIdentifier(whereFilterMark, SqlParserPos.ZERO);
                    wrappedSetOp = new SqlSelect(
                            p,
                            SqlNodeList.EMPTY, // keywords (e.g., DISTINCT) – none here
                            new SqlNodeList(Collections.singletonList(star), p), // *
                            newFrom, // FROM (<set>) AS t
                            whereFilterNode, null, null, // WHERE, GROUP BY, HAVING
                            null, null, null, // WINDOW, ORDER BY, OFFSET
                            null, // FETCH
                            null);
                } else {
                    wrappedSetOp = new SqlSelect(
                            p,
                            SqlNodeList.EMPTY, // keywords (e.g., DISTINCT) – none here
                            selectList, // *
                            as, // FROM (<set>) AS t
                            null, null, null, // WHERE, GROUP BY, HAVING
                            null, null, null, // WINDOW, ORDER BY, OFFSET
                            null, // FETCH
                            null);
                }

                if ((parent instanceof SqlBasicCall && ((SqlBasicCall) parent).getOperator() instanceof SqlSetOperator)) {
                    // if the parent is also a SetOp, we need to wrap it again to only expose the original columns
                    SqlIdentifier wrappedAlias = new SqlIdentifier(newCtx.id + "_wrapped_t", SqlParserPos.ZERO); // use the context id as the alias to avoid conflict with other tables
                    SqlBasicCall wrappedAs = (SqlBasicCall) SqlStdOperatorTable.AS.createCall(SqlParserPos.ZERO, wrappedSetOp, wrappedAlias);
                    SqlNodeList wrappedSelectList = new SqlNodeList(SqlParserPos.ZERO);
                    for (int i = 0; i < iidColumnNames.size(); i++) {
                        wrappedSelectList
                                .add(new SqlIdentifier(iidColumnNames.get(i) + " AS " + setTable.getCommaSeparatedColumnNames().get(i), SqlParserPos.ZERO));
                    }
                    return new SqlSelect(SqlParserPos.ZERO,
                            SqlNodeList.EMPTY,
                            wrappedSelectList,
                            wrappedAs,
                            null, null, null,
                            null, null, null, null, null);
                }
                return wrappedSetOp;
            } else if (sqlBasicCall.getOperator() == SqlStdOperatorTable.AS && sqlBasicCall.operandCount() == 3) {
                // if the parent is AS with a renamed column list, we need to add extra IID and seq columns to the renamed list
                DContext nextCtx = (DContext) this.xNodeToDContext.get(this.sqlToXNode.get(sqlBasicCall.operand(0)));
                IID iid = nextCtx.xnode.getIID();
                for (int i = 0; i < iid.size(); i++) {
                    ((SqlNodeList) sqlBasicCall.operand(2)).add(new SqlIdentifier(iid.ctxId + "_iid_" + i, SqlParserPos.ZERO));
                }
                ((SqlNodeList) sqlBasicCall.operand(2)).add(new SqlIdentifier(nextCtx.id + "_seq", SqlParserPos.ZERO));

            }
            return newNode;

        } else if (sqlNode instanceof SqlNodeList) {
            SqlNodeList newNode = (SqlNodeList) (SqlNode.clone(sqlNode));
            for (int i = 0; i < newNode.size(); i++) {
                newNode.set(i,
                        this.cloneSpecial(newNode.get(i), sqlNode, map, curCtx, withIID, withPushdown));
            }
            map.put(sqlNode, newNode);
            return newNode;
        } else if (sqlNode instanceof SqlSelect) {
            SqlSelect newNode = (SqlSelect) (SqlNode.clone(sqlNode));
            DSelectContext newCtx = (DSelectContext) this.xNodeToDContext.get(this.sqlToXNode.get(sqlNode));
            newNode.setSelectList(
                    (SqlNodeList) (this.cloneSpecial(newNode.getSelectList(), sqlNode, map, newCtx, withIID,
                            withPushdown)));
            newNode.setFrom(
                    this.cloneSpecial(newNode.getFrom(), sqlNode, map, newCtx, withIID, withPushdown));
            newNode.setWhere(
                    this.cloneSpecial(newNode.getWhere(), sqlNode, map, newCtx, withIID, withPushdown));
            newNode.setGroupBy((SqlNodeList) (this.cloneSpecial(newNode.getGroup(), sqlNode, map, newCtx,
                    withIID, withPushdown)));
            newNode.setHaving(
                    this.cloneSpecial(newNode.getHaving(), sqlNode, map, newCtx, withIID, withPushdown));
            newNode.setOrderBy(
                    (SqlNodeList) (this.cloneSpecial(newNode.getOrderList(), sqlNode, map, newCtx, withIID,
                            withPushdown)));
            map.put(sqlNode, newNode);

            if (withIID) {
                // situations where we can insert IID:
                // 1. parent is not a set operation
                // 2. we are generating milestone SQL (withPushdown is false)
                if (((parent instanceof SqlBasicCall) && !(((SqlBasicCall) parent).getOperator() instanceof SqlSetOperator)) ||
                        (!(parent instanceof SqlBasicCall) && !withPushdown)) {
                    if ((!((XSelectNode) newCtx.xnode).hasCorrelatedSub || newCtx == curCtx)) {
                        // insert IID only when it is not correlated subquery
                        // or when the correlated subquery is the root ctx
                        newNode = insertSelectIID(newNode, newCtx);
                    }
                }
            }

            if (withPushdown && (!((XSelectNode) newCtx.xnode).hasCorrelatedSub || newCtx == curCtx)) {
                // insert pushdown only when it is not correlated subquery
                // or when the correlated subquery is the root ctx
                newNode = insertSelectPushdown(newNode, newCtx);
            }
            return newNode;
        } else if (sqlNode instanceof SqlJoin) {
            SqlJoin newNode = (SqlJoin) (SqlNode.clone(sqlNode));
            newNode.setLeft(
                    this.cloneSpecial(newNode.getLeft(), sqlNode, map, curCtx, withIID, withPushdown));
            newNode.setRight(
                    this.cloneSpecial(newNode.getRight(), sqlNode, map, curCtx, withIID, withPushdown));
            newNode.setOperand(5,
                    this.cloneSpecial(newNode.getCondition(), sqlNode, map, curCtx, withIID, withPushdown));
            map.put(sqlNode, newNode);
            return newNode;
        } else {
            SqlNode newNode = SqlNode.clone(sqlNode);
            for (Field f : newNode.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) {
                    continue;
                }
                try {
                    if (f.get(newNode) instanceof SqlNode) {
                        f.set(newNode, this.cloneSpecial((SqlNode) (f.get(newNode)), sqlNode, map, curCtx,
                                withIID, withPushdown));
                    } else if (f.get(newNode) instanceof SqlNode[] && f.get(newNode) != null) {
                        assert f.get(newNode) != f.get(sqlNode); // Calcite should know better not
                                                                 // to just copy the array pointer!
                        SqlNode[] sqlNodes = (SqlNode[]) (f.get(newNode));
                        // Modify in place!
                        for (int i = 0; i < sqlNodes.length; i++) {
                            sqlNodes[i] = this.cloneSpecial(sqlNodes[i], sqlNode, map, curCtx, withIID,
                                    withPushdown);
                        }
                    }
                }
                catch (IllegalAccessException e) {
                    throw new AnalyzerException("unexpected internal error");
                }
            }
            map.put(sqlNode, newNode);
            return newNode;
        }
    }
}

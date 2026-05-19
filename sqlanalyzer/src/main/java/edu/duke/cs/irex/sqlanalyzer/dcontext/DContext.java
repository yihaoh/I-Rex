package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
// import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.NestedArrayList;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;
import edu.duke.cs.irex.sqlanalyzer.xnode.XBasicCallNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XCellValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XColumnRefNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XColumnRenameNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XJoinNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSetOpNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableRefNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithItemNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithNode;

public abstract class DContext {
    public final QueryContext qc;
    public final String id;
    public final XTableValuedNode xnode;
    public final List<XWithNode> xWithAncestors;
    public final DContext parent;
    public final DContext refWithContext; // null if not a copy of a WITH context
    public final List<DContext> children;
    public final List<DContextMilestone> milestones;
    public DContextCode code;

    public DContext(QueryContext qc, XTableValuedNode xnode, List<DContext> dContextAncestors, List<XWithNode> xWithAncestors)
            throws AnalyzerException {
        this.qc = qc;
        // adding extra "a" to avoid leading digit that causes SQL parsing issue
        // as id is used for aliasing iid columns
        this.id = "a" + Integer.toHexString(System.identityHashCode(this));
        this.xnode = xnode;
        if (!(xnode instanceof XJoinNode)) {
            // let's not register DJoinContext for now
            this.qc.xNodeToDContext.put(xnode, this);
        }
        this.xWithAncestors = List.copyOf(xWithAncestors);
        this.code = null;
        if (dContextAncestors.size() > 0) {
            this.parent = dContextAncestors.get(dContextAncestors.size() - 1);
            this.parent.children.add(this);
        } else {
            this.parent = null;
        }
        refWithContext = null;
        this.children = new ArrayList<DContext>();
        this.milestones = new ArrayList<>();
        return;
    }

    public DContext(DContext refContext, DContext parent) throws AnalyzerException {
        this.qc = refContext.qc;
        this.id = "a" + Integer.toHexString(System.identityHashCode(this));
        this.xnode = refContext.xnode;
        this.xWithAncestors = List.copyOf(refContext.xWithAncestors);
        this.code = null;
        this.parent = parent;
        this.refWithContext = refContext;
        this.parent.children.add(this);
        this.children = new ArrayList<DContext>();
        for (int i = 0; i < refContext.children.size(); i++) {
            if (refContext instanceof DSelectContext) {
                this.children.add(new DSelectContext(refContext.children.get(i), this));
            } else {
                this.children.add(new DSetOpContext(refContext.children.get(i), this));
            }
        }
        this.milestones = refContext.milestones;
        return;
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        if (this.parent != null) {
            json.addProperty("parent_id", this.parent.id);
        } else {
            json.add("parent_id", null);
        }
        if (this.refWithContext != null) {
            json.addProperty("with_reference", this.refWithContext.id);
        } else {
            json.add("with_reference", null);
        }
        json.addProperty("type", this.getClass().getSimpleName());
        json.addProperty("XTableValuedNode_id", this.xnode.id);
        json.add("XWithNode_ancestor_ids", XNode.toJsonArrayOfXNodeIds(this.xWithAncestors));
        json.add("code", this.code.toJsonObject());
        JsonArray jsonArr = new JsonArray();
        for (int i = 0; i < this.children.size(); i++) {
            if (this.children.get(i) == null) {
                continue;
            }
            jsonArr.add(this.children.get(i).toJsonObject());
        }
        json.add("children", jsonArr);
        return json;
    }

    public abstract static class DContextCode {
        public JsonObject toJsonObject() {
            JsonObject json = (JsonObject) (Analyzer.toJsonTreeByDefault(this));
            // JsonObject json =
            // this instanceof DSelectContextCode ? ((DSelectContextCode)
            // this).toJsonObject()
            // : (JsonObject) (Analyzer.toJsonTreeByDefault(this));
            json.addProperty("type", this.getClass().getSimpleName());
            return json;
        }

        public static DContextCode fromJson(String json) {
            return Analyzer.fromJsonByDefault(json, DContextCode.class);
        }
    }

    public abstract DContextCode prepareCode() throws AnalyzerException;

    public abstract static class DContextExecd {
        @SerializedName(value = "contents")
        public final List<NestedArrayList> contents;

        public DContextExecd() {
            this.contents = new ArrayList<>();
        }

        public static NestedArrayList iidJsonStringToList(JsonObject obj, int startIndex) throws AnalyzerException {
            // JsonObject obj = Analyzer.fromJsonByDefault(iidString,
            // JsonElement.class).getAsJsonObject();
            NestedArrayList iid = new NestedArrayList();
            for (int i = startIndex; i <= obj.size(); i++) {
                if (obj.get("f" + i).isJsonObject()) {
                    JsonObject tpObj = obj.get("f" + i).getAsJsonObject();
                    if (tpObj.has("value")) {
                        iid.add(obj.get("f" + i).getAsJsonObject().get("value").getAsString());
                    } else {
                        iid.add(iidJsonStringToList(tpObj, 1));
                    }
                } else if (obj.get("f" + i).isJsonPrimitive()) {
                    JsonPrimitive val = obj.get("f" + i).getAsJsonPrimitive();
                    if (val.isNumber()) {
                        iid.add(val.getAsBigDecimal());
                    } else if (val.isBoolean()) {
                        iid.add(val.getAsBoolean());
                    } else {
                        iid.add(val.getAsString());
                    }
                } else if (obj.get("f" + i).isJsonNull()) {
                    iid.add(null);
                } else {
                    throw new AnalyzerException(String.format("IID contains unsupported type. Value: %s.", obj.get("f" + i).toString()));
                }
            }
            return iid;
        }
    }

    public static Map<String, DContextMilestone> collectNecessaryCtxFilters(XNode tree, Map<XNode, DContext> xNodeToDContext)
            throws AnalyzerException {
        Map<String, DContextMilestone> filters = new HashMap<>();
        collectNecessaryCtxFiltersHelper(filters, tree, xNodeToDContext);
        return filters;
    }

    public static void collectNecessaryCtxFiltersHelper(Map<String, DContextMilestone> collection,
            XNode tree, Map<XNode, DContext> xNodeToDContext) throws AnalyzerException {
        if (tree == null) {
            return;
        } else if (tree instanceof XSelectNode) {
            DSelectContext curCtx = (DSelectContext) xNodeToDContext.get(tree);
            collection.put(xNodeToDContext.get(tree).id, curCtx.milestones.get(curCtx.tOutput));
            collectNecessaryCtxFiltersHelper(collection, ((XSelectNode) tree).fromExpr, xNodeToDContext);
        } else if (tree instanceof XSetOpNode) {
            // TODO: finish when SetOp ready
            return;
        } else if (tree instanceof XTableRefNode && !((XTableRefNode) tree).isInDatabase) {
            DContext withCtx = xNodeToDContext.get(tree);
            DContext origWithCtx = withCtx.refWithContext == null ? withCtx : withCtx.refWithContext;
            if (collection.containsKey(origWithCtx.id)) {
                return;
            } else {
                collectNecessaryCtxFiltersHelper(collection, origWithCtx.xnode, xNodeToDContext);
            }
        } else {
            for (XNode x : tree.children) {
                collectNecessaryCtxFiltersHelper(collection, x, xNodeToDContext);
            }
        }
    }

    public static List<XColumnRefNode> collectCorrelatedColumnRefsBelongTo(XNode tree,
            XNode curCtxRoot, XNode refCtxRoot) throws AnalyzerException {
        // see if tree is an external reference under curCtxRoot, and it belongs to
        // refCtxRoot
        List<XColumnRefNode> refs = new ArrayList<>();
        collectCorrelatedColumnRefsBelongToHelper(refs, tree, curCtxRoot, refCtxRoot);
        return refs;
    }

    private static void collectCorrelatedColumnRefsBelongToHelper(List<XColumnRefNode> collecion,
            XNode tree, XNode curCtxRoot, XNode refCtxRoot) throws AnalyzerException {
        if (tree == null) {
            return;
        }
        if (tree instanceof XColumnRefNode) {
            XColumnRefNode columnRef = (XColumnRefNode) tree;
            if ((XNode) columnRef.selectNode == refCtxRoot && curCtxRoot != refCtxRoot) {
                // find external references in a subquery
                collecion.add(columnRef);
            }
        } else if (tree instanceof XBasicCallNode && curCtxRoot == refCtxRoot
                && ((XBasicCallNode) tree).sqlOperator == SqlStdOperatorTable.IN
                && tree.children.get(1) instanceof XSelectNode) {
            // special case, collect subquery like (x,y,..) IN (SELECT ...)
            // do we need this?
            Queue<XNode> q = new LinkedList<>();
            q.add(tree.children.get(0));
            while (!q.isEmpty()) {
                XNode cur = q.poll();
                if (cur instanceof XColumnRefNode
                        && (XNode) ((XColumnRefNode) cur).selectNode == refCtxRoot) {
                    collecion.add((XColumnRefNode) cur);
                }
                for (XNode x : cur.children) {
                    q.add(x);
                }
            }
        } else {
            XNode newCtxRoot = (tree instanceof XSelectNode || tree instanceof XSetOpNode) ? tree : curCtxRoot;
            for (XNode c : tree.children) {
                collectCorrelatedColumnRefsBelongToHelper(collecion, c, newCtxRoot, refCtxRoot);
            }
        }
        return;
    }

    public static List<XColumnRefNode> collectColumnRefsExternalTo(XNode tree, XNode n)
            throws AnalyzerException {
        List<XColumnRefNode> refs = new ArrayList<>();
        collectColumnRefsExternalToHelper(refs, tree, n);
        return refs;
    }

    private static void collectColumnRefsExternalToHelper(List<XColumnRefNode> collecion,
            XNode tree, XNode n) throws AnalyzerException {
        if (tree == null) {
            return;
        }
        if (tree instanceof XColumnRefNode) {
            XColumnRefNode columnRef = (XColumnRefNode) tree;
            if (!columnRef.isInternalTo(n)) {
                collecion.add(columnRef);
            }
        } else {
            for (XNode c : tree.children) {
                collectColumnRefsExternalToHelper(collecion, c, n);
            }
        }
        return;
    }

    public ParameterizedSQL wrapWith(ParameterizedSQL inner, boolean withIID, boolean withPushdown)
            throws AnalyzerException {
        ParameterizedSQL result = inner;
        for (XWithNode w : Lists.reverse(this.xWithAncestors)) {
            // This additional no-op step below is needed because postgresql does not like
            // consecutive WITH:
            result = new ParameterizedSQL("SELECT * FROM (\n").concat(result)
                    .concat("\n) AS T ORDER BY 1");
            result = this.wrapWithHelper(result, w, withIID, withPushdown);
        }
        return result;
    }

    public ParameterizedSQL wrapWith(ParameterizedSQL inner, boolean withIID, boolean withPushdown, int offset, List<Boolean> dirs)
            throws AnalyzerException {
        ParameterizedSQL result = inner;
        String order = String.join(", ",
                IntStream.range(offset, offset + dirs.size())
                        .mapToObj(i -> i + (dirs.get(i - offset) ? "" : " desc"))
                        .collect(Collectors.toList()));
        for (XWithNode w : Lists.reverse(this.xWithAncestors)) {
            // This additional no-op step below is needed because postgresql does not like
            // consecutive WITH:
            result = new ParameterizedSQL("SELECT * FROM (\n").concat(result)
                    .concat("\n) AS T ORDER BY " + order);
            result = this.wrapWithHelper(result, w, withIID, withPushdown);
        }
        return result;
    }

    private ParameterizedSQL wrapWithHelper(ParameterizedSQL inner, XWithNode w, boolean withIID, boolean withPushdown)
            throws AnalyzerException {
        List<ParameterizedSQL> withItemsSQL = new ArrayList<>();
        for (XWithItemNode i : w.withItems) {
            ParameterizedSQL iSql = withIID ? new ParameterizedSQL(
                    i.tableName + "(" + i.getCommaSeparatedColumnNamesString() + ", "
                            + i.getIID().toWithSchemaAlias() + ") AS ")
                    : new ParameterizedSQL(
                            i.tableName + "(" + i.getCommaSeparatedColumnNamesString() + ") AS ");
            iSql = iSql.concat(this.toParameterizedSQL(i.query, i.query, withIID, withPushdown).parenthesized());
            withItemsSQL.add(iSql);
        }
        return (new ParameterizedSQL("WITH ")).concat(ParameterizedSQL.join(",\n", withItemsSQL))
                .concat("\n").concat(inner);
    }

    public static List<XCellValuedNode> enumSubs(XNode tree, Set<String> excludeSubtrees) {
        List<XCellValuedNode> exprs = new ArrayList<>();
        enumSubsHelper(tree, excludeSubtrees, exprs);
        return exprs;
    }

    private static void enumSubsHelper(XNode x, Set<String> excludeSubtrees, List<XCellValuedNode> exprs) {
        if (excludeSubtrees.contains(x.id)) {
            return;
        } else if (x instanceof XColumnRefNode) {
            exprs.add((XColumnRefNode) x);
            return;
        } else if (x instanceof XBasicCallNode) {
            XBasicCallNode call = (XBasicCallNode) x;
            exprs.add(call);
            if (!call.isAggregate) {
                for (XNode c : x.children) {
                    enumSubsHelper(c, excludeSubtrees, exprs);
                }
            }
            return;
        } else if (x instanceof XColumnRenameNode) {
            enumSubsHelper(x.children.get(0), excludeSubtrees, exprs);
            return;
        } else {
            return;
        }
    }

    public static List<XCellValuedNode> enumSubsBelowAggrs(XNode tree, Set<String> excludeSubtrees) {
        List<XCellValuedNode> exprs = new ArrayList<>();
        enumSubsBelowAggrsHelper(tree, excludeSubtrees, exprs);
        return exprs;
    }

    private static void enumSubsBelowAggrsHelper(XNode x, Set<String> excludeSubtrees, List<XCellValuedNode> exprs) {
        // Overall, we are just exploring the tree to find aggregates, and then call
        // enumerateRelevantSubexpressionsNotBelowAggregatesHelper to process their
        // subtrees as
        // normal.
        if (excludeSubtrees.contains(x.id)) {
            return;
        }
        if (x instanceof XBasicCallNode) {
            if (((XBasicCallNode) x).isAggregate) {
                enumSubsHelper(x.children.get(0), excludeSubtrees, exprs);
            } else {
                for (XNode c : x.children) {
                    enumSubsBelowAggrsHelper(c, excludeSubtrees, exprs);
                }
            }
            return;
        } else if (x instanceof XColumnRenameNode) {
            enumSubsBelowAggrsHelper(x.children.get(0), excludeSubtrees, exprs);
            return;
        } else {
            return;
        }
    }

    public Map<XNode, ParameterizedSQL> genParameterizedSQLForSubs(XNode tree, XTableValuedNode scopeNode)
            throws AnalyzerException {
        return this.genParameterizedSQLForSubs(tree, new HashSet<>(), false, scopeNode);
    }

    public Map<XNode, ParameterizedSQL> genParameterizedSQLForSubs(XNode tree)
            throws AnalyzerException {
        return this.genParameterizedSQLForSubs(tree, new HashSet<>(), false, this.xnode);
    }

    public Map<XNode, ParameterizedSQL> genParameterizedSQLForSubs(XNode tree,
            Set<String> excludeSubtrees) throws AnalyzerException {
        return this.genParameterizedSQLForSubs(tree, excludeSubtrees, false, this.xnode);
    }

    public Map<XNode, ParameterizedSQL> genParameterizedSQLForSubsBelowAggrs(XNode tree)
            throws AnalyzerException {
        return this.genParameterizedSQLForSubs(tree, new HashSet<>(), true, this.xnode);
    }

    public Map<XNode, ParameterizedSQL> genParameterizedSQLForSubs(XNode tree,
            Set<String> excludeSubtrees, boolean belowAggreagtes, XTableValuedNode scopeNode) throws AnalyzerException {
        Map<SqlNode, SqlNode> map = new HashMap<>();
        for (XColumnRefNode ref : collectColumnRefsExternalTo(tree, scopeNode)) {
            map.put(ref.sqlNode, new QueryContext.SqlColumnRef(ref, scopeNode));
        }
        this.qc.cloneSpecial(tree.sqlNode, null, map, this, false, false);
        Map<XNode, ParameterizedSQL> nodeToSQLMap = new HashMap<>();
        for (XNode x : (belowAggreagtes ? enumSubsBelowAggrs(tree, excludeSubtrees)
                : enumSubs(tree, excludeSubtrees))) {
            SqlNode newNode = map.get(x.sqlNode);
            SqlWriter writer = this.qc.analyzer.createSqlWriter();
            newNode.unparse(writer, 0, 0);
            nodeToSQLMap.put(x, new ParameterizedSQL(writer.toString(),
                    collectColumnRefsExternalTo(x, scopeNode), new HashMap<>(), new ArrayList<>(), new ArrayList<>()));
        }
        return nodeToSQLMap;
    }

    public ParameterizedSQL toParameterizedSQL(XNode tree) throws AnalyzerException {
        return this.toParameterizedSQL(tree, this.xnode, false, false);
    }

    public ParameterizedSQL toParameterizedSQL(XNode tree, boolean withIID, boolean withPushdown) throws AnalyzerException {
        return this.toParameterizedSQL(tree, this.xnode, withIID, withPushdown);
    }

    public ParameterizedSQL toParameterizedSQL(XNode tree, XTableValuedNode context, boolean withIID, boolean withPushdown)
            throws AnalyzerException {
        List<XColumnRefNode> refs = collectColumnRefsExternalTo(tree, context);
        Map<SqlNode, SqlNode> map = new HashMap<>();
        for (XColumnRefNode ref : refs) {
            map.put(ref.sqlNode, new QueryContext.SqlColumnRef(ref, context));
        }
        SqlNode sqlTree = this.qc.cloneSpecial(tree.sqlNode, null, map, this, withIID, withPushdown);
        SqlWriter writer = this.qc.analyzer.createSqlWriter();
        sqlTree.unparse(writer, 0, 0);
        if (withPushdown) {
            Map<String, DContextMilestone> msts = DContext.collectNecessaryCtxFilters(tree, this.qc.xNodeToDContext);
            return new ParameterizedSQL(writer.toString(), refs, msts, new ArrayList<>(), new ArrayList<>());
        }
        return new ParameterizedSQL(writer.toString(), refs, new HashMap<>(), new ArrayList<>(), new ArrayList<>());
    }

    public void collectCtxFilters(Map<String, ParameterizedSQL.SerializedFilter> filters, DContext ctx) {
        // recursively collect filters from all derived input tables
        this.collectCtxFiltersHelper(filters, ctx, false);
    }

    private void collectCtxFiltersHelper(Map<String, ParameterizedSQL.SerializedFilter> filters, DContext ctx, boolean inCorrelatedSubquery) {
        if (ctx.children == null) {
            return;
        }

        // we do not collect filters in correlated subqueries unless it is the root context
        inCorrelatedSubquery = (ctx.xnode instanceof XSelectNode && ((XSelectNode) ctx.xnode).hasCorrelatedSub && ctx != this);
        if (ctx.refWithContext != null) {
            this.collectCtxFiltersHelper(filters, ctx.refWithContext, inCorrelatedSubquery);
        } else {
            if (inCorrelatedSubquery) {
                // in the correlated subquery, in case this filter has been collected in the past, remove it
                filters.remove(ctx.id);
            } else {
                filters.put(ctx.id, new ParameterizedSQL.SerializedFilter(ctx.milestones.get(ctx.milestones.size() - 1)));
            }
            for (DContext c : ctx.children) {
                this.collectCtxFiltersHelper(filters, c, inCorrelatedSubquery);
            }
        }
        return;
    }

}

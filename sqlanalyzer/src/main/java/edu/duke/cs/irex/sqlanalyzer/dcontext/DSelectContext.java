package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.calcite.sql.JoinType;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.SqlWriter.Frame;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL.SerializedRow;
import edu.duke.cs.irex.sqlanalyzer.xnode.XBasicCallNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XCellValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XJoinNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableRefNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableRenameNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.IID;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.Sargable;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithNode;

public class DSelectContext extends DContext {
    // public final List<DContextMilestone> milestones;
    public final int tJoinFilter;
    public final int tExpandGroup;
    public final int tGroup;
    public final int tPreDistinct;
    public final int tOutput;
    public final DJoinContext joinCtx;

    public DSelectContext(QueryContext qc, XSelectNode xSelect, List<DContext> dContextAncestors, List<XWithNode> xWithAncestors)
            throws AnalyzerException {
        super(qc, xSelect, dContextAncestors, xWithAncestors);
        this.tJoinFilter = xSelect.inputTables.size();
        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            this.tExpandGroup = this.tJoinFilter + 1;
            this.tGroup = this.tExpandGroup + 1;
        } else {
            this.tExpandGroup = -1;
            this.tGroup = -1;
        }

        if (xSelect.isDistinct) {
            this.tPreDistinct = this.tGroup == -1 ? this.tJoinFilter + 1 : this.tGroup + 1;
            this.tOutput = this.tPreDistinct + 1;
        } else {
            this.tPreDistinct = -1;
            this.tOutput = this.tGroup == -1 ? this.tJoinFilter + 1 : this.tGroup + 1;
        }
        List<DContext> tmpCtxAncestors = new ArrayList<>();
        this.joinCtx = this.contextualizeJoins(xSelect.fromExpr, xSelect, tmpCtxAncestors);
    }

    public DSelectContext(DContext refContext, DContext parent) throws AnalyzerException {
        super(refContext, parent);
        this.tJoinFilter = ((DSelectContext) refContext).tJoinFilter;
        this.tExpandGroup = ((DSelectContext) refContext).tExpandGroup;
        this.tGroup = ((DSelectContext) refContext).tGroup;
        this.tPreDistinct = ((DSelectContext) refContext).tPreDistinct;
        this.tOutput = ((DSelectContext) refContext).tOutput;
        this.joinCtx = ((DSelectContext) refContext).joinCtx;
        return;
    }

    @Override
    public DContextCode prepareCode() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        // generate input milestones
        for (int i = 0; i < xSelect.inputTables.size(); i++) {
            XTableValuedNode xTable = xSelect.inputTables.get(i);
            this.milestones.add(new DContextMilestone(this.id, i, xTable.getIID()));
        }

        // generate JoinFilter milestone
        this.milestones.add(new DContextMilestone(this.id, this.tJoinFilter, xSelect.fromExpr.getIID(), xSelect.fromExpr.getSargable()));

        // generate group milestone
        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            this.milestones.add(new DContextMilestone(this.id, this.tExpandGroup, this.genExpandGroupIID(), xSelect.fromExpr.getSargable()));
            this.milestones.add(new DContextMilestone(this.id, this.tGroup, this.genGroupIID(), xSelect.fromExpr.getSargable()));
        }

        if (xSelect.isDistinct) {
            this.milestones.add(new DContextMilestone(this.id, this.tPreDistinct, this.genPreDistinctIID(), this.genPostGroupBySargable()));
        }

        // generate output milestone
        if (xSelect.parent instanceof XBasicCallNode && ((XBasicCallNode) xSelect.parent).isScalarSubquery) {
            this.milestones.add(new DContextMilestone(this.id, this.tOutput, xSelect.getIID()));
        } else {
            this.milestones.add(new DContextMilestone(this.id, this.tOutput, xSelect.getIID(), this.genPostGroupBySargable()));
        }
        return new DSelectContextCode(this);
    }

    @Override
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

        if (this.joinCtx != null) {
            json.add("join_context_tree", this.joinCtx.toJsonObject());
        } else {
            json.add("join_context_tree", null);
        }
        return json;
    }

    /***************************************************************
     * Generate context tree to handle fancy joins
     ***************************************************************/

    private void collectTopLevelNonCommaJoin(XTableValuedNode xnode, List<XTableValuedNode> topLevelNonCommaJoins) {
        if (xnode instanceof XJoinNode && ((XJoinNode) xnode).joinType != JoinType.COMMA) {
            topLevelNonCommaJoins.add(xnode);
            return;
        } else if (!(xnode instanceof XJoinNode)) {
            topLevelNonCommaJoins.add(xnode);
            return;
        }
        collectTopLevelNonCommaJoin(((XJoinNode) xnode).left, topLevelNonCommaJoins);
        collectTopLevelNonCommaJoin(((XJoinNode) xnode).right, topLevelNonCommaJoins);
        return;
    }

    public DJoinContext contextualizeJoins(XTableValuedNode xnode, XSelectNode xSelect, List<DContext> ctxAncestors) throws AnalyzerException {
        if (!(xnode instanceof XJoinNode)) {
            return null;
        }

        XJoinNode xJoin = (XJoinNode) xnode;
        if (xJoin.joinType == JoinType.COMMA) {
            List<XTableValuedNode> topLevelNonCommaJoins = new ArrayList<>();
            collectTopLevelNonCommaJoin(xJoin, topLevelNonCommaJoins);
            DJoinContext root = new DJoinContext(qc, xSelect, ctxAncestors, this.xWithAncestors, xJoin, this, topLevelNonCommaJoins);
            ctxAncestors.add(root);
            for (XTableValuedNode x : topLevelNonCommaJoins) {
                if (x instanceof XJoinNode) {
                    this.contextualizeJoins(x, xSelect, ctxAncestors);
                }
            }
            ctxAncestors.remove(ctxAncestors.size() - 1);
            return root;
        }

        DJoinContext root = new DJoinContext(qc, xSelect, ctxAncestors, this.xWithAncestors, xJoin, this,
                new ArrayList<>(List.of(xJoin.left, xJoin.right)));
        ctxAncestors.add(root);
        this.contextualizeJoins(xJoin.left, xSelect, ctxAncestors);
        this.contextualizeJoins(xJoin.right, xSelect, ctxAncestors);
        ctxAncestors.remove(ctxAncestors.size() - 1);
        return root;
    }

    /***************************************************************
     * Generate special table IIDs
     ***************************************************************/

    public IID genExpandGroupIID() throws AnalyzerException {
        XSelectNode xSelect = ((XSelectNode) this.xnode);
        IID joinFilterIID = xSelect.fromExpr.getIID();
        return this.genGroupIID().combineIID(joinFilterIID, 0, null);
    }

    public IID genGroupIID() throws AnalyzerException {
        XSelectNode xSelect = ((XSelectNode) this.xnode);
        List<String> groupCols = xSelect.groupByExprs.stream().map(x -> x.toString()).collect(Collectors.toList());
        List<String> groupTypes = xSelect.groupByExprs.stream().map(x -> x.relDataType.getSqlTypeName().getName()).collect(Collectors.toList());
        List<String> groupXNodes = xSelect.groupByExprs.stream().map(x -> x.id).collect(Collectors.toList());
        List<Boolean> groupDirs = new ArrayList<>(Collections.nCopies(groupCols.size(), false));
        IID groupIID = new IID(groupCols, groupTypes, groupDirs, groupXNodes, null);
        return groupIID;
    }

    public IID genPreDistinctIID() throws AnalyzerException {
        XSelectNode xSelect = ((XSelectNode) this.xnode);
        if (xSelect.isAggregate && xSelect.groupByExprs.size() > 0) {
            // group by exist, the iid is concatenation of group iid and output iid
            return this.genGroupIID().combineIID(xSelect.getIID(), 0, null);
        }
        // no group by, the iid is concatenation of join filter iid and output iid
        return xSelect.fromExpr.getIID().combineIID(xSelect.getIID(), 0, null);
    }

    public Sargable genPostGroupBySargable() throws AnalyzerException {
        XSelectNode xSelect = ((XSelectNode) this.xnode);
        if (xSelect.havingExpr == null) {
            return xSelect.fromExpr.getSargable();
        }
        // regular sargable might exclude necessary tuples to reproduce a group
        // so we have to go with Group By exprs as the pushdown
        List<String> cols = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<Integer> tableIndex = new ArrayList<>();
        List<Integer> tableColIndex = new ArrayList<>();
        for (int i = 0; i < xSelect.groupByExprs.size(); i++) {
            XCellValuedNode x = xSelect.groupByExprs.get(i);
            cols.add(x.toString());
            types.add(x.relDataType.getSqlTypeName().getName());
            tableIndex.add(this.tGroup);
            tableColIndex.add(i);
        }
        return new Sargable(cols, types, tableIndex, tableColIndex);
    }

    /***************************************************************
     * Auxiliary functions for wrapping milestone/page queries
     ***************************************************************/

    private ParameterizedSQL wrapInputMilestoneSQL(ParameterizedSQL sql) throws AnalyzerException {
        ParameterizedSQL wrapSql = new ParameterizedSQL("WITH tmp (seq, iid) AS (\n    ").concat(sql).concat("\n)\n");
        ParameterizedSQL outer = new ParameterizedSQL(String.format(
                "SELECT MIN(seq) seq, COUNT(*) count, ROW_TO_JSON(MIN_IID(iid)) iid FROM tmp GROUP BY seq / %d ORDER BY seq / %d",
                this.qc.pageSize, this.qc.pageSize));
        return wrapSql.concat(outer);
    }

    private ParameterizedSQL wrapDownstreamMilestoneSQL(ParameterizedSQL sql, int idx)
            throws AnalyzerException {
        DContextMilestone mst = this.milestones.get(idx);
        ParameterizedSQL wrapSql = new ParameterizedSQL(String.format("WITH tmp (seq, %s) AS (\n    ", String.join(", ", mst.getCTEAlias())))
                .concat(sql).concat("\n)\n");
        List<String> outerSelectList = new ArrayList<>();
        outerSelectList.add("MIN(seq) seq");
        outerSelectList.add("COUNT(*) count");
        outerSelectList.add("ROW_TO_JSON(MIN_IID(iid)) iid");
        if (mst.sarg != null) {
            for (int i = 0; i < mst.sarg.size(); i++) {
                String alias = "sarg_" + i;
                if (mst.sarg.inputColIndex.get(i) < 0) {
                    String iidSargAlias = mst.sarg.inputColIndex.get(i) == -1 ? alias + "_iid" : alias + "_iid_mix";
                    if (this.tExpandGroup > 0 && idx > this.tExpandGroup) {
                        outerSelectList.add(
                                String.format("ARRAY[ROW_TO_JSON(MIN_IID(%s[1])), ROW_TO_JSON(MAX_IID(%s[2]))] AS %s", alias, alias, iidSargAlias));
                    } else {
                        outerSelectList
                                .add(String.format("ARRAY[ROW_TO_JSON(MIN_IID(%s)), ROW_TO_JSON(MAX_IID(%s))] AS %s", alias, alias, iidSargAlias));
                    }
                } else {
                    if ((this.tExpandGroup > 0 && idx > this.tExpandGroup) || (idx == this.tOutput && this.tPreDistinct != -1)) {
                        outerSelectList.add(String.format("ARRAY[MIN(%s[1]), MAX(%s[2])] AS %s", alias, alias, alias));
                    } else {
                        outerSelectList.add(String.format("ARRAY[MIN(%s), MAX(%s)] AS %s", alias, alias, alias));
                    }
                }
            }
        }

        // process blmfl
        if (mst.blm != null && !(idx == this.tOutput && this.tGroup != -1)) {
            // note that we don't collect blmfl for output when there is GroupBy
            outerSelectList.add(mst.blm.toBloomExpr());
        }
        ParameterizedSQL outer = new ParameterizedSQL(String.format(
                "SELECT %s \nFROM tmp\nGROUP BY seq / %d ORDER BY seq / %d",
                String.join(", ", outerSelectList),
                this.qc.pageSize, this.qc.pageSize));
        return wrapSql.concat(outer);
    }

    protected ParameterizedSQL wrapDistinctOutputMilestoneSQL(ParameterizedSQL sql) throws AnalyzerException {
        // wrap the DISTINCT part using a GROUP BY and get it ready for wrapDownstreamMilestoneSQL
        DContextMilestone mst = this.milestones.get(this.tOutput);
        List<String> iidAliasCols = IntStream.range(0, mst.iid.size()).mapToObj(i -> "iid_" + i).collect(Collectors.toList());
        List<String> iidAliasColsWithOrder = IntStream.range(0, mst.iid.size())
                .mapToObj(i -> mst.iid.dirs.get(i) ? iidAliasCols.get(i) + " desc" : iidAliasCols.get(i)).collect(Collectors.toList());
        List<String> sargBlmCols = mst.getCTEAlias().subList(1, mst.getCTEAlias().size());
        ParameterizedSQL wrapSql = new ParameterizedSQL(
                String.format("WITH tmp_distinct (%s, %s) AS (\n    ", String.join(", ", iidAliasCols), String.join(",", sargBlmCols))).concat(sql)
                        .concat("\n)\n");

        List<String> outerSelectList = new ArrayList<>();
        outerSelectList.add(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", String.join(", ", iidAliasColsWithOrder)));
        outerSelectList.add(String.format("ROW(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", String.join(", ", iidAliasColsWithOrder))
                + String.join(", ", iidAliasCols) + ")"));
        for (int i = 0; i < sargBlmCols.size(); i++) {
            // String alias = "sarg_" + i;
            if (mst.sarg.inputColIndex.get(i) < 0) {
                // String iidSargAlias = mst.sarg.inputColIndex.get(i) == -1 ? alias + "_iid" :
                // alias + "_iid_mix";
                outerSelectList.add(
                        String.format("ARRAY[ROW_TO_JSON(MIN_IID(%s[1])), ROW_TO_JSON(MAX_IID(%s[2]))]", sargBlmCols.get(i), sargBlmCols.get(i)));
            } else {
                if (this.tGroup != -1) {
                    outerSelectList.add(String.format("ARRAY[MIN(%s[1]), MAX(%s[2])]", sargBlmCols.get(i), sargBlmCols.get(i)));
                } else {
                    outerSelectList.add(String.format("ARRAY[MIN(%s), MAX(%s)]", sargBlmCols.get(i), sargBlmCols.get(i)));
                }
            }
        }

        ParameterizedSQL outer = new ParameterizedSQL(String.format(
                "SELECT %s \nFROM tmp_distinct\nGROUP BY %s",
                String.join(", ", outerSelectList),
                String.join(", ", iidAliasCols)));
        return wrapSql.concat(outer);
    }

    public ParameterizedSQL genFromHelper(XTableValuedNode fromXNode, boolean withIID, boolean withPushdown)
            throws AnalyzerException {
        Map<SqlNode, SqlNode> map = new HashMap<>();
        SqlNode newFromSqlNode = this.qc.cloneSpecial(fromXNode.sqlNode, null, map, this, withIID, withPushdown);
        SqlWriter writer = this.qc.analyzer.createSqlWriter();
        Frame frame = writer.startList(SqlWriter.FrameTypeEnum.FROM_LIST);
        newFromSqlNode.unparse(writer, 0, 0);
        writer.endList(frame);
        return new ParameterizedSQL("FROM " + writer.toString());
    }

    private ParameterizedSQL insertPinSubspace(ParameterizedSQL sql) {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        for (int i = 0; i < xSelect.inputTables.size(); i++) {
            sql.pins.add(new ParameterizedSQL.SerializedPin(this.milestones.get(i)));
        }
        if (this.tGroup != -1) {
            sql.pins.add(new ParameterizedSQL.SerializedPin(this.milestones.get(this.tGroup)));
        }
        return sql;
    }

    /*************************************************************
     * Schema generation functions for tables in the context
     *************************************************************/

    // Note: any change in the schema must be reflected in the genXXXPageSQL and genPinSubspaceXXXPageSQL to be
    // consistent.

    public Pair<RelRecordType, List<TableContent.Usage>> genJoinFilterRecordType() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        if (xSelect.fromExpr == null) {
            return null;
        }
        // RelDataTypeFactory typeFactory = new
        // SqlTypeFactoryImpl(RelDataTypeSystemImpl.DEFAULT);
        RelRecordType joinFilterRecord = xSelect.fromExpr.getRecordTypeWithIID();
        List<RelDataTypeField> updatedFieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        int offset = 0;

        // collect columns
        for (int i = 0; i < joinFilterRecord.getFieldCount(); i++) {
            updatedFieldList.add(new RelDataTypeFieldImpl(
                    joinFilterRecord.getFieldList().get(i).getName(), offset++,
                    joinFilterRecord.getFieldList().get(i).getType()));
            if (i == joinFilterRecord.getFieldCount() - 1) {
                usages.add(TableContent.Usage.IID);
            } else {
                usages.add(TableContent.Usage.DISPLAY);
            }
        }

        // collect all IIDs
        if (this.tGroup != -1) {
            // if group by, then collect next IID as ExpandGroup_IID and Group_IID
            updatedFieldList.add(new RelDataTypeFieldImpl("ExpandGroup_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
            updatedFieldList.add(new RelDataTypeFieldImpl("Group_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
            usages.add(TableContent.Usage.NEXT_IID);
            usages.add(TableContent.Usage.NEXT_IID);
        } else {
            // if no group by, then collect next IID as PreDistinct_IID or Output_IID
            if (this.tPreDistinct != -1) {
                updatedFieldList.add(new RelDataTypeFieldImpl("PreDistinct_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
                usages.add(TableContent.Usage.NEXT_IID);
            } else {
                updatedFieldList.add(new RelDataTypeFieldImpl("Output_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
                usages.add(TableContent.Usage.NEXT_IID);
            }
        }

        // now adding all column hooks, first all WHERE subexpressions
        if (xSelect.whereExpr != null) {
            Map<XNode, ParameterizedSQL> map = genParameterizedSQLForSubs(xSelect.whereExpr);
            for (XNode x : map.keySet()) {
                XCellValuedNode xx = (XCellValuedNode) x;
                updatedFieldList.add(new RelDataTypeFieldImpl(xx.id, offset++, xx.relDataType));
                usages.add(TableContent.Usage.EVAL);
            }
        }
        RelRecordType updatedRecordType = new RelRecordType(updatedFieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genExpandGroupRecordType() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        if (xSelect.groupByExprs == null) {
            return null;
        }
        List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();

        // collect the IID columns first
        fieldList.add(new RelDataTypeFieldImpl("IID", 0, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        fieldList.add(new RelDataTypeFieldImpl("PREV_IID", 1, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        fieldList.add(new RelDataTypeFieldImpl("NEXT_IID", 1, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        usages.add(TableContent.Usage.IID);
        usages.add(TableContent.Usage.PREV_IID);
        usages.add(TableContent.Usage.NEXT_IID);

        // collect all subexpressions under aggregate functions in HAVING
        int offset = fieldList.size();
        if (xSelect.havingExpr != null) {
            Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubsBelowAggrs(xSelect.havingExpr);
            for (XNode x : map.keySet()) {
                XCellValuedNode xx = (XCellValuedNode) x;
                fieldList.add(new RelDataTypeFieldImpl(xx.id, offset++, xx.relDataType));
                usages.add(TableContent.Usage.EVAL);
            }
        }
        RelRecordType updatedRecordType = new RelRecordType(fieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genGroupRecordType() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        if (xSelect.groupByExprs == null) {
            return null;
        }
        List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        int offset = 0;

        // collect GROUP BY exprs
        for (XCellValuedNode e : xSelect.groupByExprs) {
            fieldList.add(new RelDataTypeFieldImpl(e.toString(), offset++, e.relDataType));
            usages.add(TableContent.Usage.DISPLAY);
        }

        // collect group table IID and output IID
        fieldList.add(new RelDataTypeFieldImpl("IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        fieldList.add(new RelDataTypeFieldImpl("PREV_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        fieldList.add(new RelDataTypeFieldImpl("NEXT_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        usages.add(TableContent.Usage.IID);
        usages.add(TableContent.Usage.PREV_IID);
        usages.add(TableContent.Usage.NEXT_IID);

        // collect Group By subexpressions
        for (XCellValuedNode e : xSelect.groupByExprs) {
            Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
            for (XNode x : map.keySet()) {
                XCellValuedNode xx = (XCellValuedNode) x;
                fieldList.add(new RelDataTypeFieldImpl(xx.id, offset++, xx.relDataType));
                usages.add(TableContent.Usage.EVAL);
            }
        }

        // collect all subexpressions in HAVING above aggregate functions
        List<XCellValuedNode> havingSubExprsAboveAggr = new ArrayList<>();
        if (xSelect.havingExpr != null) {
            Set<String> exprsBelowAggr = enumSubsBelowAggrs(xSelect.havingExpr, new HashSet<>()).stream().map(x -> x.id).collect(Collectors.toSet());
            havingSubExprsAboveAggr.addAll(enumSubs(xSelect.havingExpr, exprsBelowAggr));
        }
        for (XCellValuedNode x : havingSubExprsAboveAggr) {
            fieldList.add(new RelDataTypeFieldImpl(x.id, offset++, x.relDataType));
            usages.add(TableContent.Usage.EVAL);
        }

        // collect all subexpressions in SELECT above aggregate functions
        List<XCellValuedNode> selectSubExprsAboveAggr = new ArrayList<>();
        for (XCellValuedNode x : xSelect.selectExprs) {
            Set<String> exprsBelowAggr = enumSubsBelowAggrs(x, new HashSet<>()).stream().map(y -> y.id).collect(Collectors.toSet());
            selectSubExprsAboveAggr.addAll(enumSubs(x, exprsBelowAggr));
        }
        for (XCellValuedNode x : selectSubExprsAboveAggr) {
            fieldList.add(new RelDataTypeFieldImpl(x.id, offset++, x.relDataType));
            usages.add(TableContent.Usage.EVAL);
        }

        RelRecordType updatedRecordType = new RelRecordType(fieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genPreDistinctRecordType() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        // RelDataTypeFactory typeFactory = new
        // SqlTypeFactoryImpl(RelDataTypeSystemImpl.DEFAULT);
        int offset = 0;

        // collect content then IID
        RelRecordType tp = xSelect.getRecordTypeWithIID();
        for (int i = 0; i < tp.getFieldCount(); i++) {
            if (i == tp.getFieldCount() - 1) {
                fieldList.add(new RelDataTypeFieldImpl("IID", offset++, tp.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.IID);
            } else {
                fieldList.add(new RelDataTypeFieldImpl(tp.getFieldNames().get(i), offset++, tp.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.DISPLAY);
            }
        }

        // collect previous IID for backward tracing
        fieldList.add(new RelDataTypeFieldImpl("PREV_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        fieldList.add(new RelDataTypeFieldImpl("NEXT_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        usages.add(TableContent.Usage.PREV_IID);
        usages.add(TableContent.Usage.NEXT_IID);

        // collect select sub-expressions
        for (XCellValuedNode e : xSelect.selectExprs) {
            Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
            for (XNode x : map.keySet()) {
                fieldList.add(new RelDataTypeFieldImpl(x.id, offset++, ((XCellValuedNode) x).relDataType));
                usages.add(TableContent.Usage.EVAL);
            }
        }
        RelRecordType updatedRecordType = new RelRecordType(fieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genOutputRecordType() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        // RelDataTypeFactory typeFactory = new
        // SqlTypeFactoryImpl(RelDataTypeSystemImpl.DEFAULT);
        int offset = 0;

        // collect content then IID
        RelRecordType tp = xSelect.getRecordTypeWithIID();
        for (int i = 0; i < tp.getFieldCount(); i++) {
            if (i == tp.getFieldCount() - 1) {
                fieldList.add(new RelDataTypeFieldImpl("IID", offset++, tp.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.IID);
            } else {
                fieldList.add(new RelDataTypeFieldImpl(tp.getFieldNames().get(i), offset++, tp.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.DISPLAY);
            }
        }

        // collect only previous IID for backward tracing
        fieldList.add(new RelDataTypeFieldImpl("PREV_IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        usages.add(TableContent.Usage.PREV_IID);

        // collect select sub-expressions only when not DISTINCT
        if (!xSelect.isDistinct) {
            for (XCellValuedNode e : xSelect.selectExprs) {
                Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
                for (XNode x : map.keySet()) {
                    fieldList.add(new RelDataTypeFieldImpl(x.id, offset++, ((XCellValuedNode) x).relDataType));
                    usages.add(TableContent.Usage.EVAL);
                }
            }
        }
        RelRecordType updatedRecordType = new RelRecordType(fieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    /***********************************************************
     * Milestone queries generation functions
     ***********************************************************/

    public ParameterizedSQL genInputMilestoneSQL(int i) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        XTableValuedNode xTable = xSelect.inputTables.get(i).withoutRename();
        ParameterizedSQL sql = null;
        if (xTable instanceof XTableRefNode && ((XTableRefNode) xTable).isInDatabase) {
            // base table, simple formulation
            String tableName = ((XTableRefNode) xTable).name;
            String alias = xTable.parent instanceof XTableRenameNode ? ((XTableRenameNode) xTable.parent).name : ((XTableRefNode) xTable).name;
            IID tp = this.milestones.get(i).iid;
            sql = new ParameterizedSQL(String.format(
                    "SELECT ROW_NUMBER() OVER (ORDER BY %s) - 1, (ROW_NUMBER() OVER (ORDER BY %s) - 1, %s) FROM %s %s",
                    tp.toOrderByString(), tp.toOrderByString(), tp.toSelectString(), tableName, alias));
        } else if (xTable instanceof XTableRefNode && !((XTableRefNode) xTable).isInDatabase) {
            // must be WITH table
            XTableValuedNode tp = ((XTableRefNode) xTable).withItem.query;
            return tp instanceof XSelectNode
                    ? ((DSelectContext) this.qc.xNodeToDContext.get(tp)).genOutputMilestoneSQL(false)
                    : null;
        } else {
            // WITH or inline derived table
            return xTable instanceof XSelectNode
                    ? ((DSelectContext) this.qc.xNodeToDContext.get(xTable)).genOutputMilestoneSQL(false)
                    : ((DSetOpContext) this.qc.xNodeToDContext.get(xTable)).genOutputMilestoneSQL();
        }
        return this.wrapInputMilestoneSQL(sql);
    }

    public ParameterizedSQL genJoinFilterMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);

        // process SELECT: seq, iid, index columns
        List<ParameterizedSQL> selects = new ArrayList<>();
        IID iid = this.milestones.get(this.tJoinFilter).iid;
        // first the seq number
        selects.add(new ParameterizedSQL(
                String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString())));
        // then the iid with seq number for the purpose of sorting mix order
        selects.add(new ParameterizedSQL(
                "(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", iid.toOrderByString()) + iid.toSelectString() + ")"));
        if (this.milestones.get(this.tJoinFilter).sarg != null) {
            selects.addAll(this.milestones.get(this.tJoinFilter).sarg.cols.stream()
                    .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        }
        if (this.milestones.get(this.tJoinFilter).blm != null) {
            selects.addAll(this.milestones.get(this.tJoinFilter).blm.cols.stream()
                    .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        }
        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));

        // process FROM: for each derived table, add IID in the SELECT
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE: keep it as it is, we do not need IID in the correlated subqueries
        if (xSelect.whereExpr != null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,pin)s", this.id));
        } else if (xSelect.whereExpr == null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(String.format("!(%s,pin)s", this.id));
        } else if (xSelect.whereExpr != null && !pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr));
        }
        if (pinSubspace) {
            sql = this.insertPinSubspace(sql);
        }
        sql = this.wrapWith(sql, true, false);
        sql = this.wrapDownstreamMilestoneSQL(sql, this.tJoinFilter);
        return sql;
    }

    public ParameterizedSQL genExpandGroupMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);

        // process SELECT: seq, iid, index columns
        IID expandGroupIID = this.milestones.get(tExpandGroup).iid;
        List<ParameterizedSQL> selects = new ArrayList<>();
        selects.add(new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", expandGroupIID.toOrderByString())));
        selects.add(new ParameterizedSQL("ROW(").concat(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", expandGroupIID.toOrderByString()))
                .concat(expandGroupIID.toSelectString()).concat(")"));
        if (this.milestones.get(this.tExpandGroup).sarg != null) {
            selects.addAll(this.milestones.get(this.tExpandGroup).sarg.cols.stream()
                    .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        }
        if (this.milestones.get(this.tExpandGroup).blm != null) {
            selects.addAll(this.milestones.get(this.tExpandGroup).blm.cols.stream()
                    .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        }
        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));

        // process FROM: for each derived table, add IID in the SELECT
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE: keep it as it is, we do not need IID in the correlated
        // subqueries
        if (xSelect.whereExpr != null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,pin)s", this.id));
        } else if (xSelect.whereExpr == null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(String.format("!(%s,pin)s", this.id));
        } else if (xSelect.whereExpr != null && !pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr));
        }
        if (pinSubspace) {
            sql = this.insertPinSubspace(sql);
        }
        sql = this.wrapWith(sql, true, false);
        sql = this.wrapDownstreamMilestoneSQL(sql, tExpandGroup);
        return sql;
    }

    public ParameterizedSQL genGroupMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);

        // process SELECT: seq, iid, index columns
        IID groupIID = this.milestones.get(tGroup).iid;
        List<ParameterizedSQL> selects = new ArrayList<>();
        selects.add(new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", groupIID.toOrderByString())));
        selects.add(new ParameterizedSQL("ROW(").concat(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", groupIID.toOrderByString()))
                .concat(groupIID.toSelectString()).concat(")"));
        if (this.milestones.get(this.tGroup).sarg != null) {
            Sargable groupSarg = this.milestones.get(this.tGroup).sarg;
            // selects.addAll(this.milestones.get(this.tExpandGroup).sarg.cols.stream()
            // .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
            selects.addAll(IntStream.range(0, groupSarg.size())
                    .mapToObj(i -> groupSarg.types.get(i) == "ANY"
                            ? new ParameterizedSQL(String.format("ARRAY[MIN_IID(%s), MAX_IID(%s)]", groupSarg.cols.get(i), groupSarg.cols.get(i)))
                            : new ParameterizedSQL(String.format("ARRAY[MIN(%s), MAX(%s)]", groupSarg.cols.get(i), groupSarg.cols.get(i))))
                    .collect(Collectors.toList()));
        }
        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));

        // process FROM: for each derived table, add IID in the SELECT
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE: keep it as it is, we do not need IID in the correlated subqueries
        if (xSelect.whereExpr != null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,pin)s", this.id));
        } else if (xSelect.whereExpr == null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(String.format("!(%s,pin)s", this.id));
        } else if (xSelect.whereExpr != null && !pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr));
        }
        if (pinSubspace) {
            sql = this.insertPinSubspace(sql);
        }

        // process GROUP BY: keep it as it is
        sql = sql.concat("\nGROUP BY ").concat(String.join(", ", groupIID.cols));

        sql = this.wrapWith(sql, true, false);
        sql = this.wrapDownstreamMilestoneSQL(sql, tGroup);
        return sql;
    }

    public ParameterizedSQL genPreDistinctMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);

        // process SELECT
        List<ParameterizedSQL> selects = new ArrayList<>();
        IID iid = this.milestones.get(this.tPreDistinct).iid;
        selects.add(new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString())));
        selects.add(new ParameterizedSQL(
                "ROW(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", iid.toOrderByString())
                        + iid.toSelectString() + ")"));

        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            if (this.milestones.get(this.tPreDistinct).sarg != null) {
                Sargable sarg = this.milestones.get(this.tPreDistinct).sarg;
                for (int i = 0; i < sarg.cols.size(); i++) {
                    String col = sarg.cols.get(i);
                    if (sarg.inputIndex.get(i) < 0) {
                        selects.add(new ParameterizedSQL(String.format("ARRAY[MIN_IID(%s), MAX_IID(%s)]", col, col)));
                    } else { // regular range pushdown predicate
                        selects.add(new ParameterizedSQL(String.format("ARRAY[MIN(%s), MAX(%s)]", col, col)));
                    }
                }
            }
        } else {
            if (this.milestones.get(this.tPreDistinct).sarg != null) {
                selects.addAll(this.milestones.get(this.tPreDistinct).sarg.cols.stream()
                        .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
            }
            if (this.milestones.get(this.tPreDistinct).blm != null) {
                selects.addAll(this.milestones.get(this.tPreDistinct).blm.cols.stream()
                        .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
            }
        }

        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));

        // process FROM: for each derived table, add IID in the SELECT
        sql = sql.concat(new ParameterizedSQL("\n"))
                .concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE: keep it as it is, we do not need IID in the correlated subqueries
        if (xSelect.whereExpr != null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,pin)s", this.id));
        } else if (xSelect.whereExpr == null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(String.format("!(%s,pin)s", this.id));
        } else if (xSelect.whereExpr != null && !pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr));
        }
        if (pinSubspace) {
            sql = this.insertPinSubspace(sql);
        }

        // process GROUP BY: simply reconstruct
        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            List<ParameterizedSQL> groups = new ArrayList<>();
            for (XCellValuedNode e : xSelect.groupByExprs) {
                groups.add(this.toParameterizedSQL(e));
            }
            sql = sql.concat("\nGROUP BY ").concat(ParameterizedSQL.join(", ", groups));
        }

        if (xSelect.havingExpr != null) {
            sql = sql.concat("\nHAVING ").concat(this.toParameterizedSQL(xSelect.havingExpr));
        }
        sql = this.wrapWith(sql, true, false);
        sql = this.wrapDownstreamMilestoneSQL(sql, this.tPreDistinct);
        return sql;
    }

    public ParameterizedSQL genOutputMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);

        // process SELECT
        List<ParameterizedSQL> selects = new ArrayList<>();
        IID iid = this.milestones.get(this.tOutput).iid;
        if (this.tPreDistinct == -1) {
            // only when not distinct, otherwise we have to delay the formation of iid
            selects.add(new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString())));
            selects.add(new ParameterizedSQL(
                    "ROW(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", iid.toOrderByString()) + iid.toSelectString() + ")"));
        } else {
            // if distinct, just propagate all iid columns
            selects.add(new ParameterizedSQL(iid.toSelectString()));
        }

        if (this.tGroup != -1) {
            if (this.milestones.get(this.tOutput).sarg != null) {
                Sargable sarg = this.milestones.get(this.tOutput).sarg;
                for (int i = 0; i < sarg.cols.size(); i++) {
                    String col = sarg.cols.get(i);
                    if (sarg.inputIndex.get(i) < 0) {
                        selects.add(new ParameterizedSQL(String.format("ARRAY[MIN_IID(%s), MAX_IID(%s)]", col, col)));
                    } else { // regular range pushdown predicate
                        selects.add(new ParameterizedSQL(String.format("ARRAY[MIN(%s), MAX(%s)]", col, col)));
                    }
                }
            }
        } else {
            if (this.milestones.get(this.tOutput).sarg != null) {
                selects.addAll(this.milestones.get(this.tOutput).sarg.cols.stream()
                        .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
            }
            if (this.milestones.get(this.tOutput).blm != null) {
                selects.addAll(this.milestones.get(this.tOutput).blm.cols.stream()
                        .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
            }
        }

        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));

        // process FROM: for each derived table, add IID in the SELECT
        sql = sql.concat(new ParameterizedSQL("\n"))
                .concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE: keep it as it is, we do not need IID in the correlated
        // subqueries
        if (xSelect.whereExpr != null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,pin)s", this.id));
        } else if (xSelect.whereExpr == null && pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(String.format("!(%s,pin)s", this.id));
        } else if (xSelect.whereExpr != null && !pinSubspace) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr));
        }
        if (pinSubspace) {
            sql = this.insertPinSubspace(sql);
        }

        // process GROUP BY: simply reconstruct
        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            List<ParameterizedSQL> groups = new ArrayList<>();
            for (XCellValuedNode e : xSelect.groupByExprs) {
                groups.add(this.toParameterizedSQL(e));
            }
            sql = sql.concat("\nGROUP BY ").concat(ParameterizedSQL.join(", ", groups));
        }

        if (xSelect.havingExpr != null) {
            sql = sql.concat("\nHAVING ").concat(this.toParameterizedSQL(xSelect.havingExpr));
        }

        sql = this.wrapWith(sql, true, false);
        if (xSelect.isDistinct) {
            sql = this.wrapDistinctOutputMilestoneSQL(sql);
        }
        sql = this.wrapDownstreamMilestoneSQL(sql, this.tOutput);
        return sql;
    }

    /***********************************************************
     * Page queries generation functions
     ***********************************************************/

    public ParameterizedSQL genInputPageSQL(int i) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        XTableValuedNode xTable = xSelect.inputTables.get(i).withoutRename();
        if (xTable instanceof XTableRefNode && ((XTableRefNode) xTable).isInDatabase) {
            // base table, simple formulation
            String tableName = xTable.parent instanceof XTableRenameNode
                    ? ((XTableRefNode) xTable).name + " " + ((XTableRenameNode) xTable.parent).name
                    : ((XTableRefNode) xTable).name;
            IID curIID = this.milestones.get(i).iid;
            ParameterizedSQL sql = new ParameterizedSQL(
                    String.format(
                            "SELECT *, ROW_TO_JSON(ROW(%s)) iid FROM %s WHERE !(%s,all)s ORDER BY %s",
                            curIID.toSelectString(), tableName, this.id,
                            curIID.toOrderByString()));
            sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(i)));
            return sql;
        } else if (xTable instanceof XTableRefNode && !((XTableRefNode) xTable).isInDatabase) {
            xTable = ((XTableRefNode) xTable).withItem.query;
        }
        // WITH table or inline derived table
        DContext tp = this.qc.xNodeToDContext.get(xTable);
        return tp instanceof DSelectContext ? ((DSelectContext) tp).genOutputPageSQL(false).left : ((DSetOpContext) tp).genOutputPageSQL();
    }

    public Pair<ParameterizedSQL, List<Set<String>>> genJoinFilterPageSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        IID joinFilterIID = xSelect.fromExpr.getIID();
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<Set<String>> columnHooks = new ArrayList<>();

        // collect contents (exclude iid columns)
        RelRecordType record = xSelect.fromExpr.getRecordTypeFullyQualifed();
        for (int i = 0; i < record.getFieldCount(); i++) {
            // selects.add(new ParameterizedSQL(tables.get(i) + "." + attrs.get(i)));
            selects.add(new ParameterizedSQL(record.getFieldNames().get(i)));
            columnHooks.add(new HashSet<String>());
        }

        // collect iid
        selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) iid", joinFilterIID.toSelectString())));
        columnHooks.add(new HashSet<String>());

        // collect NEXT IID
        if (xSelect.groupByExprs != null && xSelect.groupByExprs.size() > 0) {
            // collect ExpandGroup iid
            selects.add(new ParameterizedSQL(
                    String.format("ROW_TO_JSON(ROW(%s)) group_expand_iid", this.milestones.get(this.tExpandGroup).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());

            // collect Group iid
            selects.add(new ParameterizedSQL(
                    String.format("ROW_TO_JSON(ROW(%s)) group_iid", this.milestones.get(this.tGroup).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());
        } else {
            // collect preDistinct iid or output iid
            if (this.tPreDistinct != -1) {
                selects.add(new ParameterizedSQL(
                        String.format("ROW_TO_JSON(ROW(%s)) preDistinct_iid", this.milestones.get(this.tPreDistinct).iid.toSelectString())));
                columnHooks.add(new HashSet<String>());
            } else {
                selects.add(new ParameterizedSQL(
                        String.format("ROW_TO_JSON(ROW(%s)) output_iid", this.milestones.get(this.tOutput).iid.toSelectString())));
                columnHooks.add(new HashSet<String>());
            }
        }

        // collect WHERE subexpressions
        ParameterizedSQL where = null;
        if (xSelect.whereExpr != null) {
            Map<XNode, ParameterizedSQL> map = genParameterizedSQLForSubs(xSelect.whereExpr);
            for (XNode x : map.keySet()) {
                if (x == xSelect.whereExpr) {
                    where = map.get(x);
                }
                selects.add(map.get(x));
                columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
            }
        }

        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE ").concat(where).concat(String.format(" AND !(%s,all)s", this.id));
        } else {
            sql = sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
        }
        if (pinSubspace) {
            sql = sql.concat(String.format(" AND !(%s,pin)s", this.id));
            sql = this.insertPinSubspace(sql);
        }
        sql = sql.concat("\n" + "ORDER BY " + joinFilterIID.toOrderByString());
        sql = this.wrapWith(sql, true, true);
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tJoinFilter)));
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        return new Pair<>(sql, columnHooks);
    }

    public Pair<ParameterizedSQL, List<Set<String>>> genExpandGroupPageSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<Set<String>> columnHooks = new ArrayList<>();

        // collect IID
        IID groupIID = this.milestones.get(this.tExpandGroup).iid;
        selects.add(new ParameterizedSQL("ROW_TO_JSON(ROW(" + groupIID.toSelectString() + ")) iid"));
        columnHooks.add(new HashSet<>());

        // collect previous (join filter) iid
        selects.add(new ParameterizedSQL(
                String.format("ROW_TO_JSON(ROW(%s)) join_filter_iid", this.milestones.get(this.tJoinFilter).iid.toSelectString())));
        columnHooks.add(new HashSet<String>());

        // collect next (Group) iid
        selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) group_iid", this.genGroupIID().toSelectString())));
        columnHooks.add(new HashSet<String>());

        // collect various expressions
        if (xSelect.isAggregate) {
            // Collect HAVING subexpressions below aggregation functions:
            if (xSelect.havingExpr != null) {
                Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubsBelowAggrs(xSelect.havingExpr);
                for (XNode x : map.keySet()) {
                    selects.add(map.get(x));
                    columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
                }
            }
            // Collect SELECT subexpressions below aggregation functions (comment out for
            // now)
            // for (XCellValuedNode e : xSelect.selectExprs) {
            // Map<XNode, ParameterizedSQL> map =
            // this.genParameterizedSQLForSubsBelowAggrs(e);
            // for (XNode x : map.keySet()) {
            // selects.add(map.get(x));
            // columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
            // }
            // }
        }

        // now assemble all clauses
        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(" AND !(%s,all)s", this.id));
        } else {
            sql = sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
        }
        if (pinSubspace) {
            sql = sql.concat(String.format(" AND !(%s,pin)s", this.id));
            sql = this.insertPinSubspace(sql);
        }
        sql = sql.concat("\nORDER BY ").concat(groupIID.toOrderByString()).concat(", " + xSelect.fromExpr.getIID().toOrderByString());
        sql = this.wrapWith(sql, true, true);
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tExpandGroup)));
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        return new Pair<>(sql, columnHooks);
    }

    public Pair<ParameterizedSQL, List<Set<String>>> genGroupPageSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<Set<String>> columnHooks = new ArrayList<>();

        // collect Group By exprs, kinda duplicate of the IID but for the convenience of
        // the front end
        for (XCellValuedNode e : xSelect.groupByExprs) {
            selects.add(new ParameterizedSQL(e.toString()));
            columnHooks.add(new HashSet<>(Collections.singleton(e.id)));
        }

        // collect IID
        selects.add(new ParameterizedSQL("ROW_TO_JSON(ROW(" + this.milestones.get(this.tGroup).iid.toSelectString() + ")) iid"));
        columnHooks.add(new HashSet<>());

        // collect previous (expand group) IID
        selects.add(new ParameterizedSQL(
                String.format("ROW_TO_JSON(MIN_IID(ROW(%s))) expandGroup_iid", this.milestones.get(this.tExpandGroup).iid.toSelectString())));
        columnHooks.add(new HashSet<String>());

        // collect next (preDistinct or output) IID
        if (this.tPreDistinct != -1) {
            selects.add(
                    new ParameterizedSQL(
                            String.format("ROW_TO_JSON(ROW(%s)) preDistinct_iid", this.milestones.get(this.tPreDistinct).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());
        } else {
            selects.add(
                    new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) output_iid", this.milestones.get(this.tOutput).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());
        }

        // collect Group By subexpressions
        for (XCellValuedNode e : xSelect.groupByExprs) {
            Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
            for (XNode x : map.keySet()) {
                selects.add(map.get(x));
                columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
            }
        }

        // collect HAVING subexpressions above (include) aggregate functions
        List<XCellValuedNode> havingSubExprsAboveAggr = new ArrayList<>();
        if (xSelect.havingExpr != null) {
            Set<String> exprsBelowAggr = enumSubsBelowAggrs(xSelect.havingExpr, new HashSet<>()).stream().map(x -> x.id).collect(Collectors.toSet());
            havingSubExprsAboveAggr.addAll(enumSubs(xSelect.havingExpr, exprsBelowAggr));
        }
        for (XCellValuedNode e : havingSubExprsAboveAggr) {
            selects.add(new ParameterizedSQL(e.toString()));
            columnHooks.add(new HashSet<>(Collections.singleton(e.id)));
        }

        // collect SELECT subexpressions above (include) aggregate functions
        List<XCellValuedNode> selectSubExprsAboveAggr = new ArrayList<>();
        for (XCellValuedNode x : xSelect.selectExprs) {
            Set<String> exprsBelowAggr = enumSubsBelowAggrs(x, new HashSet<>()).stream().map(y -> y.id).collect(Collectors.toSet());
            selectSubExprsAboveAggr.addAll(enumSubs(x, exprsBelowAggr));
        }
        for (XCellValuedNode e : selectSubExprsAboveAggr) {
            selects.add(new ParameterizedSQL(e.toString()));
            columnHooks.add(new HashSet<>(Collections.singleton(e.id)));
        }

        // assemble clause by clause
        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE (").concat(this.toParameterizedSQL(xSelect.whereExpr)).concat(String.format(") AND !(%s,all)s", this.id));
        } else {
            sql = sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
        }
        if (pinSubspace) {
            sql = sql.concat(String.format(" AND !(%s,pin)s", this.id));
            sql = this.insertPinSubspace(sql);
        }
        List<ParameterizedSQL> groupBys = new ArrayList<>();
        for (XCellValuedNode g : xSelect.groupByExprs) {
            groupBys.add(this.toParameterizedSQL(g));
        }
        sql = sql.concat("\nGROUP BY ").concat(ParameterizedSQL.join(", ", groupBys));
        sql = sql.concat("\nORDER BY ").concat(ParameterizedSQL.join(", ", groupBys));
        sql = this.wrapWith(sql, true, true);
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tGroup)));
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        return new Pair<>(sql, columnHooks);
    }

    public Pair<ParameterizedSQL, List<Set<String>>> genPreDistinctPageSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<Set<String>> columnHooks = new ArrayList<>();

        for (XCellValuedNode e : xSelect.selectExprs) {
            selects.add(new ParameterizedSQL(e.toString()));
            columnHooks.add(new HashSet<String>());
            // columnHooks.add(new HashSet<>(Collections.singleton(e.id)));
        }

        // collect IID
        selects.add(new ParameterizedSQL("ROW_TO_JSON(ROW(" + this.milestones.get(this.tPreDistinct).iid.toSelectString() + ")) iid"));
        columnHooks.add(new HashSet<>());

        // collect previous (group or join filter) IID
        if (this.tGroup != -1) {
            selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) group_iid", this.milestones.get(this.tGroup).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());
        } else {
            selects.add(new ParameterizedSQL(
                    String.format("ROW_TO_JSON(ROW(%s)) join_filter_iid", this.milestones.get(this.tJoinFilter).iid.toSelectString())));
            columnHooks.add(new HashSet<String>());
        }

        // collect next (output) IID
        selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) output_iid", this.milestones.get(this.tOutput).iid.toSelectString())));
        columnHooks.add(new HashSet<String>());

        // collect SELECT subexpressions at and above aggregation functions
        for (XCellValuedNode e : xSelect.selectExprs) {
            Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
            for (XNode x : map.keySet()) {
                selects.add(map.get(x));
                columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
            }
        }

        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE (").concat(this.toParameterizedSQL(xSelect.whereExpr));
            sql = xSelect.isAggregate ? sql.concat(String.format(") AND !(%s,sarg)s", this.id))
                    : sql.concat(String.format(") AND !(%s,all)s", this.id));
            sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tPreDistinct)));
        } else {
            sql = xSelect.isAggregate ? sql.concat(String.format("\nWHERE !(%s,sarg)s", this.id))
                    : sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
            sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tPreDistinct)));
        }

        if (pinSubspace) {
            sql = sql.concat(String.format(" AND !(%s,pin)s", this.id));
            sql = this.insertPinSubspace(sql);
        }

        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        if (xSelect.isAggregate && xSelect.groupByExprs.size() > 0) {
            List<ParameterizedSQL> groupBys = new ArrayList<>();
            for (XCellValuedNode g : xSelect.groupByExprs) {
                groupBys.add(this.toParameterizedSQL(g));
            }
            sql = sql.concat("\nGROUP BY ").concat(ParameterizedSQL.join("\n       , ", groupBys));
        }

        if (xSelect.isAggregate || xSelect.havingExpr != null) {
            sql = xSelect.havingExpr != null
                    ? sql.concat(String.format("\nHAVING !(%s,iid)s AND ", this.id))
                            .concat(this.toParameterizedSQL(xSelect.havingExpr))
                    : sql.concat(String.format("\nHAVING !(%s,iid)s ", this.id));
        }
        sql = sql.concat("\nORDER BY " + this.milestones.get(this.tPreDistinct).iid.toOrderByString());
        sql = this.wrapWith(sql, true, true);
        return new Pair<>(sql, columnHooks);
    }

    private ParameterizedSQL wrapDistinctOutputPageSQL(ParameterizedSQL sql, List<String> iidAliasCols,
            List<String> iidAliasColsWithOrder,
            List<Boolean> orders) throws AnalyzerException {
        ParameterizedSQL wrapSql = new ParameterizedSQL(
                String.format("WITH tmp_distinct (%s, preDistinct_iid) AS (\n    ", String.join(", ", iidAliasCols))).concat(sql)
                        .concat("\n)\n");

        List<String> orderString = IntStream.range(0, orders.size())
                .mapToObj(i -> orders.get(i) ? iidAliasColsWithOrder.get(i) + " desc" : iidAliasColsWithOrder.get(i)).collect(Collectors.toList());
        wrapSql = wrapSql.concat(String.format(
                "SELECT %s, ROW_TO_JSON(ROW(%s)) iid, ROW_TO_JSON(MIN_IID(preDistinct_iid)) preDistinct_iid FROM tmp_distinct GROUP BY %s ORDER BY %s",
                String.join(", ", iidAliasCols), String.join(", ", iidAliasColsWithOrder),
                String.join(", ", iidAliasCols),
                String.join(", ", orderString)));
        return wrapSql;
    }

    public Pair<ParameterizedSQL, List<Set<String>>> genOutputPageSQL(boolean pinSubspace) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<Set<String>> columnHooks = new ArrayList<>();

        // collect SELECT expressions
        for (XCellValuedNode e : xSelect.selectExprs) {
            selects.add(new ParameterizedSQL(e.toString()));
            columnHooks.add(new HashSet<String>());
        }

        if (!xSelect.isDistinct) {
            // collect IID
            selects.add(new ParameterizedSQL("ROW_TO_JSON(ROW(" + this.milestones.get(this.tOutput).iid.toSelectString() + ")) iid"));
            columnHooks.add(new HashSet<>());

            // collect previous (group or join filter) IID
            if (this.tGroup != -1) {
                selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) group_iid", this.genGroupIID().toSelectString())));
                columnHooks.add(new HashSet<String>());
            } else {
                selects.add(new ParameterizedSQL(
                        String.format("ROW_TO_JSON(ROW(%s)) join_filter_iid", this.milestones.get(this.tJoinFilter).iid.toSelectString())));
                columnHooks.add(new HashSet<String>());
            }

            // collect SELECT subexpressions at and above aggregation functions
            for (XCellValuedNode e : xSelect.selectExprs) {
                Map<XNode, ParameterizedSQL> map = this.genParameterizedSQLForSubs(e);
                for (XNode x : map.keySet()) {
                    selects.add(map.get(x));
                    columnHooks.add(new HashSet<>(Collections.singleton(x.id)));
                }
            }
        } else {
            // only collect previous (preDistinct) IID
            selects.add(new ParameterizedSQL("ROW(" + this.milestones.get(this.tPreDistinct).iid.toSelectString() + ") preDistinct_iid"));
            columnHooks.add(new HashSet<>());
        }

        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE (").concat(this.toParameterizedSQL(xSelect.whereExpr));
            if (xSelect.isAggregate) {
                sql = this.milestones.get(this.tOutput).sarg != null
                        ? sql.concat(String.format(") AND !(%s,sarg)s", this.id))
                        : sql.concat(")");
            } else {
                sql = sql.concat(String.format(") AND !(%s,all)s", this.id));
            }
            sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tOutput)));
        } else {
            if (xSelect.isAggregate) {
                sql = this.milestones.get(this.tOutput).sarg != null ? sql.concat(String.format("\nWHERE !(%s,sarg)s", this.id)) : sql.concat(")");
            } else {
                sql = sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
            }
            sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tOutput)));
        }
        if (pinSubspace) {
            sql = sql.concat(String.format(" AND !(%s,pin)s", this.id));
            sql = this.insertPinSubspace(sql);
        }
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        if (xSelect.isAggregate && xSelect.groupByExprs.size() > 0) {
            List<ParameterizedSQL> groupBys = new ArrayList<>();
            for (XCellValuedNode g : xSelect.groupByExprs) {
                groupBys.add(this.toParameterizedSQL(g));
            }
            sql = sql.concat("\nGROUP BY ").concat(ParameterizedSQL.join("\n       , ", groupBys));
        }

        if (xSelect.isAggregate || xSelect.havingExpr != null) {
            sql = xSelect.havingExpr != null
                    ? sql.concat(String.format("\nHAVING !(%s,iid)s AND ", this.id))
                            .concat(this.toParameterizedSQL(xSelect.havingExpr))
                    : sql.concat(String.format("\nHAVING !(%s,iid)s ", this.id));
        }
        sql = sql.concat("\nORDER BY " + xSelect.getIID().toOrderByString());
        sql = this.wrapWith(sql, true, true);
        if (xSelect.isDistinct) {
            IID tpIID = xSelect.getIID();
            List<String> selectExprString = xSelect.selectExprs.stream().map(x -> x.withoutRename().toString()).collect(Collectors.toList());
            List<String> iidAliasCols = IntStream.range(0, xSelect.selectExprs.size()).mapToObj(i -> "col_" + i).collect(Collectors.toList());
            List<String> iidAliasColsWithOrder = IntStream.range(0, tpIID.size()).mapToObj(i -> "col_" + selectExprString.indexOf(tpIID.cols.get(i)))
                    .collect(Collectors.toList());
            List<Boolean> orders = IntStream.range(0, tpIID.size()).mapToObj(i -> tpIID.dirs.get(i)).collect(Collectors.toList());
            sql = this.wrapDistinctOutputPageSQL(sql, iidAliasCols, iidAliasColsWithOrder, orders);
        }
        return new Pair<>(sql, columnHooks);
    }

    /***********************************************************
     * ON/WHERE expression evaluation query
     ***********************************************************/

    Pair<ParameterizedSQL, List<String>> genOnWhereEvalSQL() throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<String> columnHooks = new ArrayList<>();

        // collect ON subexpressions
        for (XNode onExpr : xSelect.onExprs) {
            Map<XNode, ParameterizedSQL> map = genParameterizedSQLForSubs(onExpr);
            for (XNode x : map.keySet()) {
                selects.add(map.get(x));
                columnHooks.add(x.id);
            }
        }

        // collect WHERE expressions
        if (xSelect.whereExpr != null) {
            Map<XNode, ParameterizedSQL> map = genParameterizedSQLForSubs(xSelect.whereExpr);
            for (XNode x : map.keySet()) {
                selects.add(map.get(x));
                columnHooks.add(x.id);
            }
        }

        // collect ParameterizedRow
        List<String> tables = new ArrayList<>();
        List<SerializedRow> rows = new ArrayList<>();
        for (int i = 0; i < xSelect.inputTables.size(); i++) {
            List<String> cols = xSelect.inputTables.get(i).getRecordType().getFieldNames();
            List<String> types = xSelect.inputTables.get(i).getRecordType().getFieldList().stream().map(x -> x.getType().getSqlTypeName().getName())
                    .collect(Collectors.toList());
            tables.add(String.format("(VALUES !(%s,%s)s ) AS %s(%s)", xSelect.id, xSelect.inputTableAliases.get(i), xSelect.inputTableAliases.get(i),
                    String.join(", ", cols)));
            rows.add(new SerializedRow(xSelect.inputTableAliases.get(i), cols, types));
        }

        // assemble clauses
        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ", new ArrayList<>(), new HashMap<>(), rows, new ArrayList<>()))
                .concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\nFROM ").concat(String.join(", ", tables));
        sql = this.wrapWith(sql, false, false);
        return new Pair<>(sql, columnHooks);
    }

    /***********************************************************
     * Pin subspace milestone queries generation
     ***********************************************************/

    public ParameterizedSQL genInputPinSubspaceMilestoneSQL(int i) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) this.xnode;
        List<ParameterizedSQL> selects = new ArrayList<>();
        IID iid = this.milestones.get(i).iid;

        // then the seq number
        selects.add(
                new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOuterOrderByString())));
        // then the iid with seq number for the purpose of sorting mix order
        selects.add(new ParameterizedSQL(
                "(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", iid.toOuterOrderByString())
                        + iid.toOuterSelectString(false) + ")"));

        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, false));

        // process WHERE, not that we only need non-NULL-padding rows
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr))
                    .concat(String.format(" AND ROW(%s) IS NOT NULL", iid.toOuterSelectString(true)))
                    .concat(String.format(" AND !(%s,pin)s", this.id));
        } else {
            sql = sql.concat("\nWHERE ").concat(String.format("ROW(%s) IS NOT NULL", iid.toOuterSelectString(true)))
                    .concat(String.format(" AND !(%s,pin)s", this.id));
        }
        sql = sql.concat("\nGROUP BY ").concat(iid.toOuterSelectString(false));
        sql = this.wrapWith(sql, true, false);

        ParameterizedSQL wrapSql = new ParameterizedSQL(String.format("WITH tmp (seq, iid) AS (")).concat(sql)
                .concat(")\n");
        wrapSql = wrapSql.concat(
                String.format(
                        "SELECT MIN(seq) seq, COUNT(*) count, ROW_TO_JSON(MIN_IID(iid)) iid FROM tmp GROUP BY seq / %d ORDER BY seq / %d",
                        this.qc.pageSize, this.qc.pageSize));

        wrapSql = this.insertPinSubspace(wrapSql);
        return wrapSql;
    }

    /***********************************************************
     * Pin subspace page queries generation
     ***********************************************************/

    public ParameterizedSQL genInputPinSubspacePageSQL(int i) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (this.xnode);
        IID iid = this.milestones.get(i).iid;

        // we will group by input IID to ensure correct reproduction of the input table
        // thus, for each input column, we have to aggregate the values
        // since IID is unique, values in a single group should always be the same, so
        // we select the first one
        List<ParameterizedSQL> selects = new ArrayList<>();
        List<String> origInputCols = xSelect.inputTables.get(i).getRecordTypeFullyQualifed().getFieldNames();
        selects.addAll(origInputCols.stream().map(x -> new ParameterizedSQL(String.format("(ARRAY_AGG(%s))[1]", x))).collect(Collectors.toList()));
        // selects.addAll(origInputCols.stream().map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) iid", iid.toOuterSelectString(false))));

        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(this.genFromHelper(xSelect.fromExpr, true, true));
        if (xSelect.whereExpr != null) {
            sql = sql.concat("\nWHERE ").concat(this.toParameterizedSQL(xSelect.whereExpr))
                    .concat(String.format(" AND !(%s,all)s AND !(%s,pin)s", this.id, this.id));
        } else {
            sql = sql.concat(String.format("\nWHERE !(%s,all)s AND !(%s,pin)s", this.id, this.id));
        }
        sql = sql.concat("\nGROUP BY ").concat(iid.toOuterSelectString(false));
        sql = sql.concat("\n" + "ORDER BY " + iid.toOuterOrderByString());
        sql = this.wrapWith(sql, true, true);

        sql = this.insertPinSubspace(sql);
        return sql;
    }

}

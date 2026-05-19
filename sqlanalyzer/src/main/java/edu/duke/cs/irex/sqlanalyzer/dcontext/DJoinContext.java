package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.JoinType;
import org.apache.calcite.util.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL.SerializedRow;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XJoinNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.IID;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithNode;

public class DJoinContext extends DContext {
    // public final List<DContextMilestone> milestones;
    public final List<XTableValuedNode> inputXNodes;
    public final XSelectNode xSelect;
    public final DSelectContext selectCtx;

    public DJoinContext(QueryContext qc, XSelectNode xSelect, List<DContext> dContextAncestors, List<XWithNode> xWithAncestors, XJoinNode joinNode,
            DSelectContext selectCtx, List<XTableValuedNode> inputs) throws AnalyzerException {
        super(qc, joinNode, dContextAncestors, xWithAncestors);
        this.xSelect = xSelect;
        this.selectCtx = selectCtx;
        this.inputXNodes = inputs;
    }

    @Override
    public DContextCode prepareCode() throws AnalyzerException {
        int cnt = 0;
        for (XTableValuedNode input : this.inputXNodes) {
            this.milestones.add(new DContextMilestone(this.id, cnt++, input.getIID(), input.getSargable()));
        }
        XJoinNode xJoin = (XJoinNode) this.xnode;
        this.milestones.add(new DContextMilestone(this.id, cnt++, xJoin.getIID(), xJoin.getSargable()));
        return new DJoinContextCode(this);
    }

    /* ***********************************************************
     * Record type generation functions
     *********************************************************** */

    public Pair<RelRecordType, List<TableContent.Usage>> genInputRecordType(int i) throws AnalyzerException {
        XTableValuedNode xnode = this.inputXNodes.get(i); // i == 0 ? ((XJoinNode) this.xnode).left : ((XJoinNode) this.xnode).right;
        if (xnode instanceof XJoinNode) {
            // if input is the output of a child context, just grab it from child context code
            return ((DJoinContext) this.children.get(i)).genOutputRecordType();
        }

        // otherwise it is a base or derived table, check
        int j = xSelect.inputTables.indexOf(xnode);
        return new Pair<>(this.xSelect.inputTables.get(j).getRecordTypeWithIID(), ((DSelectContextCode) selectCtx.code).tables.get(j).columnUsage);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genOutputRecordType() throws AnalyzerException {
        XJoinNode xJoin = (XJoinNode) this.xnode;
        RelRecordType joinRecord = xJoin.getRecordTypeWithIID();
        List<RelDataTypeField> updatedFieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        int offset = 0;

        // collect columns
        for (int i = 0; i < joinRecord.getFieldCount(); i++) {
            updatedFieldList.add(new RelDataTypeFieldImpl(
                    joinRecord.getFieldList().get(i).getName(), offset++,
                    joinRecord.getFieldList().get(i).getType()));
            if (i == joinRecord.getFieldCount() - 1) {
                usages.add(TableContent.Usage.IID);
            } else {
                usages.add(TableContent.Usage.DISPLAY);
            }
        }
        return new Pair<RelRecordType, List<TableContent.Usage>>(joinRecord, usages);
    }

    /* ***********************************************************
     * Milestone queries generation functions
     *********************************************************** */

    public ParameterizedSQL genInputMilestoneSQL(int i) {
        XTableValuedNode xnode = this.inputXNodes.get(i); // i == 0 ? ((XJoinNode) this.xnode).left : ((XJoinNode) this.xnode).right;
        if (xnode instanceof XJoinNode) {
            // if input is the output of a child context, just grab it from child context code
            List<ParameterizedSQL> childMilestoneSQLs = ((DJoinContextCode) ((DJoinContext) this.children.get(i)).code).milestoneSQLs;
            return childMilestoneSQLs.get(childMilestoneSQLs.size() - 1);
        }

        // otherwise it is a base or derived table, check
        int j = xSelect.inputTables.indexOf(xnode);
        return ((DSelectContextCode) selectCtx.code).milestoneSQLs.get(j);
    }

    public ParameterizedSQL genOutputMilestoneSQL(boolean pinSubspace) throws AnalyzerException {
        XJoinNode xJoin = (XJoinNode) this.xnode;
        int outputTableIdx = this.inputXNodes.size();
        if (xJoin == xSelect.fromExpr && xJoin.joinType == JoinType.COMMA) {
            // at top level, if join type is comma, then don't need to construct such query as cross join might blow up the memory
            return null;
        }
        // process SELECT: seq, iid, index columns
        List<ParameterizedSQL> selects = new ArrayList<>();
        IID iid = this.milestones.get(outputTableIdx).iid;
        selects.add(new ParameterizedSQL(
                String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString())));
        selects.add(new ParameterizedSQL(
                "(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1, ", iid.toOrderByString()) + iid.toSelectString() + ")"));
        if (this.milestones.get(outputTableIdx).sarg != null) {
            selects.addAll(this.milestones.get(outputTableIdx).sarg.cols.stream()
                    .map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        }

        ParameterizedSQL sql = new ParameterizedSQL("SELECT ").concat(ParameterizedSQL.join(", ", selects));
        sql = sql.concat("\n").concat(this.selectCtx.genFromHelper(xJoin, true, false));
        if (pinSubspace) {
            sql = sql.concat(String.format("\nWHERE !(%s,pin)s", this.selectCtx.id));
            for (int i = 0; i < this.xSelect.inputTables.size(); i++) {
                sql.pins.add(new ParameterizedSQL.SerializedPin(this.selectCtx.milestones.get(i)));
            }
            if (this.selectCtx.tGroup != -1) {
                sql.pins.add(new ParameterizedSQL.SerializedPin(this.selectCtx.milestones.get(this.selectCtx.tGroup)));
            }
        }
        sql = this.wrapWith(sql, true, true);
        sql = this.wrapOutputMilestoneSQL(sql);
        return sql;
    }

    /* ***********************************************************
     * Page queries generation functions
     *********************************************************** */

    public ParameterizedSQL genInputPageSQL(int i) {
        XTableValuedNode xnode = this.inputXNodes.get(i); // i == 0 ? ((XJoinNode) this.xnode).left : ((XJoinNode) this.xnode).right;
        if (xnode instanceof XJoinNode) {
            // if input is the output of a child context, just grab it from child context code
            List<ParameterizedSQL> childPageSQLs = ((DJoinContextCode) ((DJoinContext) this.children.get(i)).code).pageSQLs;
            return childPageSQLs.get(childPageSQLs.size() - 1);
        }

        // otherwise it is a base or derived table, check
        int j = xSelect.inputTables.indexOf(xnode);
        return ((DSelectContextCode) this.selectCtx.code).pageSQLs.get(j);
    }

    public ParameterizedSQL genOutputPageSQL(boolean pinSubspace) throws AnalyzerException {
        XJoinNode xJoin = (XJoinNode) this.xnode;
        int outputTableIdx = this.inputXNodes.size();
        if (xJoin == xSelect.fromExpr && xJoin.joinType == JoinType.COMMA) {
            // at top level, if join type is comma, then don't need to construct such query as cross join might blow up the memory
            // return new Pair<>(null, null);
            return null;
        }

        IID xJoinIID = xJoin.getIID();
        List<ParameterizedSQL> selects = new ArrayList<>();
        // List<Set<String>> columnHooks = new ArrayList<>();

        // collect contents (exclude iid columns)
        RelRecordType record = xJoin.getRecordTypeFullyQualifed();
        for (int i = 0; i < record.getFieldCount(); i++) {
            // selects.add(new ParameterizedSQL(tables.get(i) + "." + attrs.get(i)));
            selects.add(new ParameterizedSQL(record.getFieldNames().get(i)));
            // columnHooks.add(new HashSet<String>());
        }

        // collect iid
        selects.add(new ParameterizedSQL(String.format("ROW_TO_JSON(ROW(%s)) iid", xJoinIID.toSelectString())));
        // columnHooks.add(new HashSet<String>());

        // collect on expr evaluation
        // if (!(xJoin.condition instanceof XUsingJoinCondition)) {
        // selects.add(this.toParameterizedSQL(xJoin.condition));
        // columnHooks.add(new HashSet<String>(Collections.singleton(xJoin.condition.id)));
        // }

        ParameterizedSQL sql = (new ParameterizedSQL("SELECT ")).concat(ParameterizedSQL.join("\n  , ", selects));
        sql = sql.concat("\n").concat(selectCtx.genFromHelper(xJoin, true, true));
        if (pinSubspace) {
            sql = sql.concat(String.format("\nWHERE !(%s,pin)s", this.selectCtx.id));
            for (int i = 0; i < this.xSelect.inputTables.size(); i++) {
                sql.pins.add(new ParameterizedSQL.SerializedPin(this.selectCtx.milestones.get(i)));
            }
            if (this.selectCtx.tGroup != -1) {
                sql.pins.add(new ParameterizedSQL.SerializedPin(this.selectCtx.milestones.get(this.selectCtx.tGroup)));
            }
        }
        sql = sql.concat("\n" + "ORDER BY " + xJoinIID.toOrderByString());
        sql = this.wrapWith(sql, true, true);
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(outputTableIdx)));
        return sql;
        // return new Pair<>(sql, columnHooks);
    }

    /* ***********************************************************
     * Pin subspace milestone queries generation functions
     *********************************************************** */

    public ParameterizedSQL genInputPinSubspaceMilestoneSQL(int i) throws AnalyzerException {
        XTableValuedNode xnode = this.inputXNodes.get(i); // i == 0 ? ((XJoinNode) this.xnode).left : ((XJoinNode) this.xnode).right;
        if (xnode instanceof XJoinNode) {
            // if input is the output of a child context, just grab it from child context code
            List<ParameterizedSQL> childMilestoneSQLs = ((DJoinContextCode) ((DJoinContext) this.children.get(i)).code).pinSubspaceMilestoneSQLs;
            return childMilestoneSQLs.get(childMilestoneSQLs.size() - 1);
        }

        // otherwise it is a base or derived table, check
        int j = xSelect.inputTables.indexOf(xnode);
        return ((DSelectContextCode) this.selectCtx.code).pinSubspaceMilestoneSQLs.get(j);
    }

    /* ***********************************************************
     * Pin subspace page queries generation functions
     *********************************************************** */

    public ParameterizedSQL genInputPinSubspacePageSQL(int i) throws AnalyzerException {
        XTableValuedNode xnode = this.inputXNodes.get(i); // i == 0 ? ((XJoinNode) this.xnode).left : ((XJoinNode) this.xnode).right;
        if (xnode instanceof XJoinNode) {
            // if input is the output of a child context, just grab it from child context code
            List<ParameterizedSQL> childPageSQLs = ((DJoinContextCode) ((DJoinContext) this.children.get(i)).code).pinSubspacePageSQLs;
            return childPageSQLs.get(childPageSQLs.size() - 1);
        }

        // otherwise it is a base or derived table, check
        int j = xSelect.inputTables.indexOf(xnode);
        return ((DSelectContextCode) this.selectCtx.code).pinSubspacePageSQLs.get(j);
    }

    /* ***********************************************************
     * ON expression evaluation queries generation functions
     *********************************************************** */

    Pair<ParameterizedSQL, List<String>> genOnEvalSQL() throws AnalyzerException {
        XJoinNode xJoin = (XJoinNode) this.xnode;
        if (xJoin.condition == null) {
            return new Pair<>(null, null);
        }

        List<ParameterizedSQL> selects = new ArrayList<>();
        List<String> columnHooks = new ArrayList<>();

        // collect ON subexpressions
        Map<XNode, ParameterizedSQL> map = genParameterizedSQLForSubs(xJoin.condition, this.xSelect);
        for (XNode x : map.keySet()) {
            selects.add(map.get(x));
            columnHooks.add(x.id);
        }

        // collect ParameterizedRow
        List<String> tables = new ArrayList<>();
        List<SerializedRow> rows = new ArrayList<>();
        List<Integer> inputs = new ArrayList<>();
        this.collectInputTableUnderJoin(xJoin, inputs);
        for (Integer i : inputs) {
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

    /* ***********************************************************
     * Helper functions
     *********************************************************** */
    private ParameterizedSQL wrapOutputMilestoneSQL(ParameterizedSQL sql) {
        int outputTableIdx = this.inputXNodes.size();
        DContextMilestone mst = this.milestones.get(outputTableIdx);
        ParameterizedSQL wrapSql = new ParameterizedSQL(String.format("WITH tmp (seq, %s) AS (\n    ",
                String.join(", ", mst.getCTEAlias()))).concat(sql).concat("\n)\n");
        List<String> outerSelectList = new ArrayList<>();
        outerSelectList.add("MIN(seq) seq");
        outerSelectList.add("COUNT(*) count");
        outerSelectList.add("ROW_TO_JSON(MIN_IID(iid)) iid");
        for (int i = 0; i < mst.sarg.size(); i++) {
            String alias = "sarg_" + i;
            if (mst.sarg.inputColIndex.get(i) < 0) {
                String iidSargAlias = mst.sarg.inputColIndex.get(i) == -1 ? alias + "_iid" : alias + "_iid_mix";
                outerSelectList.add(String.format("ARRAY[ROW_TO_JSON(MIN_IID(%s)), ROW_TO_JSON(MAX_IID(%s))] AS %s", alias, alias, iidSargAlias));
            } else {
                outerSelectList.add(String.format("ARRAY[MIN(%s), MAX(%s)] AS %s", alias, alias, alias));
            }
        }

        ParameterizedSQL outer = new ParameterizedSQL(String.format(
                "SELECT %s \nFROM tmp\nGROUP BY seq / %d ORDER BY seq / %d",
                String.join(", ", outerSelectList),
                this.qc.pageSize, this.qc.pageSize));
        return wrapSql.concat(outer);
    }

    private void collectInputTableUnderJoin(XTableValuedNode xnode, List<Integer> inputs) {
        int i = xSelect.inputTables.indexOf(xnode);
        if (i >= 0) {
            inputs.add(i);
        } else if (xnode instanceof XJoinNode) {
            this.collectInputTableUnderJoin(((XJoinNode) xnode).left, inputs);
            this.collectInputTableUnderJoin(((XJoinNode) xnode).right, inputs);
        }
        return;
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
        json.addProperty("type", this.getClass().getSimpleName());
        json.addProperty("XTableValuedNode_id", this.xnode.id);
        json.add("XWithNode_ancestor_ids", XNode.toJsonArrayOfXNodeIds(this.xWithAncestors));
        json.addProperty("XJoinNode_id", this.xnode.id);
        json.addProperty("join_type", ((XJoinNode) this.xnode).joinType.toString());
        json.add("code", this.code.toJsonObject());
        JsonArray childCtx = new JsonArray();
        for (int i = 0; i < this.children.size(); i++) {
            if (this.children.get(i) == null) {
                continue;
            }
            childCtx.add(this.children.get(i).toJsonObject());
        }
        json.add("children", childCtx);

        JsonArray inputTables = new JsonArray();
        for (int i = 0; i < this.inputXNodes.size(); i++) {
            inputTables.add(this.inputXNodes.get(i).id);
        }
        json.add("input_XTableValuedNode_ids", inputTables);

        return json;
    }
}

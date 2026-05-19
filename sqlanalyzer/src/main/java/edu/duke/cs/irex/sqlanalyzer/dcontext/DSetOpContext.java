package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
// import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.util.Pair;

// import com.google.common.reflect.Parameter;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XCellValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSetOpNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.IID;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.Sargable;
import edu.duke.cs.irex.sqlanalyzer.xnode.XWithNode;

public class DSetOpContext extends DContext {

    public final int tLeftInput = 0;
    public final int tRightInput = 1;
    public final int tLeftIntermediate = 2;
    public final int tRightIntermediate = 3;
    public final int tOutput = 4;

    public DSetOpContext(QueryContext qc, XSetOpNode xSetOp, List<DContext> dContextAncestors,
            List<XWithNode> xWithAncestors) throws AnalyzerException {
        super(qc, xSetOp, dContextAncestors, xWithAncestors);
        return;
    }

    public DSetOpContext(DContext refContext, DContext parent) throws AnalyzerException {
        super(refContext, parent);
        return;
    }

    @Override
    public DContextCode prepareCode() throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) this.xnode;
        DContext leftCtx = this.qc.xNodeToDContext.get(xSetOp.left);
        DContext rightCtx = this.qc.xNodeToDContext.get(xSetOp.right);
        this.milestones.add(new DContextMilestone(this.id, this.tLeftInput, leftCtx.milestones.get(leftCtx.milestones.size() - 1).iid,
                leftCtx.milestones.get(leftCtx.milestones.size() - 1).sarg));
        this.milestones.add(new DContextMilestone(this.id, this.tRightInput, rightCtx.milestones.get(rightCtx.milestones.size() - 1).iid,
                rightCtx.milestones.get(rightCtx.milestones.size() - 1).sarg));

        Sargable leftSarg = xSetOp.left.getSargable();
        this.milestones.add(new DContextMilestone(this.id, this.tLeftIntermediate, this.genIntermediateIID(xSetOp.left), leftSarg));
        leftSarg.inputIndex.set(0, this.tLeftInput);
        Sargable rightSarg = xSetOp.right.getSargable();
        rightSarg.inputIndex.set(0, this.tRightInput);
        this.milestones.add(new DContextMilestone(this.id, this.tRightIntermediate, this.genIntermediateIID(xSetOp.right), rightSarg));
        this.milestones.add(new DContextMilestone(this.id, this.tOutput, xSetOp.getIID(), null));
        return new DSetOpContextCode(this);
    }

    private IID genIntermediateIID(XTableValuedNode xTable) throws AnalyzerException {
        List<String> cols = IntStream.range(0, xTable.getCommaSeparatedColumnNames().size()).mapToObj(i -> this.id + "_iid_" + i).collect(Collectors.toList());
        List<String> types = xTable.getRecordType().getFieldList().stream().map(x -> x.getType().getSqlTypeName().getName()).collect(Collectors.toList());
        List<Boolean> dirs = new ArrayList<>(Collections.nCopies(cols.size(), false));
        List<String> xnodes = new ArrayList<>(Collections.nCopies(cols.size(), null));
        String ctxId = this.id;
        return new IID(cols, types, dirs, xnodes, ctxId);
    }

    // private Sargable genIntermediateSargable(XTableValuedNode xTable) throws AnalyzerException {
    //     DContext inputCtx = this.qc.xNodeToDContext.get(xTable);
    //     return inputCtx
    //     if (inputCtx instanceof DSelectContext) {
    //         return ((DSelectContext) inputCtx).genPostGroupBySargable();
    //     } else {
    //         return null;
    //     }
    // }

    /***********************************************************
     * Record type generation functions
     ***********************************************************/

    public Pair<RelRecordType, List<TableContent.Usage>> genInputRecordType(XTableValuedNode xTable) throws AnalyzerException {
        DContext inputCtx = this.qc.xNodeToDContext.get(xTable);
        if (inputCtx instanceof DSelectContext) {
            return ((DSelectContext) inputCtx).genOutputRecordType();
        } else if (inputCtx instanceof DSetOpContext) {
            return ((DSetOpContext) inputCtx).genOutputRecordType();
        } else {
            return null;
        }
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genIntermediateRecordType() throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) this.xnode;
        RelRecordType curRecordType = xSetOp.getRecordTypeWithIID();
        List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        for (int i = 0; i < curRecordType.getFieldCount(); i++) {
            if (i == curRecordType.getFieldCount() - 1) {
                // add duplicate count first
                fieldList.add(new RelDataTypeFieldImpl("count", i, this.qc.analyzer.createSqlType(SqlTypeName.get("INTEGER"))));
                usages.add(TableContent.Usage.DISPLAY);
                fieldList.add(new RelDataTypeFieldImpl(curRecordType.getFieldList().get(i).getName(), i + 1, curRecordType.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.IID);
            } else {
                // add original column
                fieldList.add(new RelDataTypeFieldImpl(curRecordType.getFieldList().get(i).getName(), i, curRecordType.getFieldList().get(i).getType()));
                usages.add(TableContent.Usage.DISPLAY);
            }
        }
        RelRecordType updatedRecordType = new RelRecordType(fieldList);
        return new Pair<>(updatedRecordType, usages);
    }

    public Pair<RelRecordType, List<TableContent.Usage>> genOutputRecordType() throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) this.xnode;
        RelRecordType curRecordType = xSetOp.getRecordTypeWithIID();
        // List<RelDataTypeField> fieldList = new ArrayList<>();
        List<TableContent.Usage> usages = new ArrayList<>();
        for (int i = 0; i < curRecordType.getFieldCount(); i++) {
            // fieldList.add(new RelDataTypeFieldImpl(curRecordType.getFieldList().get(i).getName(), i, curRecordType.getFieldList().get(i).getType()));
            if (i == curRecordType.getFieldCount() - 1) {
                usages.add(TableContent.Usage.IID);
            } else {
                usages.add(TableContent.Usage.DISPLAY);
            }
        }
        return new Pair<>(curRecordType, usages);
    }

    /***********************************************************
     * Milestone queries generation functions
     ***********************************************************/

    public ParameterizedSQL genInputMilestoneSQL(XTableValuedNode xTable) throws AnalyzerException {
        DContext inputCtx = this.qc.xNodeToDContext.get(xTable);
        if (inputCtx instanceof DSelectContext) {
            return ((DSelectContext) inputCtx).genOutputMilestoneSQL(false);
        } else if (inputCtx instanceof DSetOpContext) {
            return ((DSetOpContext) inputCtx).genOutputMilestoneSQL();
        } else {
            return null;
        }
    }

    public ParameterizedSQL genIntermediateMilestoneSQL(XTableValuedNode xTable) throws AnalyzerException {
        ParameterizedSQL sql = this.toParameterizedSQL(xTable, true, false);
        sql = this.wrapWith(sql, true, false);

        IID iid = xTable == ((XSetOpNode) (this.xnode)).left ? this.milestones.get(this.tLeftIntermediate).iid
                : this.milestones.get(this.tRightIntermediate).iid;
        // int tableIdx = xTable == ((XSetOpNode) (this.xnode)).left ? this.tLeftIntermediate : this.tRightIntermediate;
        List<String> childCtxIID = xTable.getIID().convertColsToAlias();

        // first wrap to grab the child context IID as a sargable
        List<String> selects = new ArrayList<>();
        selects.add(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString()));
        selects.add("ROW(" + String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", iid.toOrderByString()) + ", " + iid.toSelectString() + ")"); // add seq number into iid to stay consistent with the implementation of DSelectContext
        selects.add(String.format("ARRAY[MIN_IID(ROW(%s)), MAX_IID(ROW(%s))]", String.join(", ", childCtxIID), String.join(", ", childCtxIID)));

        sql = new ParameterizedSQL("WITH tmp(" + String.join(", ", iid.cols) + ", " + String.join(", ", childCtxIID) + ") AS (").concat(sql).concat(")");
        sql = sql.concat("\nSELECT ").concat(String.join(", ", selects));
        sql = sql.concat("\nFROM tmp");
        sql = sql.concat("\nGROUP BY " + String.join(", ", iid.cols));

        // final wrap to return IID and the sargable IID for pushdown
        List<String> outerSelectList = new ArrayList<>();
        outerSelectList.add("MIN(seq) seq");
        outerSelectList.add("COUNT(*) count");
        outerSelectList.add("ROW_TO_JSON(MIN_IID(iid)) iid");
        outerSelectList.add("ARRAY[ROW_TO_JSON(MIN_IID(sarg_iid[1])), ROW_TO_JSON(MAX_IID(sarg_iid[2]))] sarg_iid");

        sql = new ParameterizedSQL("WITH tp(seq, iid, sarg_iid) AS (").concat(sql).concat(") ");
        sql = sql.concat(String.format("\nSELECT %s \nFROM tp \nGROUP BY seq / %d \nORDER BY seq / %d", String.join(", ", outerSelectList),
                this.qc.pageSize, this.qc.pageSize));
        return sql;
    }

    public ParameterizedSQL genOutputMilestoneSQL() throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) (this.xnode);
        ParameterizedSQL sql = this.toParameterizedSQL(xSetOp, true, false);
        IID iid = this.milestones.get(this.tOutput).iid;
        List<String> iidNumbers = IntStream.range(0, iid.cols.size() - 1).mapToObj(i -> this.id + "_iid_" + i).collect(Collectors.toList());
        sql = this.wrapWith(sql, false, false);
        sql = new ParameterizedSQL("WITH tmp AS (").concat(sql).concat(") ");
        List<ParameterizedSQL> selects = new ArrayList<>();
        // selects.addAll(iidNumbers.stream().map(x -> new ParameterizedSQL(x)).collect(Collectors.toList()));
        selects.add(new ParameterizedSQL(String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", String.join(", ", iidNumbers)))); // seq number
        selects.add(new ParameterizedSQL(String.format("ROW(%s, %s, ROW_NUMBER() OVER (PARTITION BY %s ORDER BY %s) - 1)",
                String.format("ROW_NUMBER() OVER (ORDER BY %s) - 1", String.join(", ", iidNumbers)), String.join(", ", iidNumbers),
                String.join(", ", iidNumbers), String.join(", ", iidNumbers)))); // iid with group seq number, add leading seq number to be consistent with DSelectContext
        sql = sql.concat("\nSELECT ").concat(ParameterizedSQL.join(", ", selects)).concat("\nFROM tmp");

        List<String> outerSelectList = new ArrayList<>();
        outerSelectList.add("MIN(seq) seq");
        outerSelectList.add("COUNT(*) count");
        outerSelectList.add("ROW_TO_JSON(MIN_IID(iid)) iid");
        sql = new ParameterizedSQL("WITH tp(seq, iid) AS (").concat(sql).concat(") ");
        sql = sql.concat(String.format("\nSELECT %s \nFROM tp \nGROUP BY seq / %d \nORDER BY seq / %d", String.join(", ", outerSelectList),
                this.qc.pageSize, this.qc.pageSize));

        return sql;
    }

    /***********************************************************
     * Page queries generation functions
     ***********************************************************/

    public ParameterizedSQL genInputPageSQL(XTableValuedNode xTable) throws AnalyzerException {
        DContext inputCtx = this.qc.xNodeToDContext.get(xTable);
        if (inputCtx instanceof DSelectContext) {
            return ((DSelectContext) inputCtx).genOutputPageSQL(false).left;
        } else if (inputCtx instanceof DSetOpContext) {
            return ((DSetOpContext) inputCtx).genOutputPageSQL();
        } else {
            return null;
        }
    }

    public ParameterizedSQL genIntermediatePageSQL(XTableValuedNode xTable) throws AnalyzerException {
        // DContext inputCtx = this.qc.xNodeToDContext.get(xTable);
        ParameterizedSQL sql = this.toParameterizedSQL(xTable, true, true);
        sql = this.wrapWith(sql, true, true);

        IID iid = xTable == ((XSetOpNode) (this.xnode)).left ? this.milestones.get(this.tLeftIntermediate).iid
                : this.milestones.get(this.tRightIntermediate).iid;
        int tableIdx = xTable == ((XSetOpNode) (this.xnode)).left ? this.tLeftIntermediate : this.tRightIntermediate;

        // List<String> childCtxIID = xTable.getIID().convertColsToAlias();
        List<String> selects = new ArrayList<>();
        List<String> iidCols = iid.convertColsToAlias();
        List<String> colAliases = IntStream.range(0, iidCols.size()).mapToObj(i -> "col_" + i).collect(Collectors.toList()); // we need this simply because PageExecd will treat any column containing "iid" as JSON
        selects.addAll(IntStream.range(0, iidCols.size()).mapToObj(i -> iidCols.get(i) + " AS " + colAliases.get(i)).collect(Collectors.toList()));
        selects.add("COUNT(*) count");
        selects.add("ROW_TO_JSON(ROW(" + String.join(", ", iidCols) + ")) iid");
        sql = new ParameterizedSQL("WITH tmp(" + String.join(", ", iidCols) + ") AS (").concat(sql).concat(")");
        sql = sql.concat("\nSELECT " + String.join(", ", selects));
        sql = sql.concat("\nFROM tmp");
        sql = sql.concat(String.format("\nWHERE !(%s,all)s", this.id));
        sql = sql.concat("\nGROUP BY " + String.join(", ", colAliases));
        // sql = sql.concat(String.format("\nHAVING !(%s,iid)s", this.id));
        sql = sql.concat("\nORDER BY " + String.join(", ", colAliases));
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(tableIdx)));
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        return sql;
    }

    public ParameterizedSQL genOutputPageSQL() throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) (this.xnode);
        ParameterizedSQL sql = this.toParameterizedSQL(xSetOp, true, true);
        sql = this.wrapWith(sql, true, true);
        sql = new ParameterizedSQL("WITH tmp AS (\n").concat(sql).concat("\n) ");
        IID iid = this.milestones.get(this.tOutput).iid;
        List<String> iidCols = IntStream.range(0, iid.cols.size() - 1).mapToObj(i -> iid.cols.get(i) + " AS col_" + i).collect(Collectors.toList());
        sql = sql.concat("\nSELECT " + String.join(", ", iidCols));
        sql = sql.concat(", ROW_TO_JSON(ROW(" + iid.toSelectString() + ")) iid");
        sql = sql.concat("\nFROM tmp");
        sql = sql.concat(String.format("\nWHERE !(%s,iid)s", this.id));
        sql = sql.concat("\nORDER BY " + iid.toOrderByString());
        sql.filters.put(this.id, new ParameterizedSQL.SerializedFilter(this.milestones.get(this.tOutput)));
        for (DContext c : this.children) {
            this.collectCtxFilters(sql.filters, c);
        }
        return sql;
    }
}

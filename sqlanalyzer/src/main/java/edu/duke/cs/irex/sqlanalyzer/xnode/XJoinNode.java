package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;

import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.JoinConditionType;
import org.apache.calcite.sql.JoinType;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XJoinNode extends XTableValuedNode {
    public final XTableValuedNode left;
    public final XTableValuedNode right;
    public final boolean isDeclaredNatural;
    public final JoinType joinType; // one of INNER, FULL, CROSS, LEFT, RIGHT, and COMMA
    public final XCellValuedNode condition; // besides explicitly specified USING/ON, this may be an
                                            // XUsingJoinCondition inferred from a natural join
    public final RelRecordType relRecordType;
    public final RelRecordType relRecordTypeFullyQualified;
    public final List<Integer> leftColumnToOutputIndex;
    public final List<Integer> rightColumnToOutputIndex;

    public XJoinNode(QueryContext qc, SqlJoin sqlJoin, XNode parent, SqlValidatorScope sqlScope,
            boolean isVisitingSelectFrom) throws AnalyzerException {
        super(qc, sqlJoin, parent, sqlScope);
        this.left = (XTableValuedNode) qc.transform(sqlJoin.getLeft(), this, sqlScope,
                isVisitingSelectFrom);
        this.right = (XTableValuedNode) qc.transform(sqlJoin.getRight(), this, sqlScope,
                isVisitingSelectFrom);
        this.isDeclaredNatural = sqlJoin.isNatural();
        this.joinType = sqlJoin.getJoinType();
        if (sqlJoin.getConditionType() == JoinConditionType.USING || sqlJoin.isNatural()) {
            this.condition = new XUsingJoinCondition(this.qc, this);
        } else if (sqlJoin.getConditionType() == JoinConditionType.ON) {
            this.condition =
                    (XBasicCallNode) (qc.transform(sqlJoin.getCondition(), this, sqlScope, false));
        } else {
            this.condition = null;
        }
        // this.relRowType is nasty (with RelCrossType), so let's flatten it.
        // Start with USING columns, if any; then the rest of the columns from the left and right.
        // In the same process, we figure the mappings from input column indexes to output column
        // indexes.
        List<String> usingColumns = (this.condition instanceof XUsingJoinCondition)
                ? ((XUsingJoinCondition) (this.condition)).columnNames
                : new ArrayList<>();
        List<RelDataTypeField> leftFields =
                new ArrayList<>(this.left.getRecordType().getFieldList());
        List<RelDataTypeField> rightFields =
                new ArrayList<>(this.right.getRecordType().getFieldList());
        List<RelDataTypeField> leftFieldsQualified =
                new ArrayList<>(this.left.getRecordTypeFullyQualifed().getFieldList());
        List<RelDataTypeField> rightFieldsQualified =
                new ArrayList<>(this.right.getRecordTypeFullyQualifed().getFieldList());
        Map<Integer, Integer> leftMap = new HashMap<>();
        Map<Integer, Integer> rightMap = new HashMap<>();
        List<RelDataTypeField> fields = new ArrayList<>();
        List<RelDataTypeField> fieldsQual = new ArrayList<>();
        for (int i = 0; i < usingColumns.size(); i++) {
            int iLeft = this.qc.analyzer.findMatchingNameInFields(usingColumns.get(i), leftFields);
            fields.add(leftFields.get(iLeft));
            fieldsQual.add(leftFields.get(iLeft));
            leftMap.put(iLeft, i);
            int iRight =
                    this.qc.analyzer.findMatchingNameInFields(usingColumns.get(i), rightFields);
            rightMap.put(iRight, i);
        }
        int target = usingColumns.size();
        for (int i = 0; i < leftFields.size(); i++) {
            if (!leftMap.containsKey(i)) {
                fields.add(leftFields.get(i));
                fieldsQual.add(leftFieldsQualified.get(i));
                leftMap.put(i, target);
                target++;
            }
        }
        for (int i = 0; i < rightFields.size(); i++) {
            if (!rightMap.containsKey(i)) {
                fields.add(rightFields.get(i));
                fieldsQual.add(rightFieldsQualified.get(i));
                rightMap.put(i, target);
                target++;
            }
        }
        this.relRecordType = new RelRecordType(fields);
        this.relRecordTypeFullyQualified = new RelRecordType(fieldsQual);
        this.leftColumnToOutputIndex = new ArrayList<>();
        for (int i = 0; i < leftMap.size(); i++) {
            this.leftColumnToOutputIndex.add(leftMap.get(i));
        }
        this.rightColumnToOutputIndex = new ArrayList<>();
        for (int i = 0; i < rightMap.size(); i++) {
            this.rightColumnToOutputIndex.add(rightMap.get(i));
        }
        return;
    }

    @Override
    public RelRecordType getRecordType() {
        return this.relRecordType;
    }

    @Override
    public RelRecordType getRecordTypeFullyQualifed() {
        return this.relRecordTypeFullyQualified;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.add("left", this.left.toJsonObject());
        json.add("right", this.right.toJsonObject());
        json.addProperty("is_declared_natural", this.isDeclaredNatural);
        json.addProperty("join_type", this.joinType.name());
        json.add("condition", (this.condition == null) ? null : this.condition.toJsonObject());
        json.addProperty("is_fancy", this.isFancy());
        json.addProperty("complex_row_type", this.getRecordType().toString());
        json.add("left_column_to_output_index",
                Analyzer.toJsonTreeByDefault(this.leftColumnToOutputIndex));
        json.add("right_column_to_output_index",
                Analyzer.toJsonTreeByDefault(this.rightColumnToOutputIndex));
        return json;
    }

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        IID leftIID = this.left.getIID();
        IID rightIID = this.right.getIID();

        // different ways to combine IID
        int structCode = 0; // assuming both child are table references
        if (!(this.left instanceof XJoinNode) && this.right instanceof XJoinNode) {
            structCode = 1;
        } else if (this.left instanceof XJoinNode && !(this.right instanceof XJoinNode)) {
            structCode = 2;
        } else if (this.left instanceof XJoinNode && this.right instanceof XJoinNode) {
            structCode = 3;
        }
        return leftIID.combineIIDForJoins(rightIID, structCode, this);
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        Sargable leftSargable = this.left.getSargable();
        Sargable rightSargable = this.right.getSargable();
        return leftSargable.combineSargable(rightSargable);
    }

    public boolean isFancy() {
        if (this.condition instanceof XUsingJoinCondition) { // natural/USING check
            if (this.joinType.equals(JoinType.INNER)) {
                return !(((XUsingJoinCondition) (this.condition)).columnNames.isEmpty());
            } else { // must be an outer join or some other exotic type
                return true;
            }
        } else if (this.joinType.equals(JoinType.COMMA) || this.joinType.equals(JoinType.CROSS)
                || this.joinType.equals(JoinType.INNER)) {
            return false;
        } else { // variants of outerjoins (FULL, LEFT, RIGHT) and possibly other exotic types
            return true;
        }
    }

    public boolean allCrossJoin() {
        if (this.joinType != JoinType.COMMA) {
            return false;
        }
        boolean left = false;
        boolean right = false;
        if (this.left instanceof XJoinNode) {
            left = ((XJoinNode) this.left).allCrossJoin();
        } else if (this.left instanceof XTableRenameNode || this.left instanceof XTableRefNode) {
            left = true;
        } else {
            left = false;
        }
        if (this.right instanceof XJoinNode) {
            right = ((XJoinNode) this.right).allCrossJoin();
        } else if (this.right instanceof XTableRenameNode || this.right instanceof XTableRefNode) {
            right = true;
        } else {
            right = false;
        }
        return left && right;
    }
}

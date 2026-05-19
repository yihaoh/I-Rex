package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.SqlAsOperator;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

/**
 * Rename a table or an expression that produces a table, using the AS (keyword optional) construct
 * in FROM. It can optionally rename columns as well; use {@link XTableValuedNode.sqlRowType} to see
 * the new column names. Note: We don't use this class to capture output column renaming in SELECT
 * or WITH table definitions.
 */
public class XTableRenameNode extends XTableValuedNode {
    public final String name; // new table name (or alias)

    public XTableRenameNode(QueryContext qc, SqlBasicCall sqlBasicCall, XNode parent,
            SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlBasicCall, parent, sqlScope);
        assert sqlBasicCall.getOperator() instanceof SqlAsOperator;
        qc.transform(sqlBasicCall.operand(0), this, sqlScope, true);
        assert sqlBasicCall.operand(1) instanceof SqlIdentifier;
        this.name = ((SqlIdentifier) (sqlBasicCall.operand(1))).names.get(0);
        return;
    }

    @Override
    public RelRecordType getRecordTypeFullyQualifed() throws AnalyzerException {
        List<RelDataTypeField> updatedFieldList = new ArrayList<>();
        RelRecordType oldRecordType = ((XTableValuedNode) this.children.get(0)).getRecordType();
        for (int i = 0; i < oldRecordType.getFieldCount(); i++) {
            updatedFieldList.add(new RelDataTypeFieldImpl(
                    this.name + "." + oldRecordType.getFieldList().get(i).getName(), i,
                    oldRecordType.getFieldList().get(i).getType()));
        }
        return new RelRecordType(updatedFieldList);
    }

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        XTableValuedNode child = this.withoutRename();
        boolean isRenameDerived = (child instanceof XTableRefNode && ((XTableRefNode) child).withItem != null)
                || child instanceof XSelectNode || child instanceof XSetOpNode;
        IID iid = child.getIID();
        if (isRenameDerived) {
            // referencing the IID columns in an outer scope, need to rename to "ctxId_iid_x"
            String ctxIdPrefix = iid.ctxId == null ? "" : iid.ctxId + "_";
            for (int i = 0; i < iid.size(); i++) {
                iid.cols.set(i, this.name + String.format(".%siid_%d", ctxIdPrefix, i));
            }
        }
        return iid;
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        XTableValuedNode child = this.withoutRename();
        boolean isRenameDerived = (child instanceof XTableRefNode && ((XTableRefNode) child).withItem != null)
                || child instanceof XSelectNode || child instanceof XSetOpNode;
        Sargable sarg = this.withoutRename().getSargable();
        if (isRenameDerived) {
            XNode tmp = (child instanceof XTableRefNode && ((XTableRefNode) child).withItem != null) ? ((XTableRefNode) child).withItem.query : child;
            // the only sargable for a derived table is its IID
            String ctxIdPrefix = this.qc.xNodeToDContext.get(tmp).id + "_";
            // if mix order, then return seq, otherwise ignore seq
            return sarg.inputColIndex.get(0) == -2 ? new Sargable(
                    new ArrayList<>(Arrays.asList(String.format("ROW(%s,", this.name + "." + ctxIdPrefix + "_seq") + String.join(", ",
                            IntStream.range(0, sarg.size())
                                    .mapToObj(i -> this.name + String.format(".%siid_%d", ctxIdPrefix, i))
                                    .collect(Collectors.toList()))
                            + ")")),
                    new ArrayList<>(Arrays.asList("ANY")),
                    new ArrayList<>(Arrays.asList(0)), new ArrayList<>(Arrays.asList(-1)))
                    : new Sargable(
                            new ArrayList<>(Arrays.asList("ROW(" + String.join(", ",
                                    IntStream.range(0, sarg.size())
                                            .mapToObj(i -> this.name + String.format(".%siid_%d", ctxIdPrefix, i))
                                            .collect(Collectors.toList()))
                                    + ")")),
                            new ArrayList<>(Arrays.asList("ANY")),
                            new ArrayList<>(Arrays.asList(0)), new ArrayList<>(Arrays.asList(-1)));
        }
        return sarg;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("new_name", this.name);
        json.add("operand", this.children.get(0).toJsonObject());
        return json;
    }
}

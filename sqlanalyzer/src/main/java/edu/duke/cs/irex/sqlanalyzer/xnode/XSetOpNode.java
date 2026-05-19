package edu.duke.cs.irex.sqlanalyzer.xnode;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlSetOperator;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XSetOpNode extends XTableValuedNode {
    public final SqlSetOperator sqlSetOperator;
    public final XTableValuedNode left;
    public final XTableValuedNode right;

    public XSetOpNode(QueryContext qc, SqlBasicCall sqlBasicCall, XNode parent,
            SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlBasicCall, parent, sqlScope);
        assert sqlBasicCall.getOperator() instanceof SqlSetOperator;
        this.sqlSetOperator = (SqlSetOperator) (sqlBasicCall.getOperator());
        this.left = (XTableValuedNode) (qc.transform(sqlBasicCall.operand(0), this, sqlScope, false));
        this.right = (XTableValuedNode) (qc.transform(sqlBasicCall.operand(1), this, sqlScope, false));
        return;
    }

    /*
     * Note: IID and Sargable are all based on the wrapped set operation in SELECT
     * Any set operation will be wrapped in a SELECT statement in the following format:
     * SELECT ... FROM <set operation> t(...) ...
     */

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        List<String> cols = new ArrayList<>();
        List<Boolean> dirs = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> xnodes = new ArrayList<>();
        String ctxId = this.qc.xNodeToDContext.get(this).refWithContext != null
                ? this.qc.xNodeToDContext.get(this).refWithContext.id
                : this.qc.xNodeToDContext.get(this).id;
        RelRecordType curRecordType = this.getRecordType();
        for (int i = 0; i < curRecordType.getFieldCount(); i++) {
            cols.add(ctxId + "_iid_" + i);
            dirs.add(false);
            types.add(curRecordType.getFieldList().get(i).getType().getSqlTypeName().getName());
            xnodes.add(null);
        }

        cols.add(String.format("%s_iid_%s", ctxId, curRecordType.getFieldCount()));
        dirs.add(false);
        types.add("BIGINT");
        xnodes.add(null);
        return new IID(cols, types, dirs, xnodes, ctxId);
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        List<String> cols = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<Integer> inputIndex = new ArrayList<>();
        List<Integer> inputColIndex = new ArrayList<>();
        String ctxId = this.qc.xNodeToDContext.get(this).refWithContext != null
                ? this.qc.xNodeToDContext.get(this).refWithContext.id
                : this.qc.xNodeToDContext.get(this).id;
        inputIndex.add(0);
        inputColIndex.add(-1);
        cols.add(String.format("ROW(%s)", String.join(", ",
                IntStream.range(0, this.getRecordType().getFieldList().size()).mapToObj(i -> ctxId + "_iid_" + i).collect(Collectors.toList()))));
        types.add("ANY");
        return new Sargable(cols, types, inputIndex, inputColIndex);
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("operator", this.sqlSetOperator.toString());
        json.add("left", this.left.toJsonObject());
        json.add("right", this.right.toJsonObject());
        return json;
    }
    // @Override protected OutputOrderSpec getFullOutputOrderImpl() throws AnalyzerException {
    // return this.left.getFullOutputOrder();
    // }
}

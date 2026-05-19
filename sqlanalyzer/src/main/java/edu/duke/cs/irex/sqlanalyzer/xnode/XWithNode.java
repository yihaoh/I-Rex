package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.validate.SqlValidatorScope;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XWithNode extends XTableValuedNode {
    public final List<XWithItemNode> withItems;
    public final XTableValuedNode body;

    public XWithNode(QueryContext qc, SqlWith sqlWith, XNode parent, SqlValidatorScope sqlScope)
            throws AnalyzerException {
        super(qc, sqlWith, parent, sqlScope);
        this.withItems = new ArrayList<>();
        for (SqlNode n : sqlWith.withList) {
            this.withItems.add(
                    (XWithItemNode) (qc.transform(n, this, qc.validator.getWithScope(n), false)));
        }
        this.body = (XTableValuedNode) (qc.transform(sqlWith.body, this, sqlScope, false));
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.add("with_items", toJsonArrayOfXNodes(this.withItems));
        json.add("body", this.body.toJsonObject());
        return json;
    }

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        return this.body.getIID();
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        return this.body.getSargable();
    }

    @Override
    protected Bloom getBloomImpl() throws AnalyzerException {
        return this.body.getBloom();
    }
}

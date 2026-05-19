package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import com.google.gson.JsonObject;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.validate.SqlValidatorNamespace;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public class XTableRefNode extends XTableValuedNode {
    public final String name;
    // resolvedNamespace is either a TableNamespace (if it is from in the database schema) or a
    // WithItemNamespace (if it is defined in some WITH).
    // Unfortunately these classes are not visible outside Calcite.
    public final SqlValidatorNamespace resolvedNamespace;
    public final boolean isInDatabase; // false if withItem is non-null
    public final XWithItemNode withItem; // null if isInDatabase

    public XTableRefNode(QueryContext qc, SqlIdentifier sqlIdentifier, XNode parent,
            SqlValidatorScope sqlScope) throws AnalyzerException {
        super(qc, sqlIdentifier, parent, sqlScope);
        this.name = sqlIdentifier.names.get(sqlIdentifier.names.size() - 1);
        this.resolvedNamespace = qc.validator.getNamespace(sqlIdentifier).resolve();
        if (this.resolvedNamespace.getTable() != null) {
            this.isInDatabase = true;
            this.withItem = null;
        } else {
            this.isInDatabase = false;
            this.withItem = (XWithItemNode) (qc.sqlToXNode.get(this.resolvedNamespace.getNode()));
        }
        return;
    }

    @Override
    public RelRecordType getRecordTypeFullyQualifed() throws AnalyzerException {
        if (this.parent instanceof XTableRenameNode) {
            return this.getRecordType();
        }
        List<RelDataTypeField> updatedFieldList = new ArrayList<>();
        RelRecordType oldRecordType = (RelRecordType) this.relRowType;
        for (int i = 0; i < oldRecordType.getFieldCount(); i++) {
            updatedFieldList.add(new RelDataTypeFieldImpl(
                    this.name + "." + oldRecordType.getFieldList().get(i).getName(), i,
                    oldRecordType.getFieldList().get(i).getType()));
        }
        return new RelRecordType(updatedFieldList);
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        json.addProperty("name", this.name);
        json.addProperty("in_database", this.isInDatabase);
        json.addProperty("XWithItemNode_id", (this.withItem == null) ? null : this.withItem.id);
        json.add("schema", Analyzer.toJsonTreeByDefault(this.getRecordType().getFieldNames()));
        return json;
    }

    @Override
    protected IID getIIDImpl() throws AnalyzerException {
        IID iid = null;
        String tableName =
                this.parent instanceof XTableRenameNode ? ((XTableRenameNode) this.parent).name
                        : this.name;
        if (this.isInDatabase) {
            // make sure the names get resolved correctly
            List<String> pkCols = this.qc.analyzer.getPKColumnNames(this.name).stream()
                    .map(x -> tableName + "." + x).collect(Collectors.toList());
            List<Boolean> dirs = new ArrayList<>(Collections.nCopies(pkCols.size(), Boolean.FALSE));
            List<String> types = new ArrayList<>();
            ArrayList<String> xnodes = new ArrayList<>(Collections.nCopies(pkCols.size(), null));
            for (Integer i : this.qc.analyzer.getPKColumnIndexes(this.name)) {
                if (i == -1) {
                    types.add("TID");
                } else {
                    types.add(this.getRecordType().getFieldList().get(i).getType().getSqlTypeName().getName());
                }
            }
            iid = new IID(pkCols, types, dirs, xnodes, null);
        } else { // must be WITH
            iid = this.withItem.query.getIID();
            for (int i = 0; i < iid.size(); i++) {
                iid.cols.set(i, this.name + ".iid_" + i);
            }
        }
        return iid;
    }

    @Override
    protected Sargable getSargableImpl() throws AnalyzerException {
        Sargable sarg = null;
        if (this.isInDatabase) {
            // make sure the names get resolved correctly
            String tableName =
                    this.parent instanceof XTableRenameNode ? ((XTableRenameNode) this.parent).name
                            : this.name;
            List<String> sargCols = this.qc.analyzer.getIdxColumnNames(name).stream()
                    .map(x -> tableName + "." + x).collect(Collectors.toList());
            List<Integer> sargColIndex = this.qc.analyzer.getIdxColumnIndexes(name);
            List<String> types = new ArrayList<>();
            for (Integer i : sargColIndex) {
                if (i == -1) {
                    types.add("TID");
                } else {
                    types.add(this.getRecordType().getFieldList().get(i).getType().getSqlTypeName().getName());
                }
            }
            sarg = sargCols.isEmpty() ? null : new Sargable(sargCols, types, new ArrayList<>(Collections.nCopies(sargCols.size(), 0)), sargColIndex);
        } else { // must be WITH
            sarg = this.withItem.query.getSargable();
        }
        return sarg;
    }
}

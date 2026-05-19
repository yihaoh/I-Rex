package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.sql.JoinType;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlValidatorNamespace;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Pair;
import edu.duke.cs.irex.sqlanalyzer.Analyzer;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.NestedArrayList;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public abstract class XTableValuedNode extends XNode {
    public final RelDataType relRowType; // for XJoinNode, this may be a RelCrossType

    public XTableValuedNode(QueryContext qc, SqlNode sqlNode, XNode parent,
            SqlValidatorScope sqlScope) {
        super(qc, sqlNode, parent, sqlScope);
        SqlValidatorNamespace ns = qc.validator.getNamespace(sqlNode);
        this.relRowType = ns.getRowType();
        return;
    }

    @Override
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = super.toJsonObject();
        try {
            json.addProperty("record_type", this.getRecordType().toString());
        }
        catch (AnalyzerException e) {
            // ignore
        }
        this.checkStage(2);
        json.add("internal_consistent_sort_order",
                Analyzer.toJsonTreeByDefault(this.getIID()));
        return json;
    }

    public RelRecordType getRecordType() throws AnalyzerException {
        if (this.relRowType instanceof RelRecordType) {
            return (RelRecordType) (this.relRowType);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Get the {@link RelRecordType} of a table with fully qualifed column names. This method is
     * overriden only for {@link XTableRefNode} and {@link XTableRenameNode}. Otherwise it is the same
     * as {@code getrRecordType()}.
     */
    public RelRecordType getRecordTypeFullyQualifed() throws AnalyzerException {
        return this.getRecordType();
    }

    public RelRecordType getRecordTypeWithIID() throws AnalyzerException {
        List<RelDataTypeField> updatedFieldList = new ArrayList<>();
        int offset = 0;
        RelRecordType oldRecordType = this.getRecordType();
        for (int i = 0; i < oldRecordType.getFieldCount(); i++) {
            updatedFieldList.add(new RelDataTypeFieldImpl(
                    oldRecordType.getFieldList().get(i).getName(), offset++,
                    oldRecordType.getFieldList().get(i).getType()));
        }
        updatedFieldList.add(new RelDataTypeFieldImpl("IID", offset++, this.qc.analyzer.createSqlType(SqlTypeName.get("ANY"))));
        RelRecordType updatedRecordType = new RelRecordType(updatedFieldList);
        return updatedRecordType;
    }

    public List<String> getCommaSeparatedColumnNames() throws AnalyzerException {
        return this.getRecordType().getFieldNames().stream().collect(Collectors.toList());
    }

    public String getCommaSeparatedColumnNamesString() throws AnalyzerException {
        return String.join(", ", this.getCommaSeparatedColumnNames());
    }

    public XTableValuedNode withoutRename() {
        if (this instanceof XTableRenameNode) {
            return (XTableValuedNode) (this.children.get(0));
        } else {
            return this;
        }
    }

    /*
     * Here we define the filter class (IID, Sargable, Bloom)
     */

    public static abstract class Filter extends Object {
        @SerializedName(value = "exprs")
        public final List<String> cols; // alias or expression within the context
        @SerializedName(value = "types")
        public final List<String> types;

        Filter(List<String> cols, List<String> types) {
            this.cols = cols;
            this.types = types;
        }

        public int size() {
            return cols.size();
        }
    }

    public static class IID extends Filter {
        @SerializedName(value = "exprs_structure")
        public final NestedArrayList structuredCols;
        @SerializedName(value = "exprs_orders")
        public final List<Boolean> dirs; // false means ASC (SQL default) and true means DESC
        @SerializedName(value = "exprs_xnodes")
        public final List<String> xnodes;
        @SerializedName(value = "context_id")
        public final String ctxId; // non-null only for output table, otherwise null

        public IID(List<String> cols, List<String> types, List<Boolean> dirs, List<String> xnodes, String ctxId) {
            super(cols, types);
            this.dirs = dirs;
            this.xnodes = xnodes;
            this.structuredCols = new NestedArrayList(IntStream.range(0, cols.size()).boxed().collect(Collectors.toList()));
            this.ctxId = ctxId;
            return;
        }

        public IID(List<String> cols, List<String> types, List<Boolean> dirs, List<String> xnodes,
                NestedArrayList structuredCols, String ctxId) {
            super(cols, types);
            this.dirs = dirs;
            this.xnodes = xnodes;
            this.structuredCols = structuredCols;
            this.ctxId = ctxId;
            return;
        }

        /**
         * Combine two IID into one.
         * 
         * @param iid - the {@code IID} to be combined with
         * @param struct - struct code to tell how two {@code IID} should be combined
         * @param setCtxId - set the CtxId of this {@code IID}, should be {@code null} most of time, except
         *        for generating IID for {@code XSelectNode}
         * @return {@code IID} - a new combined {@code IID}
         * @throws AnalyzerException
         */
        public IID combineIID(IID iid, int struct, String setCtxId) throws AnalyzerException {
            List<String> newCols = Stream.concat(this.cols.stream(), iid.cols.stream())
                    .collect(Collectors.toList());
            List<String> newTypes = Stream.concat(this.types.stream(), iid.types.stream())
                    .collect(Collectors.toList());
            List<Boolean> newDirs = Stream.concat(this.dirs.stream(), iid.dirs.stream())
                    .collect(Collectors.toList());
            List<String> newXnodes = Stream.concat(this.xnodes.stream(), iid.xnodes.stream())
                    .collect(Collectors.toList());
            NestedArrayList newStructuredCols = null;
            int offset = this.size();
            NestedArrayList.addOffset(iid.structuredCols, offset);
            switch (struct) {
                case 0:
                    // t1 JOIN t2
                    newStructuredCols = this.structuredCols.concat(iid.structuredCols);
                    break;
                case 1:
                    // t1 JOIN (t2 join t3)
                    NestedArrayList left = new NestedArrayList();
                    left.add(this.structuredCols);
                    newStructuredCols = left.merge(iid.structuredCols);
                    break;
                case 2:
                    // (t1 JOIN t2) join t3
                    NestedArrayList right = new NestedArrayList();
                    right.add(iid.structuredCols);
                    newStructuredCols = this.structuredCols.merge(right);
                    break;
                case 3:
                    // (t1 join t2) JOIN (t2 join t3)
                    newStructuredCols = this.structuredCols.merge(iid.structuredCols);
                    break;
            }
            return new IID(newCols, newTypes, newDirs, newXnodes, newStructuredCols, setCtxId);
        }

        public IID combineIIDForJoins(IID iid, int struct, XJoinNode xJoin) throws AnalyzerException {
            List<String> newThisCols = this.ctxId == null ? this.cols.stream().collect(Collectors.toList())
                    : IntStream.range(0, this.size())
                            .mapToObj(i -> this.ctxId != null ? this.ctxId + "_iid_" + i : "iid_" + i)
                            .collect(Collectors.toList());
            List<String> newThatCols = iid.ctxId == null ? iid.cols.stream().collect(Collectors.toList())
                    : IntStream.range(0, iid.size())
                            .mapToObj(i -> iid.ctxId != null ? iid.ctxId + "_iid_" + i : "iid_" + i)
                            .collect(Collectors.toList());
            List<String> newCols = null;
            List<String> newTypes = null;
            List<Boolean> newDirs = null;
            List<String> newXnodes = null;
            NestedArrayList newStructuredCols = null;

            boolean rightTableIsDerived = (xJoin.right.withoutRename() instanceof XTableRefNode && !((XTableRefNode) xJoin.right.withoutRename()).isInDatabase)
                    || (xJoin.right.withoutRename() instanceof XSelectNode);
            boolean leftTableIsDerived = (xJoin.left.withoutRename() instanceof XTableRefNode && !((XTableRefNode) xJoin.left.withoutRename()).isInDatabase)
                    || (xJoin.left.withoutRename() instanceof XSelectNode);

            if (xJoin.joinType == JoinType.LEFT || xJoin.joinType == JoinType.FULL) {
                // left outer join, need to know if right tuple is NULL tuple or NULL padding
                if (rightTableIsDerived) {
                    this.cols.add(0, String.format("%s IS NULL", iid.ctxId + "_seq"));
                } else {
                    this.cols.add(0, String.format("ROW(%s) IS NULL", String.join(",", newThatCols)));
                }
                this.types.add(0, "BOOLEAN");
                this.dirs.add(0, false);
                this.xnodes.add(null);
                NestedArrayList.addOffset(this.structuredCols, 1);
                this.structuredCols.add(0, 0);
            } else if (xJoin.joinType == JoinType.RIGHT) {
                // right outer join, need to know if left tuple is NULL tuple or NULL padding
                if (leftTableIsDerived) {
                    this.cols.add(0, String.format("%s IS NULL", this.ctxId + "_seq"));
                } else {
                    this.cols.add(0, String.format("ROW(%s) IS NULL", String.join(",", newThisCols)));
                }
                this.types.add(0, "BOOLEAN");
                this.dirs.add(0, false);
                this.xnodes.add(null);
                NestedArrayList.addOffset(this.structuredCols, 1);
                this.structuredCols.add(0, 0);
            }
            if (xJoin.joinType == JoinType.FULL) {
                // full outer join, need to know if tuples from both sides are NULL tuples or NULL padding
                if (leftTableIsDerived) {
                    this.cols.add(String.format("%s IS NULL", this.ctxId + "_seq"));
                } else {
                    this.cols.add(String.format("ROW(%s) IS NULL", String.join(",", newThisCols)));
                }
                this.types.add("BOOLEAN");
                this.dirs.add(false);
                this.xnodes.add(null);
                this.structuredCols.add(this.size());
            }

            newCols = Stream.concat(this.cols.stream(), iid.cols.stream()).collect(Collectors.toList());
            newTypes = Stream.concat(this.types.stream(), iid.types.stream()).collect(Collectors.toList());
            newDirs = Stream.concat(this.dirs.stream(), iid.dirs.stream()).collect(Collectors.toList());
            newXnodes = Stream.concat(this.xnodes.stream(), iid.xnodes.stream()).collect(Collectors.toList());
            NestedArrayList.addOffset(iid.structuredCols, this.size());

            switch (struct) {
                case 0:
                    // t1 JOIN t2
                    newStructuredCols = this.structuredCols.concat(iid.structuredCols);
                    break;
                case 1:
                    // t1 JOIN (t2 join t3)
                    NestedArrayList left = new NestedArrayList();
                    left.add(this.structuredCols);
                    newStructuredCols = left.merge(iid.structuredCols);
                    break;
                case 2:
                    // (t1 JOIN t2) join t3
                    NestedArrayList right = new NestedArrayList();
                    right.add(iid.structuredCols);
                    newStructuredCols = this.structuredCols.merge(right);
                    break;
                case 3:
                    // (t1 join t2) JOIN (t2 join t3)
                    newStructuredCols = this.structuredCols.merge(iid.structuredCols);
                    break;
            }
            // System.out.println(newCols);
            return new IID(newCols, newTypes, newDirs, newXnodes, newStructuredCols, this.ctxId);
        }

        public List<String> toOrderByList() {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < this.cols.size(); i++) {
                if (this.dirs.get(i)) {
                    out.add(this.cols.get(i) + " desc");
                } else {
                    out.add(this.cols.get(i));
                }
            }
            return out;
        }

        public String toOrderByString() {
            return String.join(", ", this.toOrderByList());
        }

        public String toSelectString() {
            return String.join(", ", this.cols);
        }

        public String toOuterOrderByString() {
            if (this.ctxId == null) {
                return this.toOrderByString();
            }
            return String.join(", ", IntStream.range(0, this.size())
                    .mapToObj(i -> this.ctxId + "_iid_" + i)
                    .collect(Collectors.toList()));
        }

        public String toOuterSelectString(boolean withSeq) {
            if (this.ctxId == null) {
                return this.toSelectString();
            }
            return (withSeq ? this.ctxId + "_seq, " : "") +
                    String.join(", ", IntStream.range(0, this.size())
                            .mapToObj(i -> this.ctxId + "_iid_" + i)
                            .collect(Collectors.toList()));
        }

        public String toWithSchemaAlias() {
            List<String> exprs = IntStream.range(0, this.size())
                    .mapToObj(i -> this.ctxId != null ? this.ctxId + "_iid_" + i : "iid_" + i)
                    .collect(Collectors.toList());
            exprs.add(this.ctxId + "_seq");
            return String.join(", ", exprs);
        }

        public String toSelectStringWithIIDAlias(boolean withSeq) {
            List<String> exprs = IntStream.range(0, this.size())
                    .mapToObj(i -> this.ctxId != null ? this.cols.get(i) + String.format(" AS %s_iid_%d", this.ctxId, i)
                            : this.cols.get(i) + " AS iid_" + i)
                    .collect(Collectors.toList());
            if (withSeq) {
                exprs.add(String.format("ROW_NUMBER() OVER (ORDER BY %s) AS %s_seq", this.toOrderByString(), this.ctxId));
            }
            return String.join(", ", exprs);
        }

        public List<String> convertColsToAlias() {
            return IntStream.range(0, this.size())
                    .mapToObj(i -> this.ctxId != null ? this.ctxId + "_iid_" + i : "iid_" + i)
                    .collect(Collectors.toList());
        }
    }

    final public IID getIID() throws AnalyzerException {
        this.checkStage(2);
        return this.getIIDImpl();
    }

    protected IID getIIDImpl() throws AnalyzerException {
        // Should always override this function, do not rely on the parent implementation!
        // dynamic dispatch the correct getIIDImpl
        if (this instanceof XTableRefNode) {
            return ((XTableRefNode) this).getIID();
        } else if (this instanceof XJoinNode) {
            return ((XJoinNode) this).getIID();
        } else if (this instanceof XTableRenameNode) {
            return ((XTableRenameNode) this).getIID();
        } else if (this instanceof XSelectNode) {
            return ((XSelectNode) this).getIID();
        } else if (this instanceof XWithItemNode) {
            return ((XWithItemNode) this).query.getIID();
        } else if (this instanceof XWithNode) {
            return ((XWithNode) this).body.getIID();
        } else if (this instanceof XSetOpNode) {
            return ((XSetOpNode) this).getIID();
        } else {
            throw new UnsupportedOperationException("Unhandled XTableValuedNode for getIIDImpl.");
        }
    }

    public static class Sargable extends Filter {
        @SerializedName(value = "table_index")
        public final List<Integer> inputIndex;
        @SerializedName(value = "col_index")
        public final List<Integer> inputColIndex; // if IID, then index is -1, otherwise index is the column index in the input table
        @SerializedName(value = "xnode")
        public final String xnodeID; // if IID as sargable, the root XNode id of that context, otherwise null

        public Sargable(List<String> cols, List<String> types, List<Integer> inputIndex,
                List<Integer> inputColIndex, String xnodeId) {
            super(cols, types);
            this.inputIndex = inputIndex;
            this.inputColIndex = inputColIndex;
            this.xnodeID = xnodeId;
            return;
        }

        public Sargable(List<String> cols, List<String> types, List<Integer> inputIndex,
                List<Integer> inputColIndex) {
            super(cols, types);
            this.inputIndex = inputIndex;
            this.inputColIndex = inputColIndex;
            this.xnodeID = null;
            return;
        }

        public Sargable combineSargable(Sargable sarg) {
            List<String> newCols = Stream.concat(this.cols.stream(), sarg.cols.stream())
                    .collect(Collectors.toList());
            List<String> newTypes = Stream.concat(this.types.stream(), sarg.types.stream())
                    .collect(Collectors.toList());
            int offset = this.inputIndex.get(this.size() - 1) + 1;
            List<Integer> newInputIndex = Stream.concat(this.inputIndex.stream(),
                    sarg.inputIndex.stream().map(i -> i + offset))
                    .collect(Collectors.toList());
            List<Integer> newInputColIndex = Stream.concat(this.inputColIndex.stream(), sarg.inputColIndex.stream())
                    .collect(Collectors.toList());
            return new Sargable(newCols, newTypes, newInputIndex, newInputColIndex);
        }
    }

    final public Sargable getSargable() throws AnalyzerException {
        this.checkStage(2);
        return this.getSargableImpl();
    }

    protected Sargable getSargableImpl() throws AnalyzerException {
        // Should always override this function, do not rely on the parent implementation!
        // dynamic dispatch the correct getSargableImpl
        if (this instanceof XTableRefNode) {
            return ((XTableRefNode) this).getSargable();
        } else if (this instanceof XJoinNode) {
            return ((XJoinNode) this).getSargable();
        } else if (this instanceof XTableRenameNode) {
            return ((XTableRenameNode) this).getSargable();
        } else if (this instanceof XSelectNode) {
            return ((XSelectNode) this).getSargable();
        } else if (this instanceof XWithItemNode) {
            return ((XWithItemNode) this).query.getSargable();
        } else if (this instanceof XWithNode) {
            return ((XWithNode) this).body.getSargable();
        } else if (this instanceof XSetOpNode) {
            return ((XSetOpNode) this).getSargable();
        } else {
            throw new UnsupportedOperationException("Unhandled XTableValuedNode for getSargableImpl.");
        }
    }

    public static class Bloom extends Filter {
        public Bloom(List<String> cols, List<String> types) {
            super(new ArrayList<>(), new ArrayList<>());
            List<Pair<String, String>> tmp = IntStream.range(0, cols.size())
                    .mapToObj(i -> new Pair<>(cols.get(i), types.get(i)))
                    .collect(Collectors.toList());
            tmp.sort(Comparator.comparing(Pair::getValue));
            List<String> sortedCols = tmp.stream().map(x -> x.left).collect(Collectors.toList());
            List<String> sortedTypes = tmp.stream().map(x -> x.right).collect(Collectors.toList());
            this.cols.addAll(sortedCols);
            this.types.addAll(sortedTypes);
        }

        public Bloom(List<XColumnRefNode> colRefs) {
            super(new ArrayList<>(), new ArrayList<>());
            List<Pair<String, String>> tmp = colRefs.stream()
                    .map(x -> new Pair<>(x.toString(), x.relDataType.getSqlTypeName().getName()))
                    .collect(Collectors.toList());
            tmp.sort(Comparator.comparing(Pair::getValue));
            List<String> sortedCols = tmp.stream().map(x -> x.left).collect(Collectors.toList());
            List<String> sortedTypes = tmp.stream().map(x -> x.right).collect(Collectors.toList());
            this.cols.addAll(sortedCols);
            this.types.addAll(sortedTypes);
        }

        public String toBloomExpr() throws AnalyzerException {
            String blmflExpr = "BLMFL(";
            Set<String> numTypes = new HashSet<>(Arrays.asList("TINYINT", "SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "FLOAT", "DOUBLE"));
            Set<String> strTypes = new HashSet<>(Arrays.asList("CHAR", "VARCHAR", "BINARY", "VARBINARY"));
            Set<String> dateTypes = new HashSet<>(Arrays.asList("DATE", "DATETIME", "TIMESTAMP"));
            for (int i = 0; i < this.size(); i++) {
                if (i > 0) {
                    blmflExpr += ", ";
                }
                if (numTypes.contains(this.types.get(i))) {
                    blmflExpr += String.format("numeric_send(%s)", this.cols.get(i));
                } else if (strTypes.contains(this.types.get(i))) {
                    blmflExpr += String.format("textsend(%s)", this.cols.get(i));
                } else if (dateTypes.contains(this.types.get(i))) {
                    blmflExpr += String.format("date_send(%s)", this.cols.get(i));
                } else {
                    blmflExpr += String.format("textsend(%s::text)", this.cols.get(i));
                }
            }
            blmflExpr += ")";
            return blmflExpr;
        }
    }

    final public Bloom getBloom() throws AnalyzerException {
        this.checkStage(2);
        return this.getBloomImpl();
    }

    protected Bloom getBloomImpl() throws AnalyzerException {
        // Should always override this function, do not rely on the parent implementation!
        // dynamic dispatch the correct getSargableImpl
        if (this instanceof XTableRenameNode) {
            return ((XTableRenameNode) this).withoutRename().getBloom();
        } else if (this instanceof XSelectNode) {
            return ((XSelectNode) this).getBloom();
        } else if (this instanceof XWithItemNode) {
            return ((XWithItemNode) this).query.getBloom();
        } else if (this instanceof XWithNode) {
            return ((XWithNode) this).body.getBloom();
        } else {
            // Don't handle: XJoinNode, XTableRefNode, Bloom is at the block level
            // also don't handle XSetOpNode as it does not have Bloom
            throw new UnsupportedOperationException("Unhandled XTableValuedNode for getBloomImpl.");
        }
    }
}

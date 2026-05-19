package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.util.Pair;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSelectNode;

public class DSelectContextCode extends DContext.DContextCode {
    /*
     * Milestones, table content and their corresponding SQL. One for each input
     * table and all
     * downstream tables.
     */
    public final List<ParameterizedSQL> milestoneSQLs;
    public final List<DContextMilestone> milestones;
    public final List<ParameterizedSQL> pageSQLs;
    public final List<ParameterizedSQL> pinSubspaceMilestoneSQLs;
    public final List<ParameterizedSQL> pinSubspacePageSQLs;
    public final List<TableContent> tables;

    /*
     * Stage index to the {@code tables, milestoneSQLs, milestones, pageSQLs}. Note
     * that {@code
     * tJoinFilter} is the same as the number of input tables by convention. If a
     * stage does not exist,
     * its corresponding index will be -1.
     */
    public final int tJoinFilter;
    public final int tExpandGroup;
    public final int tGroup;
    public final int tPreDistinct;
    public final int tOutput;

    /**
     * {@code tablesHooks.get(t).get(j)} returns a list of {@code XCellValuedNode}
     * ids whose values are
     * found in the {@code j}-th column in the result returned by
     * {@code pageSQLs.get(t)}. For now, each
     * list contains only one id (we might optimize it later by adding duplicates).
     */
    public final List<List<Set<String>>> tableHooks;

    /**
     * A list of {@code XCellValuedNode} ids. The {@code i}-th expression evaluation
     * in the list is
     * returned as the {@code i}-th result of the {@onWhereEvalSQL}.
     */
    public final List<String> onWhereEvalHooks;
    public final ParameterizedSQL onWhereEvalSQL;

    /**
     * Column references that are external to the current context.
     */
    public final Set<ParameterizedSQL.SerializedColumnRef> expectedBindings;

    public DSelectContextCode(DSelectContext context) throws AnalyzerException {
        XSelectNode xSelect = (XSelectNode) (context.xnode);
        this.tables = new ArrayList<>();
        this.milestones = context.milestones;
        this.milestoneSQLs = new ArrayList<>();
        this.pageSQLs = new ArrayList<>();
        this.pinSubspaceMilestoneSQLs = new ArrayList<>();
        this.pinSubspacePageSQLs = new ArrayList<>();
        this.tableHooks = new ArrayList<>();
        this.tJoinFilter = context.tJoinFilter;
        this.tExpandGroup = context.tExpandGroup;
        this.tGroup = context.tGroup;
        this.tPreDistinct = context.tPreDistinct;
        this.tOutput = context.tOutput;

        // genereate milestone queries and figure out table indexes
        for (int i = 0; i < xSelect.inputTables.size(); i++) {
            this.milestoneSQLs.add(context.genInputMilestoneSQL(i));
            this.pageSQLs.add(context.genInputPageSQL(i));
            this.pinSubspaceMilestoneSQLs.add(context.genInputPinSubspaceMilestoneSQL(i));
            this.pinSubspacePageSQLs.add(context.genInputPinSubspacePageSQL(i));
            RelRecordType intputRecord = xSelect.inputTables.get(i).getRecordTypeWithIID();
            List<TableContent.Usage> usages = new ArrayList<>(Collections.nCopies(intputRecord.getFieldCount() - 1, TableContent.Usage.DISPLAY));
            usages.add(TableContent.Usage.IID);
            this.tables.add(new TableContent(intputRecord, usages));
            int numColumns = xSelect.inputTables.get(i).getRecordType().getFieldCount();
            this.tableHooks.add(Stream.generate(() -> new HashSet<String>()).limit(numColumns + 1).collect(Collectors.toList())); // by convention
        }

        if (this.tJoinFilter != -1) {
            this.milestoneSQLs.add(context.genJoinFilterMilestoneSQL(false));
            Pair<ParameterizedSQL, List<Set<String>>> tp = context.genJoinFilterPageSQL(false);
            this.pageSQLs.add(tp.left);
            this.tableHooks.add(tp.right);
            this.pinSubspaceMilestoneSQLs.add(context.genJoinFilterMilestoneSQL(true));
            this.pinSubspacePageSQLs.add(context.genJoinFilterPageSQL(true).left);
            Pair<RelRecordType, List<TableContent.Usage>> joinFilterRecord = context.genJoinFilterRecordType();
            this.tables.add(new TableContent(joinFilterRecord.left, joinFilterRecord.right));
        }

        if (this.tGroup != -1) {
            this.milestoneSQLs.add(context.genExpandGroupMilestoneSQL(false));
            Pair<ParameterizedSQL, List<Set<String>>> genExpandGroup = context.genExpandGroupPageSQL(false);
            this.pageSQLs.add(genExpandGroup.left);
            this.tableHooks.add(genExpandGroup.right);
            this.pinSubspaceMilestoneSQLs.add(context.genExpandGroupMilestoneSQL(true));
            this.pinSubspacePageSQLs.add(context.genExpandGroupPageSQL(true).left);
            Pair<RelRecordType, List<TableContent.Usage>> expandGroupRecord = context.genExpandGroupRecordType();
            this.tables.add(new TableContent(expandGroupRecord.left, expandGroupRecord.right));

            this.milestoneSQLs.add(context.genGroupMilestoneSQL(false));
            Pair<ParameterizedSQL, List<Set<String>>> genGroup = context.genGroupPageSQL(false);
            this.pageSQLs.add(genGroup.left);
            this.tableHooks.add(genGroup.right);
            this.pinSubspaceMilestoneSQLs.add(context.genGroupMilestoneSQL(true));
            this.pinSubspacePageSQLs.add(context.genGroupPageSQL(true).left);
            Pair<RelRecordType, List<TableContent.Usage>> groupRecord = context.genGroupRecordType();
            this.tables.add(new TableContent(groupRecord.left, groupRecord.right));
        }

        if (this.tPreDistinct != -1) {
            this.milestoneSQLs.add(context.genPreDistinctMilestoneSQL(false));
            Pair<ParameterizedSQL, List<Set<String>>> genPreDistinct = context.genPreDistinctPageSQL(false);
            this.pageSQLs.add(genPreDistinct.left);
            this.tableHooks.add(genPreDistinct.right);
            this.pinSubspaceMilestoneSQLs.add(context.genPreDistinctMilestoneSQL(true));
            this.pinSubspacePageSQLs.add(context.genPreDistinctPageSQL(true).left);
            Pair<RelRecordType, List<TableContent.Usage>> preDistinctRecord = context.genPreDistinctRecordType();
            this.tables.add(new TableContent(preDistinctRecord.left, preDistinctRecord.right));
        }

        this.milestoneSQLs.add(context.genOutputMilestoneSQL(false));
        Pair<ParameterizedSQL, List<Set<String>>> genLast = context.genOutputPageSQL(false);
        this.pageSQLs.add(genLast.left);
        this.tableHooks.add(genLast.right);
        this.pinSubspaceMilestoneSQLs.add(context.genOutputMilestoneSQL(true));
        this.pinSubspacePageSQLs.add(context.genOutputPageSQL(true).left);
        Pair<RelRecordType, List<TableContent.Usage>> outputRecord = context.genOutputRecordType();
        this.tables.add(new TableContent(outputRecord.left, outputRecord.right));

        if (xSelect.onExprs != null || xSelect.whereExpr != null) {
            Pair<ParameterizedSQL, List<String>> evalSQL = context.genOnWhereEvalSQL();
            this.onWhereEvalSQL = evalSQL.left;
            this.onWhereEvalHooks = evalSQL.right;
        } else {
            this.onWhereEvalSQL = null;
            this.onWhereEvalHooks = null;
        }

        // Collect all bindings needed:
        this.expectedBindings = new HashSet<>();
        Stream.of(this.milestoneSQLs).flatMap(Collection::stream).forEach(sql -> {
            this.expectedBindings.addAll(Arrays.asList(sql.columnRefs));
        });

    }

    // @Override
    // public JsonObject toJsonObject() {
    // JsonObject json = new JsonObject();
    // json.add("tables", Analyzer.toJsonTreeByDefault(this.tables));
    // json.add("milestoneSQLs", Analyzer.toJsonTreeByDefault(this.milestoneSQLs));
    // return json;
    // }

}

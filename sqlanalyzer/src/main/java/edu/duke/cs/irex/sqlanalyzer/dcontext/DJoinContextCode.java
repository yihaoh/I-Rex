package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.calcite.rel.type.RelRecordType;
import org.apache.calcite.util.Pair;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XJoinNode;

public class DJoinContextCode extends DContext.DContextCode {
    /*
     * Milestones, table content and their corresponding SQL. One for each input table and all
     * downstream tables.
     */
    public final List<ParameterizedSQL> milestoneSQLs;
    public final List<DContextMilestone> milestones;
    public final List<ParameterizedSQL> pageSQLs;
    public final List<ParameterizedSQL> pinSubspaceMilestoneSQLs;
    public final List<ParameterizedSQL> pinSubspacePageSQLs;
    public final List<TableContent> tables;
    public final List<String> onEvalHooks;
    public final ParameterizedSQL onEvalSQL;

    /**
     * Column references that are external to the current context.
     */
    public final Set<ParameterizedSQL.SerializedColumnRef> expectedBindings;

    public DJoinContextCode(DJoinContext context) throws AnalyzerException {
        this.tables = new ArrayList<>();
        this.milestones = context.milestones;
        this.milestoneSQLs = new ArrayList<>();
        this.pageSQLs = new ArrayList<>();
        this.pinSubspaceMilestoneSQLs = new ArrayList<>();
        this.pinSubspacePageSQLs = new ArrayList<>();

        // input tables
        for (int i = 0; i < context.inputXNodes.size(); i++) {
            this.milestoneSQLs.add(context.genInputMilestoneSQL(i));
            this.pageSQLs.add(context.genInputPageSQL(i));
            this.pinSubspaceMilestoneSQLs.add(context.genInputPinSubspaceMilestoneSQL(i));
            this.pinSubspacePageSQLs.add(context.genInputPinSubspacePageSQL(i));
            Pair<RelRecordType, List<TableContent.Usage>> inputRecord = context.genInputRecordType(i);
            this.tables.add(new TableContent(inputRecord.left, inputRecord.right));
        }

        // output table
        this.milestoneSQLs.add(context.genOutputMilestoneSQL(false));
        this.pageSQLs.add(context.genOutputPageSQL(false));
        this.pinSubspaceMilestoneSQLs.add(context.genOutputMilestoneSQL(true));
        this.pinSubspacePageSQLs.add(context.genOutputPageSQL(true));
        Pair<RelRecordType, List<TableContent.Usage>> outputRecord = context.genOutputRecordType();
        this.tables.add(new TableContent(outputRecord.left, outputRecord.right));

        // on expr evaluations
        if (((XJoinNode) context.xnode).condition != null) {
            Pair<ParameterizedSQL, List<String>> evalSQL = context.genOnEvalSQL();
            this.onEvalSQL = evalSQL.left;
            this.onEvalHooks = evalSQL.right;
        } else {
            this.onEvalSQL = null;
            this.onEvalHooks = null;
        }

        this.expectedBindings = new HashSet<>();
        Stream.of(this.milestoneSQLs).flatMap(Collection::stream).filter(Objects::nonNull).forEach(sql -> {
            this.expectedBindings.addAll(Arrays.asList(sql.columnRefs));
        });

    }
}

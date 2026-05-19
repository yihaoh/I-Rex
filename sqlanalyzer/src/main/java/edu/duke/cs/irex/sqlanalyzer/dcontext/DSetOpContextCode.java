package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.ParameterizedSQL;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XSetOpNode;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode;

public class DSetOpContextCode extends DContext.DContextCode {
    /*
     * Milestones, table content and their corresponding SQL. One for each input
     * table and all
     * downstream tables.
     */
    public final List<ParameterizedSQL> milestoneSQLs;
    public final List<DContextMilestone> milestones;
    public final List<ParameterizedSQL> pageSQLs;
    public final List<TableContent> tables;
    public final Set<ParameterizedSQL.SerializedColumnRef> expectedBindings;

    public DSetOpContextCode(DSetOpContext context) throws AnalyzerException {
        XSetOpNode xSetOp = (XSetOpNode) (context.xnode);
        this.milestoneSQLs = new ArrayList<>();
        this.milestones = context.milestones;
        this.tables = new ArrayList<>();
        this.pageSQLs = new ArrayList<>();
        // Handle the input tables:
        XTableValuedNode[] inputTables = { xSetOp.left, xSetOp.right };
        for (int i = 0; i < inputTables.length; i++) {
            this.tables.add(new TableContent(inputTables[i].getRecordType(),
                    new ArrayList<>(Collections.nCopies(xSetOp.getRecordType().getFieldCount(), TableContent.Usage.DISPLAY))));
            this.milestoneSQLs.add(context.genInputMilestoneSQL(inputTables[i]));
            this.pageSQLs.add(context.genInputPageSQL(inputTables[i]));
        }

        // left intermediate table
        this.tables.add(new TableContent(context.genIntermediateRecordType().left, context.genIntermediateRecordType().right));
        this.milestoneSQLs.add(context.genIntermediateMilestoneSQL(xSetOp.left));
        this.pageSQLs.add(context.genIntermediatePageSQL(xSetOp.left));

        // right intermediate table
        this.tables.add(new TableContent(context.genIntermediateRecordType().left, context.genIntermediateRecordType().right));
        this.milestoneSQLs.add(context.genIntermediateMilestoneSQL(xSetOp.right));
        this.pageSQLs.add(context.genIntermediatePageSQL(xSetOp.right));

        // output table
        this.tables.add(new TableContent(context.genOutputRecordType().left, context.genOutputRecordType().right));
        this.milestoneSQLs.add(context.genOutputMilestoneSQL());
        this.pageSQLs.add(context.genOutputPageSQL());

        // Collect all bindings needed:
        this.expectedBindings = new HashSet<>();
        Stream.of(this.milestoneSQLs, this.pageSQLs).flatMap(Collection::stream)
                .forEach(sql -> {
                    this.expectedBindings.addAll(Arrays.asList(sql.columnRefs));
                });
    }
}

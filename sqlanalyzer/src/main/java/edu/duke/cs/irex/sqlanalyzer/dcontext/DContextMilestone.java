package edu.duke.cs.irex.sqlanalyzer.dcontext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.gson.annotations.SerializedName;
import edu.duke.cs.irex.sqlanalyzer.TableContent;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.Bloom;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.IID;
import edu.duke.cs.irex.sqlanalyzer.xnode.XTableValuedNode.Sargable;

public class DContextMilestone {
    @SerializedName(value = "context_id")
    public final String ctxID;
    @SerializedName(value = "milestone_index")
    public final int mstIndex;

    // @SerializedName(value = "column_names")
    // public final List<String> colNames;
    // @SerializedName(value = "column_types")
    // public final List<String> colTypes;
    @SerializedName(value = "iid")
    public final IID iid;
    @SerializedName(value = "sargable")
    public final Sargable sarg;
    @SerializedName(value = "bloom")
    public final Bloom blm;

    @SerializedName(value = "milestone_table")
    public final TableContent milestones;

    public DContextMilestone(String ctxID, int mstIndex, IID iid) {
        this.ctxID = ctxID;
        this.mstIndex = mstIndex;
        this.iid = iid;
        this.sarg = null;
        this.blm = null;
        // default
        List<String> cols = Arrays.asList("seq", "count", iid.cols.toString());
        List<String> types = Arrays.asList("BIGINT", "BIGINT", iid.types.toString());
        this.milestones = new TableContent(cols, types);
    }

    public DContextMilestone(String ctxID, int mstIndex, IID iid, Sargable sarg) {
        this.ctxID = ctxID;
        this.mstIndex = mstIndex;
        this.iid = iid;
        this.sarg = sarg;
        this.blm = null;
        // default
        List<String> cols = Arrays.asList("seq", "count", iid.cols.toString());
        List<String> types = Arrays.asList("BIGINT", "BIGINT", iid.types.toString());
        if (sarg != null) {
            cols = Stream.of(cols, sarg.cols).flatMap(List::stream)
                    .collect(Collectors.toList());
            types = Stream.of(types, sarg.types).flatMap(List::stream)
                    .collect(Collectors.toList());
        }
        this.milestones = new TableContent(cols, types);
    }

    public DContextMilestone(String ctxID, int mstIndex, IID iid, Sargable sarg, Bloom blm) {
        this.ctxID = ctxID;
        this.mstIndex = mstIndex;
        this.iid = iid;
        this.sarg = sarg;
        this.blm = blm;
        // default
        List<String> cols = Arrays.asList("seq", "count", iid.cols.toString());
        List<String> types = Arrays.asList("BIGINT", "BIGINT", iid.types.toString());
        cols = Stream.of(cols, sarg.cols, Arrays.asList(blm.toString()))
                .flatMap(List::stream)
                .collect(Collectors.toList());
        types = Stream.of(types, sarg.types, Arrays.asList(blm.toString()))
                .flatMap(List::stream)
                .collect(Collectors.toList());
        this.milestones = new TableContent(cols, types);
    }

    public List<String> getCTEAlias() {
        List<String> res = new ArrayList<>(Arrays.asList("iid"));
        if (this.sarg != null) {
            for (int i = 0; i < this.sarg.size(); i++) {
                res.add("sarg_" + i);
            }
        }
        if (this.blm != null) {
            for (int i = 0; i < this.blm.size(); i++) {
                res.add("blm_" + i);
            }
        }
        return res;
    }

    // public JsonObject toJsonObject() {
    // JsonObject json = new JsonObject();
    // json.addProperty("context_id", this.ctxID);
    // json.addProperty("milestone_index", this.mstIndex);
    // json.add("column_names", Analyzer.toJsonTreeByDefault(this.colNames));
    // json.add("column_types", Analyzer.toJsonTreeByDefault(this.colTypes));
    // json.add("milestone_table", Analyzer.toJsonTreeByDefault(milestones));
    // return json;
    // }
}

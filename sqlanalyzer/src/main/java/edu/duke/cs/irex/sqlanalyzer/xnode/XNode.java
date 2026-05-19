package edu.duke.cs.irex.sqlanalyzer.xnode;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Pair;

import edu.duke.cs.irex.sqlanalyzer.AnalyzerException;
import edu.duke.cs.irex.sqlanalyzer.QueryContext;

public abstract class XNode {
    public final QueryContext qc;
    public final String id;
    public final XNode parent;
    public final ArrayList<XNode> children;
    public final SqlNode sqlNode;
    public final SqlValidatorScope sqlScope;
    public XNode(QueryContext qc, SqlNode sqlNode, XNode parent, SqlValidatorScope sqlScope) {
        this.qc = qc;
        this.id = Integer.toHexString(System.identityHashCode(this));
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
        this.sqlNode = sqlNode;
        if (sqlNode != null) {
            qc.sqlToXNode.put(sqlNode, this);
        }
        this.sqlScope = sqlScope;
        this.children = new ArrayList<XNode>();
        return;
    }
    public JsonObject toJsonObject() throws AnalyzerException {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("type", this.getClass().getSimpleName());
        json.addProperty("parent", (this.parent == null)? null : this.parent.id);
        json.add("children", toJsonArrayOfXNodeIds(this.children));
        if (this.sqlNode != null) {
            JsonObject pos = new JsonObject();
            pos.addProperty("begin_line_num", this.sqlNode.getParserPosition().getLineNum());
            pos.addProperty("begin_column_num", this.sqlNode.getParserPosition().getColumnNum());
            pos.addProperty("end_line_num", this.sqlNode.getParserPosition().getEndLineNum());
            pos.addProperty("end_column_num", this.sqlNode.getParserPosition().getEndColumnNum());
            Pair<Integer, Integer> string_pos = sqlParserPosToStringPos(this.qc.query, this.sqlNode.getParserPosition());
            pos.addProperty("begin_str_index", string_pos.left);
            pos.addProperty("end_str_index_excl", string_pos.right);
            json.add("pos_in_original_query", pos);
        } else {
            json.add("pos_in_original_query", null);
        }
        return json;
    }
    public static JsonArray toJsonArrayOfXNodeIds(List<? extends XNode> xnodes) {
        if (xnodes == null) {
            return null;
        }
        JsonArray array = new JsonArray();
        for (XNode n : xnodes) {
            array.add(n.id);
        }
        return array;
    }
    public static JsonArray toJsonArrayOfXNodes(List<? extends XNode> xnodes) throws AnalyzerException {
        if (xnodes == null) {
            return null;
        }
        JsonArray array = new JsonArray();
        for (XNode n : xnodes) {
            array.add(n.toJsonObject());
        }
        return array;
    }
    public void checkStage(int minStage) throws AnalyzerException {
        if (this.qc.stage() < minStage) {
            throw new AnalyzerException(String.format("internal error: operation requires query context analysis stage %d", minStage));
        }
        return;
    }
    final public void refine() throws AnalyzerException {
        this.checkStage(1);
        this.refineImpl();
        return;
    }
    protected void refineImpl() throws AnalyzerException {
        for (XNode c : this.children) {
            c.refine();
        }
        return;
    }
    public boolean isDescendantOf(XNode n) {
        if (this == n) {
            return true;
        } else if (this.parent != null) {
            return this.parent.isDescendantOf(n);
        } else {
            return false;
        }
    }
    public static Pair<Integer, Integer> sqlParserPosToStringPos(String query, SqlParserPos pos) {
        int lineNum = 1;
        int columnNum = 1;
        int iStart = 0;
        int iEnd = query.length() - 1;
        for (int i = 0; i < query.length(); i++) {
            if (lineNum == pos.getLineNum() && columnNum == pos.getColumnNum()) {
                iStart = i;
            }
            if (lineNum == pos.getEndLineNum() && columnNum == pos.getEndColumnNum()) {
                iEnd = i;
                break;
            }
            if (query.charAt(i) == '\n') {
                lineNum++;
                columnNum = 1;
            } else {
                columnNum++;
            }
        }
        return new Pair<>(iStart, iEnd+1);
    }
    public static String substringAtSqlParserPos(String query, SqlParserPos pos) {
        Pair<Integer, Integer> stringPos = sqlParserPosToStringPos(query, pos);
        return query.substring(stringPos.left, stringPos.right);
    }
    public final String querySubstring() {
        return substringAtSqlParserPos(this.qc.query, this.sqlNode.getParserPosition());
    }
    public Pair<Integer, Integer> querySubstringPos() {
        return sqlParserPosToStringPos(this.qc.query, this.sqlNode.getParserPosition());
    }
}

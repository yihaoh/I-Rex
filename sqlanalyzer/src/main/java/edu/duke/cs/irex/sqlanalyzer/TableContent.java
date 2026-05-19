package edu.duke.cs.irex.sqlanalyzer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;

import org.apache.calcite.rel.type.RelRecordType;

public class TableContent {

    public enum Usage {
        IID, NEXT_IID, DISPLAY, EVAL, PREV_IID
    }

    @SerializedName(value = "column_names")
    public final List<String> columnNames;
    @SerializedName(value = "column_types")
    public final List<String> columnSQLTypes;
    @SerializedName(value = "column_use")
    public final List<Usage> columnUsage;
    @SerializedName(value = "contents")
    public final List<List<Object>> contents;

    public TableContent(TableContent other) {
        this.columnNames = List.copyOf(other.columnNames);
        this.columnSQLTypes = List.copyOf(other.columnSQLTypes);
        this.columnUsage = other.columnUsage == null ? null : List.copyOf(other.columnUsage);
        this.contents = new ArrayList<>();
        for (List<Object> row : other.contents) {
            this.contents.add(List.copyOf(row)); // shallow copy of objects within row
        }
        return;
    }

    public TableContent(RelRecordType rowType, List<Usage> usages) {
        this.columnNames = List.copyOf(rowType.getFieldNames());
        this.columnSQLTypes = new ArrayList<>(); //(rowType.getFieldList().stream().map(f -> f.getType().getSqlTypeName().getName()).collect(Collectors.toList()));
        // handle special type names, making it compliant with PostgreSQL
        for (int i = 0; i < rowType.getFieldCount(); i++) {
            String typeName = rowType.getFieldList().get(i).getType().getSqlTypeName().getName();
            if (typeName.equals("DOUBLE")) {
                typeName = "DOUBLE PRECISION";
            }
            this.columnSQLTypes.add(typeName);
        }
        this.columnUsage = usages;
        this.contents = new ArrayList<>();
        return;
    }

    public TableContent(List<String> columnNames, List<String> columnSQLTypes) {
        this.columnNames = columnNames;
        this.columnSQLTypes = new ArrayList<>();
        // handle special type names, making it compliant with PostgreSQL
        for (String typeName : columnSQLTypes) {
            if (typeName.equals("DOUBLE")) {
                typeName = "DOUBLE PRECISION";
            }
            this.columnSQLTypes.add(typeName);
        }
        this.columnUsage = null;
        this.contents = new ArrayList<>();
    }

    // public TableContent() {
    // // for input table milestones since they are pretty much the same
    // this.columnNames = new ArrayList<>(Arrays.asList("seq", "iid", "count"));
    // this.columnSQLTypes = new ArrayList<>(Arrays.asList("BIGINT", "IID", "BIGINT"));
    // this.contents = new ArrayList<>();
    // }

    public void addRow(ResultSet rs) throws AnalyzerException {
        try {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < this.columnNames.size(); i++) {
                row.add(rs.getObject(i + 1)); // note that JDBC column indexes are 1-based
            }
            this.contents.add(row);
        }
        catch (SQLException e) {
            throw new AnalyzerException("unable to retrieve result from backend database");
        }

    }
}

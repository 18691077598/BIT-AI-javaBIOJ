package org.example;

import java.util.Vector;

/**
 * 辅助类，用于封装 Select 方法的结果。
 */
public class SelectResult {
    private final Vector<String> columnNames;
    private final Vector<Vector<Object>> dataRows;

    public SelectResult(Vector<String> columnNames, Vector<Vector<Object>> dataRows) {
        this.columnNames = columnNames;
        this.dataRows = dataRows;
    }

    public Vector<String> getColumnNames() {
        return columnNames;
    }

    public Vector<Vector<Object>> getDataRows() {
        return dataRows;
    }
}

package org.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

public class CSVExporter {

    /**
     * 将数据写入 CSV 文件。
     *
     * @param filePath    要导出的文件路径。
     * @param columnNames 列名。
     * @param dataRows    数据行。
     * @throws IOException 如果写入文件时发生错误。
     */
    public void exportToCSV(String filePath, Vector<String> columnNames, Vector<Vector<Object>> dataRows) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // 转义并写入表头
            String escapedHeader = escapeVectorToCSV(columnNames);
            writer.write(escapedHeader);
            writer.newLine();

            // 写入数据行
            for (Vector<Object> row : dataRows) {
                StringBuilder rowString = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    Object cell = row.get(i);
                    rowString.append(escapeCSV(cell != null ? cell.toString() : ""));
                    if (i < row.size() - 1) {
                        rowString.append(",");
                    }
                }
                writer.write(rowString.toString());
                writer.newLine();
            }
        }
    }

    /**
     * 将 Vector<String> 转换为逗号分隔的 CSV 字符串，并转义每个元素。
     *
     * @param data Vector<String> 数据。
     * @return 逗号分隔的转义字符串。
     */
    private String escapeVectorToCSV(Vector<String> data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            sb.append(escapeCSV(data.get(i)));
            if (i < data.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * 转义 CSV 特殊字符。
     *
     * @param data 单元格数据。
     * @return 转义后的数据。
     */
    private String escapeCSV(String data) {
        if (data.contains(",") || data.contains("\"") || data.contains("\n")) {
            data = data.replace("\"", "\"\""); // 双引号内的双引号需转义
            data = "\"" + data + "\""; // 使用双引号括起包含特殊字符的字段
        }
        return data;
    }
}

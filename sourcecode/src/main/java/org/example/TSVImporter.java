package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TSVImporter 类负责将 TSV 文件导入到指定的数据库表中，并自动检测数据类型。
 */
public class TSVImporter {
    private final DOperator operator;
    private final boolean hasHeader;
    private final ImportProgressCallback callback;
    private static final int BATCH_SIZE = 1000; // 批量插入大小
    private static final int SAMPLE_SIZE = 1000; // 用于类型推断的样本行数

    /**
     * 构造方法。
     *
     * @param operator 数据库操作对象。
     * @param hasHeader TSV 文件是否包含表头。
     * @param callback 导入进度回调接口。
     */
    public TSVImporter(DOperator operator, boolean hasHeader, ImportProgressCallback callback) {
        this.operator = operator;
        this.hasHeader = hasHeader;
        this.callback = callback;
    }

    /**
     * 导入 TSV 文件到指定表。
     *
     * @param tableName 目标数据库表名。
     * @param tsvFile   要导入的 TSV 文件。
     * @throws IOException  如果读取文件时发生错误。
     * @throws SQLException 如果执行数据库操作时发生错误。
     */
    public void importTSV(String tableName, File tsvFile) throws IOException, SQLException {
        List<String[]> batch = new ArrayList<>();
        int processedRecords = 0;
        int totalRecords = countTotalRecords(tsvFile) - (hasHeader ? 1 : 0);

        try (BufferedReader br = new BufferedReader(new FileReader(tsvFile, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            String[] headers = null;

            // 用于类型推断的样本数据
            List<String[]> samples = new ArrayList<>();
            boolean tableCreated = false;

            while ((line = br.readLine()) != null) {
                if (isFirstLine && hasHeader) {
                    headers = parseLine(line);
                    headers = processHeaders(headers); // 处理并转义保留字
                    isFirstLine = false;
                    continue;
                }
                isFirstLine = false;

                String[] fields;
                try {
                    fields = parseLine(line);
                } catch (IOException e) {
                    System.err.println("解析行失败: " + e.getMessage());
                    continue; // 跳过有问题的行
                }

                if (samples.size() < SAMPLE_SIZE) {
                    samples.add(fields);
                    continue;
                }

                if (!tableCreated) {
                    if (headers == null) {
                        headers = generateDefaultHeaders(fields.length);
                    }
                    headers = processHeaders(headers); // 处理并转义保留字
                    String[] inferredTypes = inferColumnTypes(samples, headers.length);
                    boolean created = operator.createTable(tableName, headers, inferredTypes);
                    if (!created) {
                        throw new SQLException("Failed to create table: " + tableName);
                    }
                    tableCreated = true;

                    // 插入样本数据
                    operator.batchInsert(tableName, samples);
                    processedRecords += samples.size();
                    callback.onProgress(totalRecords, processedRecords);
                    samples.clear();
                }

                batch.add(fields);
                processedRecords++;

                if (batch.size() >= BATCH_SIZE) {
                    operator.batchInsert(tableName, batch);
                    callback.onProgress(totalRecords, processedRecords);
                    batch.clear();
                }
            }

            // 文件读取完成后，如果表尚未创建（样本不足 SAMPLE_SIZE）
            if (!tableCreated && !samples.isEmpty()) {
                if (headers == null && !samples.isEmpty()) {
                    headers = generateDefaultHeaders(samples.get(0).length);
                }
                headers = processHeaders(headers); // 处理并转义保留字
                String[] inferredTypes = inferColumnTypes(samples, headers.length);
                boolean created = operator.createTable(tableName, headers, inferredTypes);
                if (!created) {
                    throw new SQLException("Failed to create table: " + tableName);
                }
                tableCreated = true;

                // 插入样本数据
                operator.batchInsert(tableName, samples);
                processedRecords += samples.size();
                callback.onProgress(totalRecords, processedRecords);
                samples.clear();
            }

            // 插入剩余的数据
            if (!batch.isEmpty()) {
                operator.batchInsert(tableName, batch);
                callback.onProgress(totalRecords, processedRecords);
            }
        }
    }

    /**
     * 使用 Apache Commons CSV 解析一行 TSV 数据。
     *
     * @param line 要解析的行。
     * @return 字段数组。
     * @throws IOException 如果解析过程中发生错误。
     */
    private String[] parseLine(String line) throws IOException {
        CSVFormat format = CSVFormat.TDF.builder()
                .setDelimiter('\t')
                .setQuote('"')
                .setEscape('\\')
                .setIgnoreSurroundingSpaces(true)
                .build();

        try (CSVParser parser = CSVParser.parse(line, format)) {
            for (CSVRecord record : parser) {
                String[] fields = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    fields[i] = record.get(i);
                }
                return fields;
            }
        }
        return new String[0];
    }

    /**
     * 生成默认的列名（如果TSV文件没有表头）。
     *
     * @param columnCount 列数。
     * @return 默认的列名数组。
     */
    private String[] generateDefaultHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = "column_" + (i + 1);
        }
        return headers;
    }

    /**
     * 处理并转义列名中的保留字。
     *
     * @param headers 原始列名数组。
     * @return 处理后的列名数组。
     */
    private String[] processHeaders(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            headers[i] = operator.escapeColumnName(headers[i].trim());
        }
        return headers;
    }

    /**
     * 推断每一列的数据类型。
     *
     * @param samples     样本数据。
     * @param columnCount 列数。
     * @return 数据类型数组。
     */
    private String[] inferColumnTypes(List<String[]> samples, int columnCount) {
        String[] types = new String[columnCount];
        for (int col = 0; col < columnCount; col++) {
            types[col] = inferTypeForColumn(samples, col);
        }
        return types;
    }

    /**
     * 推断单个列的数据类型。
     *
     * @param samples 样本数据。
     * @param col     列索引。
     * @return 推断出的数据类型。
     */
    private String inferTypeForColumn(List<String[]> samples, int col) {
        boolean isInteger = true;
        boolean isReal = true;
        boolean isBoolean = true;

        for (String[] row : samples) {
            if (col >= row.length) {
                continue; // 处理缺失值
            }
            String value = row[col].trim();
            if (value.isEmpty()) {
                continue; // 跳过空值
            }

            // 检查整数
            if (isInteger) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    isInteger = false;
                }
            }

            // 检查实数
            if (isReal) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    isReal = false;
                }
            }

            // 检查布尔
            if (isBoolean) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    isBoolean = false;
                }
            }

            // 如果都不是，可以直接判断为文本
            if (!isInteger && !isReal && !isBoolean) {
                break;
            }
        }

        if (isInteger) {
            return "INTEGER";
        } else if (isReal) {
            return "REAL";
        } else if (isBoolean) {
            return "BOOLEAN";
        } else {
            return "TEXT";
        }
    }

    /**
     * 计算 TSV 文件的总记录数。
     *
     * @param tsvFile TSV 文件。
     * @return 总记录数。
     * @throws IOException 如果读取文件时发生错误。
     */
    private int countTotalRecords(File tsvFile) throws IOException {
        int lines = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(tsvFile, StandardCharsets.UTF_8))) {
            while (br.readLine() != null) {
                lines++;
            }
        }
        return lines;
    }
}

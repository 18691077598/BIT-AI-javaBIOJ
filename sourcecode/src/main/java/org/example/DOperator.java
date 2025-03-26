package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class DOperator {
    public String location;
    String url;

    // 定义SQLite的保留字集合
    private static final Set<String> SQL_RESERVED_WORDS = new HashSet<>(Arrays.asList(
            "ABORT", "ACTION", "ADD", "AFTER", "ALL", "ALTER", "ANALYZE", "AND",
            "AS", "ASC", "ATTACH", "AUTOINCREMENT", "BEFORE", "BEGIN", "BETWEEN",
            "BY", "CASCADE", "CASE", "CAST", "CHECK", "COLLATE", "COLUMN",
            "COMMIT", "CONFLICT", "CONSTRAINT", "CREATE", "CROSS", "CURRENT_DATE",
            "CURRENT_TIME", "CURRENT_TIMESTAMP", "DATABASE", "DEFAULT", "DEFERRABLE",
            "DEFERRED", "DELETE", "DESC", "DETACH", "DISTINCT", "DROP", "EACH",
            "ELSE", "END", "ESCAPE", "EXCEPT", "EXCLUSIVE", "EXISTS", "EXPLAIN",
            "FAIL", "FOR", "FOREIGN", "FROM", "FULL", "GLOB", "GROUP", "HAVING",
            "IF", "IGNORE", "IMMEDIATE", "IN", "INDEX", "INDEXED", "INITIALLY",
            "INNER", "INSERT", "INSTEAD", "INTERSECT", "INTO", "IS", "ISNULL",
            "JOIN", "KEY", "LEFT", "LIKE", "LIMIT", "MATCH", "NATURAL", "NO",
            "NOT", "NOTNULL", "NULL", "OF", "OFFSET", "ON", "OR", "ORDER",
            "OUTER", "PLAN", "PRAGMA", "PRIMARY", "QUERY", "RAISE", "RECURSIVE",
            "REFERENCES", "REGEXP", "REINDEX", "RELEASE", "RENAME", "REPLACE",
            "RESTRICT", "RIGHT", "ROLLBACK", "ROW", "SAVEPOINT", "SELECT", "SET",
            "TABLE", "TEMP", "TEMPORARY", "THEN", "TO", "TRANSACTION", "TRIGGER",
            "UNION", "UNIQUE", "UPDATE", "USING", "VACUUM", "VALUES", "VIEW",
            "VIRTUAL", "WHEN", "WHERE", "WITH", "WITHOUT"
    ));

    /**
     * 检查并转义列名。
     *
     * @param columnName 原始列名。
     * @return 转义后的列名。
     */
    public String escapeColumnName(String columnName) {
        if (SQL_RESERVED_WORDS.contains(columnName.toUpperCase())) {
            // 使用双引号转义保留字，并处理内部双引号
            return "\"" + columnName.replace("\"", "\"\"") + "\"";
        }
        return columnName;
    }

    /**
     * 获取表的列名及其类型，并转义必要的列名，同时排除自动增量的id列。
     *
     * @param tableName 要查询的表名。
     * @return 列信息列表。如果查询失败，返回空列表。
     */
    public List<ColumnInfo> getTableColumnsWithTypesEscaped(String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        String query = "PRAGMA table_info(" + escapeColumnName(tableName) + ");";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String columnName = rs.getString("name");
                String columnType = rs.getString("type");
                int pk = rs.getInt("pk"); // 判断是否为主键

                // 排除自动增量的id列
                if (!(pk == 1 && columnName.equalsIgnoreCase("id"))) {
                    columns.add(new ColumnInfo(columnName, columnType));
                }
            }

        } catch (SQLException e) {
            System.err.println("获取表列信息出错: " + e.getMessage());
        }

        return columns;
    }

    // 构造函数，传入数据库地址
    public DOperator(String location){
        this.location = location;
        this.url = "jdbc:sqlite:"+location;
    }

    // 修改数据库路径
    public DOperator setPath(String location){
        this.location = location;
        this.url = "jdbc:sqlite:"+location;
        return this;
    }

    // 创建数据库
    public boolean createDatabase(){
        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                System.out.println("连接成功！数据库文件存储在 " + url);
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.out.println("连接数据库失败: " + e.getMessage());
            return false;
        }
    }

    // 检查连接
    public boolean checkConnect(){
        return this.createDatabase();
    }

    /**
     * Select 方法，用于从指定的表中查询数据。
     *
     * @param tableName    要查询的表名。
     * @param fieldNames   要匹配的字段名数组。如果为 null 或空，则不进行字段匹配。
     * @param searchValues 与字段名对应的查找内容数组。如果为 null 或空，则不进行字段匹配。
     * @param page         当前页码（从 1 开始）。
     * @param pageSize     每页记录数。
     * @return SelectResult 包含列名和数据行。如果查询失败，返回空结果。
     */
    public SelectResult select(String tableName, String[] fieldNames, String[] searchValues, int page, int pageSize) throws SQLException {
        Vector<String> columnNames = new Vector<>();
        Vector<Vector<Object>> dataRows = new Vector<>();

        // 参数验证
        boolean hasSearch = fieldNames != null && searchValues != null &&
                fieldNames.length > 0 && searchValues.length > 0 &&
                fieldNames.length == searchValues.length;

        // 构建 SELECT 语句
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(escapeColumnName(tableName));
        if (hasSearch) {
            sb.append(" WHERE ");
            for (int i = 0; i < fieldNames.length; i++) {
                sb.append(escapeColumnName(fieldNames[i])).append(" LIKE ?");
                if (i < fieldNames.length - 1) {
                    sb.append(" AND ");
                }
            }
        }
        sb.append(" LIMIT ? OFFSET ?;"); // 分页

        String query = sb.toString();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            int paramIndex = 1;

            // 设置搜索参数
            if (hasSearch) {
                for (String value : searchValues) {
                    pstmt.setString(paramIndex++, value);
                }
            }

            // 设置分页参数
            pstmt.setInt(paramIndex++, pageSize);
            pstmt.setInt(paramIndex, (page - 1) * pageSize);

            ResultSet rs = pstmt.executeQuery();

            // 获取列名
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }

            // 获取数据行
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                dataRows.add(row);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("Select 查询出错: " + e.getMessage());
            throw e; // 抛出异常
        }

        return new SelectResult(columnNames, dataRows);
    }


    /**
     * 计算指定表的总记录数。
     *
     * @param tableName 要查询的表名。
     * @return 表中的总记录数。如果查询失败，返回 0。
     */
    public int getTotalRecords(String tableName) {
        String countQuery = "SELECT COUNT(*) AS total FROM " + escapeColumnName(tableName) + ";";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countQuery)) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("计算总记录数时出错: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 创建数据库表，如果表不存在，并根据推断的数据类型设置列类型。
     *
     * @param tableName 表名。
     * @param keys      表头数组。
     * @param types     推断出的数据类型数组。
     * @return 是否成功创建表。
     */
    public boolean createTable(String tableName, String[] keys, String[] types) {
        // 检查 keys 和 types 的长度是否匹配
        if (keys.length != types.length) {
            System.out.println("字段数量和类型数量不匹配！");
            return false;
        }
        // 构建 CREATE TABLE 语句
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(escapeColumnName(tableName)).append(" (");
        sb.append("id INTEGER PRIMARY KEY AUTOINCREMENT");

        for (int i = 0; i < keys.length; i++) {
            String key = escapeColumnName(keys[i]);
            String type = types[i];
            sb.append(", ").append(key).append(" ").append(type);
        }
        sb.append(");");

        String createTableSQL = sb.toString();
        System.out.println("执行的SQL语句: " + createTableSQL);

        // 执行 SQL 语句
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("表 '" + tableName + "' 创建成功或已存在。");
            return true;
        } catch (SQLException e) {
            System.out.println("创建表时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 批量插入方法，确保列名被正确转义，并且自动增量的id不参与数据插入。
     *
     * @param tableName 目标数据库表名。
     * @param rows      要插入的数据行列表。
     * @throws SQLException 如果执行数据库操作时发生错误。
     */
    public void batchInsert(String tableName, List<String[]> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            System.out.println("没有数据需要插入。");
            return;
        }

        // 获取表的列信息，已排除自动增量的id列
        List<ColumnInfo> columns = getTableColumnsWithTypesEscaped(tableName);
        if (columns.isEmpty()) {
            throw new SQLException("表 " + tableName + " 不存在或没有列信息。");
        }

        // 构建 INSERT 语句，并转义列名
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(escapeColumnName(tableName)).append(" (");

        for (int i = 0; i < columns.size(); i++) {
            sb.append(escapeColumnName(columns.get(i).getName()));
            if (i < columns.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(") VALUES (");

        for (int i = 0; i < columns.size(); i++) {
            sb.append("?");
            if (i < columns.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(");");

        String insertSQL = sb.toString();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false); // 开始事务

            for (String[] row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i < row.length) {
                        String value = row[i].trim();
                        String type = columns.get(i).getType().toUpperCase();

                        if (value.isEmpty()) {
                            pstmt.setObject(i + 1, null);
                            continue;
                        }

                        try {
                            switch (type) {
                                case "INTEGER":
                                case "INT":
                                    pstmt.setInt(i + 1, Integer.parseInt(value));
                                    break;
                                case "REAL":
                                case "FLOAT":
                                case "DOUBLE":
                                    pstmt.setDouble(i + 1, Double.parseDouble(value));
                                    break;
                                case "BOOLEAN":
                                    pstmt.setBoolean(i + 1, Boolean.parseBoolean(value));
                                    break;
                                case "TEXT":
                                default:
                                    // 特别处理 annotations 字段，将其作为字符串存储
                                    if (columns.get(i).getName().equalsIgnoreCase("annotations")) {
                                        // 移除大括号和单引号
                                        String annotations = value.replaceAll("[{}']", "");
                                        pstmt.setString(i + 1, annotations);
                                    } else {
                                        pstmt.setString(i + 1, value);
                                    }
                                    break;
                            }
                        } catch (NumberFormatException e) {
                            // 如果转换失败，设置为 null 并记录错误
                            pstmt.setObject(i + 1, null);
                            System.err.println("数据类型转换失败，字段: " + columns.get(i).getName() + ", 值: " + value);
                        }
                    } else {
                        pstmt.setObject(i + 1, null); // 处理缺失的值
                    }
                }
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit(); // 提交事务

            System.out.println("成功插入 " + rows.size() + " 条记录到表 '" + tableName + "'。");

        } catch (SQLException e) {
            System.err.println("批量插入出错: " + e.getMessage());
            throw e; // 重新抛出异常以便上层处理
        }
    }

    /**
     * 获取当前数据库中的所有表名。
     *
     * @return 表名列表。如果查询失败，返回空列表。
     */
    public List<String> getAllTableNames() {
        List<String> tableNames = new ArrayList<>();
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String tableName = rs.getString("name");
                tableNames.add(tableName);
            }

        } catch (SQLException e) {
            System.err.println("获取所有表名出错: " + e.getMessage());
        }

        return tableNames;
    }

    /**
     * 获取指定表的分页数据。
     *
     * @param tableName  要查询的表名。
     * @param pageSize   每页记录数。
     * @param pageNumber 页码（从1开始）。
     * @return 数据行的向量。如果查询失败，返回空向量。
     */
    public Vector<Vector<Object>> getTablePageData(String tableName, int pageSize, int pageNumber) {
        Vector<Vector<Object>> dataRows = new Vector<>();
        String query = "SELECT * FROM " + escapeColumnName(tableName) + " LIMIT ? OFFSET ?;";
        int offset = (pageNumber - 1) * pageSize;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, pageSize);
            pstmt.setInt(2, offset);
            ResultSet rs = pstmt.executeQuery();

            List<ColumnInfo> columns = getTableColumnsWithTypesEscaped(tableName);
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (ColumnInfo col : columns) {
                    row.add(rs.getObject(col.getName()));
                }
                dataRows.add(row);
            }

        } catch (SQLException e) {
            System.err.println("获取分页数据出错: " + e.getMessage());
        }

        return dataRows;
    }

    /**
     * 获取指定表的所有列名，不包括自动增量的id列。
     *
     * @param tableName 要查询的表名。
     * @return 列名的向量。如果查询失败，返回空向量。
     */
    public Vector<String> getTableColumnNames(String tableName) {
        Vector<String> columnNames = new Vector<>();
        List<ColumnInfo> columns = getTableColumnsWithTypesEscaped(tableName);
        for (ColumnInfo col : columns) {
            columnNames.add(col.getName());
        }
        return columnNames;
    }

    /**
     * 辅助类，用于存储列名及其类型信息。
     */
    public static class ColumnInfo {
        private String name;
        private String type;

        public ColumnInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public SelectResult search(String tableName, String columnName, String keyword) throws SQLException {
        Vector<String> columnNames = getTableColumnNames(tableName);
        Vector<Vector<Object>> dataRows = new Vector<>();

        // 构建 WHERE 子句，使用 LIKE 进行模糊匹配
        String whereClause = escapeColumnName(columnName) + " LIKE ?";
        String query = "SELECT * FROM " + escapeColumnName(tableName) + " WHERE " + whereClause + ";";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, "%" + keyword + "%"); // 部分匹配

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    for (String col : columnNames) {
                        row.add(rs.getObject(col));
                    }
                    dataRows.add(row);
                }
            }
        }

        return new SelectResult(columnNames, dataRows);
    }

    /**
     * 实现获取符合搜索条件的总记录数的方法。
     *
     * @param tableName    要查询的表名。
     * @param fieldNames   要匹配的字段名数组。
     * @param searchValues 与字段名对应的查找内容数组，支持模糊匹配（含 %）。
     * @return 符合条件的总记录数。如果查询失败，返回 0。
     * @throws SQLException 如果执行数据库操作时发生错误。
     */
    public int getTotalRecordsWithSearch(String tableName, String[] fieldNames, String[] searchValues) throws SQLException {
        // 参数验证
        boolean hasSearch = fieldNames != null && searchValues != null &&
                fieldNames.length > 0 && searchValues.length > 0 &&
                fieldNames.length == searchValues.length;

        if (!hasSearch) {
            // 如果没有搜索条件，则返回表的总记录数
            return getTotalRecords(tableName);
        }

        // 构建 COUNT 查询语句
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) AS total FROM ").append(escapeColumnName(tableName)).append(" WHERE ");
        for (int i = 0; i < fieldNames.length; i++) {
            sb.append(escapeColumnName(fieldNames[i])).append(" LIKE ?");
            if (i < fieldNames.length - 1) {
                sb.append(" AND ");
            }
        }
        sb.append(";");

        String countQuery = sb.toString();
        int total = 0;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(countQuery)) {

            // 设置搜索参数
            for (int i = 0; i < searchValues.length; i++) {
                pstmt.setString(i + 1, searchValues[i]);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    total = rs.getInt("total");
                }
            }

        } catch (SQLException e) {
            System.err.println("计算符合搜索条件的总记录数时出错: " + e.getMessage());
            throw e;
        }

        return total;
    }

    /**
     * 插入一条数据到指定表中
     *
     * @param tableName 目标数据库表名。
     * @param row       要插入的数据行（字段值的数组）。
     * @throws SQLException 如果执行数据库操作时发生错误。
     */
    public void insert(String tableName, String[] row) throws SQLException {
        if (row == null || row.length == 0) {
            System.out.println("没有数据需要插入。");
            return;
        }

        // 获取表的列信息，已排除自动增量的id列
        List<ColumnInfo> columns = getTableColumnsWithTypesEscaped(tableName);
        if (columns.isEmpty()) {
            throw new SQLException("表 " + tableName + " 不存在或没有列信息。");
        }

        // 构建 INSERT 语句，并转义列名
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(escapeColumnName(tableName)).append(" (");

        for (int i = 0; i < columns.size(); i++) {
            sb.append(escapeColumnName(columns.get(i).getName()));
            if (i < columns.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(") VALUES (");

        for (int i = 0; i < columns.size(); i++) {
            sb.append("?");
            if (i < columns.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(");");

        String insertSQL = sb.toString();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            // 设置字段值
            for (int i = 0; i < columns.size(); i++) {
                if (i < row.length) {
                    String value = row[i].trim();
                    String type = columns.get(i).getType().toUpperCase();

                    if (value.isEmpty()) {
                        pstmt.setObject(i + 1, null);
                        continue;
                    }

                    try {
                        switch (type) {
                            case "INTEGER":
                            case "INT":
                                pstmt.setInt(i + 1, Integer.parseInt(value));
                                break;
                            case "REAL":
                            case "FLOAT":
                            case "DOUBLE":
                                pstmt.setDouble(i + 1, Double.parseDouble(value));
                                break;
                            case "BOOLEAN":
                                pstmt.setBoolean(i + 1, Boolean.parseBoolean(value));
                                break;
                            case "TEXT":
                            default:
                                pstmt.setString(i + 1, value);
                                break;
                        }
                    } catch (NumberFormatException e) {
                        // 如果转换失败，设置为 null 并记录错误
                        pstmt.setObject(i + 1, null);
                        System.err.println("数据类型转换失败，字段: " + columns.get(i).getName() + ", 值: " + value);
                    }
                } else {
                    pstmt.setObject(i + 1, null); // 处理缺失的值
                }
            }

            pstmt.executeUpdate();
            System.out.println("成功插入一条记录到表 '" + tableName + "'。");

        } catch (SQLException e) {
            System.err.println("插入数据出错: " + e.getMessage());
            throw e; // 重新抛出异常以便上层处理
        }
    }
    /**
     * SelectTopN 方法，用于从指定的表中检索满足条件的前 N 条记录。
     *
     * @param tableName    要查询的表名。
     * @param fieldNames   要匹配的字段名数组。如果为 null 或空，则不进行字段匹配。
     * @param searchValues 与字段名对应的查找内容数组。如果为 null 或空，则不进行字段匹配。
     * @param limit        要检索的记录数上限。
     * @return SelectResult 包含列名和数据行。如果查询失败，返回空结果。
     */
    public SelectResult selectTopN(String tableName, String[] fieldNames, String[] searchValues, int limit) {
        Vector<String> columnNames = new Vector<>();
        Vector<Vector<Object>> dataRows = new Vector<>();

        // 参数验证
        boolean hasSearch = fieldNames != null && searchValues != null &&
                fieldNames.length > 0 && searchValues.length > 0 &&
                fieldNames.length == searchValues.length;

        // 构建 SELECT 语句
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(escapeColumnName(tableName));
        if (hasSearch) {
            sb.append(" WHERE ");
            for (int i = 0; i < fieldNames.length; i++) {
                sb.append(escapeColumnName(fieldNames[i])).append(" LIKE ?");
                if (i < fieldNames.length - 1) {
                    sb.append(" AND ");
                }
            }
        }
        sb.append(" LIMIT ?;"); // 仅限制记录数

        String query = sb.toString();

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            int paramIndex = 1;

            // 设置搜索参数
            if (hasSearch) {
                for (String value : searchValues) {
                    pstmt.setString(paramIndex++, value);
                }
            }

            // 设置 LIMIT 参数
            pstmt.setInt(paramIndex, limit);

            ResultSet rs = pstmt.executeQuery();

            // 获取列名
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }

            // 获取数据行
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                dataRows.add(row);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("SelectTopN 查询出错: " + e.getMessage());
            // 根据需求，可以选择抛出异常或返回空结果
        }

        return new SelectResult(columnNames, dataRows);
    }
    /**
     * 获取指定表中的所有记录。
     *
     * @param tableName 要查询的表名。
     * @return SelectResult 包含所有列名和数据行。如果查询失败，返回空结果。
     */
    public SelectResult getAllRecords(String tableName) {
        Vector<String> columnNames = new Vector<>();
        Vector<Vector<Object>> dataRows = new Vector<>();

        // 构建 SELECT 语句
        String query = "SELECT * FROM " + escapeColumnName(tableName) + ";";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            // 获取列名
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }

            // 获取数据行
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                dataRows.add(row);
            }

            rs.close();

        } catch (SQLException e) {
            System.err.println("获取所有记录时出错: " + e.getMessage());
            // 根据需求，可以选择抛出异常或返回空结果
        }

        return new SelectResult(columnNames, dataRows);
    }

}

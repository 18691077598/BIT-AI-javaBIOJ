import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

public class DBtest {

    public static void main(String[] args) {
        // 连接到 SQLite 数据库（如果文件不存在会自动创建）
        String url = "jdbc:sqlite:sample.db"; // 数据库文件名为 sample.db
        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                // 创建 Statement 对象用于执行 SQL
                Statement stmt = conn.createStatement();
                // 创建表
                String createTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "email TEXT NOT NULL)";
                stmt.execute(createTableSQL);
                System.out.println("Table created successfully.");

                // 插入数据
                String insertSQL = "INSERT INTO users(name, email) VALUES('Alice', 'alice@example.com')";
                stmt.executeUpdate(insertSQL);
                System.out.println("Data inserted successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }
}

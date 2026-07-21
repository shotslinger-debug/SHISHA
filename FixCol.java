import java.sql.*;
public class FixCol {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/shisha_db", "root", "");
        Statement stmt = conn.createStatement();
        stmt.execute("ALTER TABLE producto MODIFY COLUMN ImagenURL LONGTEXT");
        System.out.println("Column type changed to LONGTEXT.");
    }
}

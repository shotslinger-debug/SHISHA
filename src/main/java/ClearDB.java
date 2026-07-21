import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ClearDB {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/shisha_db", "root", "");
            Statement stmt = conn.createStatement();
            stmt.execute("TRUNCATE TABLE finanzas_ingreso");
            stmt.execute("TRUNCATE TABLE finanzas_egreso");
            System.out.println("Datos limpiados.");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckFinanzas {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/pasteleria";
        String user = "root";
        String password = "";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT NOW()");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("MySQL NOW(): " + rs.getString(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT CURRENT_TIMESTAMP");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("MySQL CURRENT_TIMESTAMP: " + rs.getString(1));
                }
            }
            System.out.println("Java new Date(): " + new java.util.Date());
            
            System.out.println("=== FINANZAS INGRESO ===");
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM finanzas_ingreso ORDER BY Fecha DESC LIMIT 5");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Id: " + rs.getInt("IdIngreso") + ", Fecha: " + rs.getString("Fecha") + ", ts: " + rs.getTimestamp("Fecha"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

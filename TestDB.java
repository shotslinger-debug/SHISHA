import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDB {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/shisha_db";
        String user = "root";
        String password = ""; // Assuming blank for XAMPP
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT IdUsuario, Nombre, Correo FROM usuario")) {
            System.out.println("--- USUARIOS EN BD ---");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("IdUsuario") + ", Nombre: " + rs.getString("Nombre") + ", Correo: " + rs.getString("Correo"));
            }
            System.out.println("----------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

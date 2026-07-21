import java.sql.*;

public class CountDB {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/pasteleria_db?useSSL=false&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(url, "root", "password");
             Statement s = c.createStatement()) {
            
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM producto");
            rs.next();
            System.out.println("TOTAL PRODUCTOS: " + rs.getInt(1));
            
            ResultSet rs2 = s.executeQuery("SELECT COUNT(*) FROM producto WHERE Activo = 1");
            rs2.next();
            System.out.println("PRODUCTOS ACTIVOS: " + rs2.getInt(1));
            
            ResultSet rs3 = s.executeQuery("SELECT Categoria, COUNT(*) FROM producto WHERE Activo = 1 GROUP BY Categoria");
            while (rs3.next()) {
                System.out.println("Cat: " + rs3.getString(1) + " -> " + rs3.getInt(2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

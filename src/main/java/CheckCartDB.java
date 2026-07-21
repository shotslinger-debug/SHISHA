import java.sql.*;

public class CheckCartDB {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/pasteleria_db?useSSL=false&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(url, "root", "password");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM carrito")) {
            System.out.println("--- RESULTADOS CARRITO ---");
            while(rs.next()) {
                System.out.println("IdUsuario: " + rs.getInt("IdUsuario") + 
                                   ", IdProducto: " + rs.getInt("IdProducto") + 
                                   ", Cantidad: " + rs.getInt("Cantidad"));
            }
            System.out.println("--- FIN ---");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

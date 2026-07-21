import java.sql.*;
public class FixDB {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/shisha_db", "root", "");
        Statement stmt = conn.createStatement();
        int affected = stmt.executeUpdate("INSERT INTO repartidor (IdUsuario, Vehiculo, Estado) SELECT IdUsuario, 'No especificado', 'disponible' FROM usuario WHERE Rol = 'repartidor' AND IdUsuario NOT IN (SELECT IdUsuario FROM repartidor)");
        System.out.println("Fixed " + affected + " rows.");
    }
}

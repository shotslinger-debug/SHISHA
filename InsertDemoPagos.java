import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class InsertDemoPagos {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/shisha_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "D64dce51f6!GABY";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.createStatement().execute("SET FOREIGN_KEY_CHECKS=0;");
            conn.createStatement().execute("INSERT IGNORE INTO usuario (IdUsuario, Nombre, Apellido, Rol) VALUES (99, 'Cliente', 'DemoPago', 'cliente')");
            conn.createStatement().execute("INSERT IGNORE INTO cliente (IdCliente, IdUsuario) VALUES (1, 99)");
            conn.createStatement().execute("INSERT IGNORE INTO pedido (IdPedido, IdCliente, MetodoPago, Total, Estado) VALUES (998, 1, 'Transferencia', 500.0, 'pendiente')");
            conn.createStatement().execute("INSERT IGNORE INTO pedido (IdPedido, IdCliente, MetodoPago, Total, Estado) VALUES (999, 1, 'Efectivo', 300.0, 'pendiente')");
            
            String sqlPago = "INSERT INTO pago (IdPedido, Metodo, Monto, Estado) VALUES (?, ?, ?, 'pendiente')";
            try (PreparedStatement ps = conn.prepareStatement(sqlPago)) {
                ps.setInt(1, 998);
                ps.setString(2, "Transferencia");
                ps.setDouble(3, 500.0);
                ps.executeUpdate();
                
                ps.setInt(1, 999);
                ps.setString(2, "Efectivo");
                ps.setDouble(3, 300.0);
                ps.executeUpdate();
            }
            conn.createStatement().execute("SET FOREIGN_KEY_CHECKS=1;");
            System.out.println("Demo pagos inserted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

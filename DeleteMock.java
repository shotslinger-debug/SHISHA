import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DeleteMock {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/shisha?useSSL=false&serverTimezone=America/Mexico_City", "root", "D64dce51f6!GABY");
            Statement stmt = conn.createStatement();
            int rows = stmt.executeUpdate("DELETE FROM pedido WHERE IdPedido IN (1,2,3)");
            System.out.println("Eliminados: " + rows);
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

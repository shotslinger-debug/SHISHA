package com.pasteleria;
import java.sql.Connection;
import java.sql.Statement;
public class ClearDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE finanzas_ingreso");
            stmt.execute("TRUNCATE TABLE finanzas_egreso");
            System.out.println("Datos limpiados exitosamente a trav?s de Maven.");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

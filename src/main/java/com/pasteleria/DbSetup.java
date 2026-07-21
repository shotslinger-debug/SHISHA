package com.pasteleria;

import java.sql.Connection;
import java.sql.Statement;

public class DbSetup {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracion_negocio (" +
                         "IdConfiguracion INT AUTO_INCREMENT PRIMARY KEY," +
                         "NombreNegocio VARCHAR(255)," +
                         "Direccion VARCHAR(255)," +
                         "Telefono VARCHAR(50)," +
                         "EmailContacto VARCHAR(100))");
            
            // Insertar datos por defecto si la tabla está vacía
            stmt.execute("INSERT INTO configuracion_negocio (NombreNegocio, Direccion, Telefono, EmailContacto) " +
                         "SELECT 'Pastelería Shisha', 'Av. Principal 123', '555-1234', 'contacto@shisha.com' " +
                         "WHERE NOT EXISTS (SELECT 1 FROM configuracion_negocio)");
                         
            System.out.println("Tabla configuracion_negocio configurada exitosamente.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.pasteleria;

import java.sql.Connection;
import java.sql.Statement;

public class InitDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS `configuracion_negocio` (" +
                         "`IdConfiguracion` int PRIMARY KEY AUTO_INCREMENT," +
                         "`NombreNegocio` varchar(150) NOT NULL DEFAULT 'Shisha Pastelería'," +
                         "`Direccion` varchar(255)," +
                         "`Telefono` varchar(20)," +
                         "`EmailContacto` varchar(150)," +
                         "`LogoURL` varchar(255)," +
                         "`HorarioApertura` time DEFAULT '08:00:00'," +
                         "`HorarioCierre` time DEFAULT '20:00:00'," +
                         "`DiasOperacion` varchar(100) DEFAULT 'Lunes a Domingo'," +
                         "`Moneda` varchar(10) DEFAULT 'MXN'," +
                         "`ImpuestoPorcentaje` decimal(5,2) DEFAULT 16.00" +
                         ")");
                         
            stmt.execute("INSERT INTO `configuracion_negocio` (`NombreNegocio`, `Direccion`, `Telefono`, `EmailContacto`) " +
                         "SELECT 'Shisha Pastelería', 'Av. Principal 123, Centro', '555-1234', 'contacto@shisha.com' " +
                         "WHERE NOT EXISTS (SELECT 1 FROM `configuracion_negocio`)");
                         
            stmt.execute("CREATE TABLE IF NOT EXISTS `reporte` (" +
                         "`IdReporte` int PRIMARY KEY AUTO_INCREMENT" +
                         ")"); // Dummy for now if needed, but reportes are usually just queries
                         
            try {
                stmt.execute("ALTER TABLE producto MODIFY COLUMN ImagenURL LONGTEXT");
                System.out.println("Columna ImagenURL actualizada a LONGTEXT.");
            } catch(Exception ex) {
                System.out.println("No se pudo alterar ImagenURL: " + ex.getMessage());
            }
                         
            System.out.println("Base de datos actualizada correctamente.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

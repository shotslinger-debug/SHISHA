package com.pasteleria;
import java.sql.Connection;
import java.sql.Statement;

public class FixDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Eliminar los 8 productos originales y los duplicados fallidos para dejar espacio limpio
            // Id 1 a 8 son los originales. 
            stmt.execute("DELETE FROM producto WHERE IdProducto <= 8 AND IdProducto != 1");
            
            // Corregir nombres mal codificados (los que se insertaron por REST sin UTF-8)
            stmt.execute("UPDATE producto SET Nombre = 'Carlota de Limón' WHERE Nombre LIKE 'Carlota de Lim%n'");
            stmt.execute("UPDATE producto SET Nombre = 'Pay de Limón', Categoria = 'Pays' WHERE Nombre LIKE 'Pay de Lim%n'");
            stmt.execute("UPDATE producto SET Nombre = 'Porción Carlota de Limón' WHERE Nombre LIKE 'Porci%n Carlota de Lim%n'");
            stmt.execute("UPDATE producto SET Nombre = 'Porción Carlota de Fresa' WHERE Nombre LIKE 'Porci%n Carlota de Fresa'");
            stmt.execute("UPDATE producto SET Nombre = 'Porción Carlota de Durazno' WHERE Nombre LIKE 'Porci%n Carlota de Durazno'");
            stmt.execute("UPDATE producto SET Nombre = 'Porción Flan' WHERE Nombre LIKE 'Porci%n Flan'");
            stmt.execute("UPDATE producto SET Nombre = 'Porción Pay de Fresa' WHERE Nombre LIKE 'Porci%n Pay de Fresa'");
            
            stmt.execute("UPDATE producto SET Nombre = 'Rebanada Carlota de Café' WHERE Nombre LIKE 'Rebanada Carlota de Caf%'");
            stmt.execute("UPDATE producto SET Nombre = 'Rebanada Pay de Limón' WHERE Nombre LIKE 'Rebanada Pay de Lim%n'");
            stmt.execute("UPDATE producto SET Nombre = 'Tiramisú' WHERE Nombre LIKE 'Tiramis%' AND IdProducto != 1");
            stmt.execute("UPDATE producto SET Nombre = 'Rebanada Tiramisú' WHERE Nombre LIKE 'Rebanada Tiramis%'");
            
            System.out.println("Nombres corregidos en la base de datos.");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

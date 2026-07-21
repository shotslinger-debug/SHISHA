package com.pasteleria;

import java.sql.Connection;
import java.sql.Statement;

public class PatchDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
             try {
                 stmt.executeUpdate("ALTER TABLE ingrediente ADD COLUMN Categoria VARCHAR(100) DEFAULT 'Otros'");
                 System.out.println("Columna Categoria agregada.");
             } catch(Exception e) { System.out.println("Categoria ya existe."); }
             
             try {
                 stmt.executeUpdate("ALTER TABLE ingrediente ADD COLUMN Proveedor VARCHAR(255) DEFAULT ''");
                 System.out.println("Columna Proveedor agregada.");
             } catch(Exception e) { System.out.println("Proveedor ya existe."); }
             
             System.out.println("PATCH EXITOSO");
        } catch (Exception e) {
             e.printStackTrace();
        }
    }
}

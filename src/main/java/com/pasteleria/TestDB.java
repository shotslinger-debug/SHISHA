package com.pasteleria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT IdUsuario, Nombre, Correo FROM usuario")) {
            System.out.println("--- USUARIOS EN BD ---");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("IdUsuario") + ", Nombre: " + rs.getString("Nombre") + ", Correo: " + rs.getString("Correo"));
            }
            System.out.println("----------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

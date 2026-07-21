package com.pasteleria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDB {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            
            System.out.println("=== CLIENTES ===");
            ResultSet rs1 = stmt.executeQuery("SELECT * FROM cliente");
            while (rs1.next()) {
                System.out.println("IdCliente: " + rs1.getInt("IdCliente") + ", IdUsuario: " + rs1.getInt("IdUsuario"));
            }
            
            System.out.println("\n=== PEDIDOS ===");
            ResultSet rs2 = stmt.executeQuery("SELECT * FROM pedido");
            while (rs2.next()) {
                System.out.println("IdPedido: " + rs2.getInt("IdPedido") + ", IdCliente: " + rs2.getInt("IdCliente") + ", Estado: " + rs2.getString("Estado"));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

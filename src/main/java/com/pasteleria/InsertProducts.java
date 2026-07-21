package com.pasteleria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class InsertProducts {
    public static void main(String[] args) {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {

            // Clean up old products to start fresh
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            stmt.execute("TRUNCATE TABLE detalle_pedido;");
            stmt.execute("TRUNCATE TABLE calificacion;");
            stmt.execute("TRUNCATE TABLE producto;");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");

            String sql = "INSERT INTO producto (Nombre, Categoria, Precio, Stock, Descripcion, ImagenURL, Activo) VALUES (?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String[][] products = {
                    {"Carlota de Durazno", "Postres", "200.00", "10", "Deliciosa carlota de durazno.", "../../img/productos/carlotta_durazno.jpg"},
                    {"Carlota de Fresa", "Postres", "210.00", "10", "Carlota con fresas frescas.", "../../img/productos/carlotta_fresa.jpg"},
                    {"Carlota de Limón", "Postres", "190.00", "10", "Clásica carlota de limón.", "../../img/productos/carlotta_limon.jpg"},
                    {"Cupcake de Chocolate", "Cupcakes", "35.00", "20", "Cupcake esponjoso de chocolate.", "../../img/productos/cupcake_chocolate.jpg"},
                    {"Cupcake Oreo", "Cupcakes", "40.00", "20", "Cupcake con galleta Oreo.", "../../img/productos/cupcake_oreo.jpg"},
                    {"Cupcake Red Velvet", "Cupcakes", "45.00", "20", "Suave cupcake red velvet.", "../../img/productos/cupcake_red_velvet.jpg"},
                    {"Flan Napolitano", "Postres", "150.00", "5", "Flan napolitano tradicional.", "../../img/productos/flan.jpg"},
                    {"Galletas de Avena", "Galletas", "15.00", "30", "Galletas de avena saludables.", "../../img/productos/galleta_avea.jpg"},
                    {"Galletas de Chocolate", "Galletas", "15.00", "30", "Galletas con trozos de chocolate.", "../../img/productos/galleta_chocolate.jpg"},
                    {"Galletas de Nuez", "Galletas", "18.00", "30", "Crujientes galletas de nuez.", "../../img/productos/galleta_nuez.jpg"},
                    {"Galletas Red Velvet", "Galletas", "20.00", "30", "Galletas estilo red velvet.", "../../img/productos/galleta_red_velvet.jpg"},
                    {"Galletas de Vainilla", "Galletas", "12.00", "30", "Galletas de mantequilla y vainilla.", "../../img/productos/galleta_vainilla.jpg"},
                    {"Gelatina de Mosaico", "Postres", "120.00", "8", "Colorida gelatina de mosaico.", "../../img/productos/gelatina_mosaico.jpg"},
                    {"Pastel de Chocolate", "Pasteles", "350.00", "5", "Pastel de chocolate intenso.", "../../img/productos/pastel_chocolate.jpg"},
                    {"Pastel de Fresa", "Pasteles", "320.00", "5", "Pastel decorado con fresas.", "../../img/productos/pastel_fresa.jpg"},
                    {"Pastel Imposible", "Pasteles", "380.00", "4", "Mitad pastel de chocolate, mitad flan.", "../../img/productos/pastel_imposible.jpg"},
                    {"Pastel de Vainilla", "Pasteles", "300.00", "5", "Pastel clásico de vainilla.", "../../img/productos/pastel_vainilla.jpg"},
                    {"Pay de Fresa", "Pays", "220.00", "6", "Pay dulce con relleno de fresa.", "../../img/productos/pay_fresa.jpg"},
                    {"Pay de Limón", "Pays", "200.00", "6", "Pay de limón refrescante.", "../../img/productos/pay_limon.jpg"},
                    {"Rebanada Carlota Durazno", "Postres", "45.00", "15", "Porción de carlota de durazno.", "../../img/productos/porcion_carlota_durazno.jpg"},
                    {"Rebanada Carlota Limón", "Postres", "40.00", "15", "Porción de carlota de limón.", "../../img/productos/porcion_carlota_limon.jpg"},
                    {"Rebanada de Flan", "Postres", "35.00", "15", "Rebanada de flan napolitano.", "../../img/productos/porcion_flan.jpg"},
                    {"Rebanada Pay Fresa", "Pays", "45.00", "15", "Porción de pay de fresa.", "../../img/productos/porcion_pay_fresa.jpg"},
                    {"Rebanada Carlota Fresa", "Postres", "45.00", "15", "Porción de carlota de fresa.", "../../img/productos/porcio_carlotta_fresa.jpg"},
                    {"Rebanada Carlota Café", "Postres", "45.00", "15", "Porción de carlota de café.", "../../img/productos/rebanada_carlotta_cafe.jpg"},
                    {"Rebanada Pastel Fresa", "Pasteles", "50.00", "15", "Rebanada de pastel de fresa.", "../../img/productos/rebanada_pastel_fresa.jpg"},
                    {"Rebanada Pastel Imposible", "Pasteles", "60.00", "15", "Rebanada de pastel imposible.", "../../img/productos/rebanada_pastel_imposible.jpg"},
                    {"Rebanada Pastel Red Velvet", "Pasteles", "55.00", "15", "Rebanada de pastel red velvet.", "../../img/productos/rebanada_pastel_red_velvet.jpg"},
                    {"Rebanada Pastel Vainilla", "Pasteles", "45.00", "15", "Rebanada de pastel de vainilla.", "../../img/productos/rebanada_pastel_vainilla.jpg"},
                    {"Rebanada Pay Limón", "Pays", "40.00", "15", "Porción de pay de limón.", "../../img/productos/rebanada_pay_limon.jpg"},
                    {"Rebanada de Tiramisú", "Postres", "55.00", "15", "Porción de tiramisú italiano.", "../../img/productos/rebanada_tiramisu.jpg"},
                    {"Tiramisú", "Postres", "380.00", "4", "Pastel tiramisú tradicional.", "../../img/productos/tiramisu.jpg"},
                    {"Vaso Gelatina Mosaico", "Postres", "25.00", "20", "Vaso individual de gelatina mosaico.", "../../img/productos/vaso_gelatina_mosaico.jpg"}
                };

                for (String[] p : products) {
                    ps.setString(1, p[0]);
                    ps.setString(2, p[1]);
                    ps.setBigDecimal(3, new java.math.BigDecimal(p[2]));
                    ps.setInt(4, Integer.parseInt(p[3]));
                    ps.setString(5, p[4]);
                    ps.setString(6, p[5]);
                    ps.addBatch();
                }
                ps.executeBatch();
                System.out.println("Los 33 productos han sido insertados correctamente con sus imágenes.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

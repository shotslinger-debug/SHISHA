package com.pasteleria;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* =========================================================================
 *  PROYECTO PASTELERIA - TODO EN UN SOLO ARCHIVO
 *  (Main, Database, Models, DAOs, Controllers y Utils)
 *  Encargado de base de datos: se centralizó todo aquí para facilitar
 *  el trabajo en equipo del proyecto escolar.
 * ========================================================================= */

public class Main {

    public static void main(String[] args) {

        // Inicializar tabla de configuración si no existe
        try (java.sql.Connection conn = Database.getConnection(); java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracion_negocio (IdConfiguracion INT AUTO_INCREMENT PRIMARY KEY, NombreNegocio VARCHAR(255), Direccion VARCHAR(255), Telefono VARCHAR(50), EmailContacto VARCHAR(100))");
            stmt.execute("INSERT INTO configuracion_negocio (NombreNegocio, Direccion, Telefono, EmailContacto) SELECT 'Pastelería Shisha', 'Av. Principal 123', '555-1234', 'contacto@shisha.com' WHERE NOT EXISTS (SELECT 1 FROM configuracion_negocio)");
            
            // Parche automático: Arreglar repartidores que se crearon antes del fix de UsuarioDao
            stmt.execute("INSERT INTO repartidor (IdUsuario, Vehiculo, Estado) SELECT IdUsuario, 'No especificado', 'disponible' FROM usuario WHERE Rol = 'repartidor' AND IdUsuario NOT IN (SELECT IdUsuario FROM repartidor)");
            
            // Parche automático: Asegurar que la columna ImagenURL soporta imágenes grandes (a veces se queda en VARCHAR)
            stmt.execute("ALTER TABLE producto MODIFY COLUMN ImagenURL LONGTEXT");
        } catch (Exception e) {
            System.err.println("Error inicializando tabla configuracion_negocio: " + e.getMessage());
        }

        // Instancias de los controladores
        AuthController authController = new AuthController();
        ProductoController productoController = new ProductoController();
        PedidoController pedidoController = new PedidoController();
        IngredienteController ingredienteController = new IngredienteController();
        UsuarioController usuarioController = new UsuarioController();
        FinanzasController finanzasController = new FinanzasController();
        DashboardController dashboardController = new DashboardController();
        ConfiguracionController configuracionController = new ConfiguracionController();
        NotificacionController notificacionController = new NotificacionController();
        ReporteController reporteController = new ReporteController();
        CalificacionController calificacionController = new CalificacionController();
        CarritoController carritoController = new CarritoController();

        Javalin app = Javalin.create(config -> {
            // Aumentar el límite de tamaño de la petición (por defecto 1MB) a 10MB para permitir imágenes en Base64 pesadas
            config.http.maxRequestSize = 10_000_000L;
            
            // Permitir servir la página web (Frontend) directamente desde Java
            config.staticFiles.add("/public", io.javalin.http.staticfiles.Location.CLASSPATH);
            
            // Habilita CORS para que el frontend (web/app) pueda consumir la API sin problema
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.reflectClientOrigin = true;
                    rule.allowCredentials = true;
                });
            });
        });

        // Manejo global de errores no controlados -> siempre responde JSON, nunca HTML
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Error interno del servidor: " + e.getMessage()));
        });

        // ── Rutas de la API ──────────────────────────────────────
        app.post("/api/login", authController::login);
        app.get("/api/session", authController::session);
        app.post("/api/logout", authController::logout);

        app.get("/api/dashboard", dashboardController::obtenerDashboard);

        // ── Carrito ─────────────────────────────────────────────
        app.get("/api/carrito", carritoController::obtener);
        app.post("/api/carrito", carritoController::agregar);
        app.delete("/api/carrito/{id}", carritoController::eliminar);
        app.delete("/api/carrito", carritoController::vaciar);

        app.get("/api/admin/perfil/negocio", configuracionController::obtener);
        app.put("/api/admin/perfil/negocio", configuracionController::actualizar);

        app.get("/api/admin/notificaciones", notificacionController::listar);
        app.put("/api/admin/notificaciones/read-all", notificacionController::marcarTodasLeidas);
        app.put("/api/admin/notificaciones/{id}/read", notificacionController::marcarLeida);

        app.get("/api/admin/reportes", reporteController::obtenerReportes);

        app.get("/api/productos", productoController::listar);
        app.get("/api/productos/{id}", productoController::obtener);
        app.post("/api/productos", productoController::crear);
        app.put("/api/productos/{id}", productoController::actualizar);

        app.post("/api/pedidos", pedidoController::crear);
        app.get("/api/pedidos", pedidoController::listar);
        app.put("/api/pedidos/{id}", pedidoController::actualizar);
        app.post("/api/pedidos/{id}/cancelar", pedidoController::cancelar);

        app.get("/api/ingredientes", ingredienteController::listar);
        app.post("/api/ingredientes", ingredienteController::crear);
        app.put("/api/ingredientes/{id}", ingredienteController::actualizarStock);
        app.put("/api/ingredientes/{id}/editar", ingredienteController::actualizar);

        app.get("/api/usuarios", usuarioController::listar);
        app.get("/api/usuarios/{id}", usuarioController::obtener);
        app.post("/api/usuarios", usuarioController::crear);
        app.put("/api/usuarios/{id}", usuarioController::actualizar);

        app.get("/api/finanzas/movimientos", finanzasController::listarMovimientos);
        app.post("/api/finanzas/egresos", finanzasController::registrarEgreso);
        app.post("/api/finanzas/ingresos", finanzasController::registrarIngreso);
        app.get("/api/finanzas/pagos-pendientes", finanzasController::listarPagosPendientes);
        app.put("/api/finanzas/pagos/{id}/confirmar", finanzasController::confirmarPago);
        app.put("/api/finanzas/pagos/{id}/rechazar", finanzasController::rechazarPago);

        app.get("/api/calificaciones", calificacionController::listarPorProducto);
        app.post("/api/calificaciones", calificacionController::crear);


        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        app.start(port);
        System.out.println("✅ API corriendo en http://localhost:" + port);
    }
}

/* =========================================================================
 *  DATABASE - Pool de conexiones a MySQL (HikariCP)
 * ========================================================================= */
class Database {

    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();

        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String name = System.getenv().getOrDefault("DB_NAME", "shisha_db");
        String user = System.getenv().getOrDefault("DB_USER", "root");
        String pass = System.getenv().getOrDefault("DB_PASS", "D64dce51f6!GABY");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&serverTimezone=America/Mexico_City&allowPublicKeyRetrieval=true";

        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(10);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}

/* =========================================================================
 *  MODELOS
 * ========================================================================= */

class Usuario {
    private Integer id;
    private String nombre;
    private String apellido;
    private String correo;

    // Este campo recibe el password en texto plano al registrar/login,
    // pero JAMÁS se devuelve en las respuestas JSON.
    private String contrasena;

    private String telefono;
    private String rol; // cliente | admin | repartidor | finanzas
    private String direccion;
    private String vehiculo;
    private boolean activo = true;

    public Usuario() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

class Producto {
    private Integer id;
    private String nombre;
    private String categoria;
    private BigDecimal precio;
    private Integer stock;
    private String descripcion;
    private String imagenURL;
    private boolean activo = true;

    public Producto() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getImagenURL() { return imagenURL; }
    public void setImagenURL(String imagenURL) { this.imagenURL = imagenURL; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

class Ingrediente {
    private Integer id;
    private String nombre;
    private String categoria;
    private String proveedor;
    private BigDecimal stock;
    private String unidad;
    private BigDecimal stockMinimo;
    private BigDecimal precioUnitario;

    public Ingrediente() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public BigDecimal getStock() { return stock; }
    public void setStock(BigDecimal stock) { this.stock = stock; }

    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
}

class Pedido {
    private Integer id;
    private Integer idCliente;
    private Integer idRepartidor;
    private String metodoPago;
    private String direccionCalle;
    private String direccionNumero;
    private String direccionDescripcion;
    private String estado; // pendiente | en_preparacion | en_camino | entregado | cancelado
    private BigDecimal total;
    private String fecha;

    // Solo se usa al RECIBIR el POST /api/pedidos (no se guarda tal cual en la tabla pedido)
    private List<DetalleItemRequest> detalle;

    public Pedido() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public Integer getIdRepartidor() { return idRepartidor; }
    public void setIdRepartidor(Integer idRepartidor) { this.idRepartidor = idRepartidor; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getDireccionCalle() { return direccionCalle; }
    public void setDireccionCalle(String direccionCalle) { this.direccionCalle = direccionCalle; }

    public String getDireccionNumero() { return direccionNumero; }
    public void setDireccionNumero(String direccionNumero) { this.direccionNumero = direccionNumero; }

    public String getDireccionDescripcion() { return direccionDescripcion; }
    public void setDireccionDescripcion(String direccionDescripcion) { this.direccionDescripcion = direccionDescripcion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public List<DetalleItemRequest> getDetalle() { return detalle; }
    public void setDetalle(List<DetalleItemRequest> detalle) { this.detalle = detalle; }
}

class DetallePedido {
    private Integer id;
    private Integer idPedido;
    private Integer idProducto;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    public DetallePedido() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}

/**
 * Representa cada objeto del arreglo "detalle" que manda el cliente
 * en el body de POST /api/pedidos:
 * { "idProducto": 1, "cantidad": 2 }
 */
class DetalleItemRequest {
    private Integer idProducto;
    private Integer cantidad;
    private String nombre;

    public DetalleItemRequest() {}

    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}

class MovimientoFinanciero {
    private Integer id;
    private String tipo; // ingreso | egreso
    private BigDecimal monto;
    private String concepto;
    private String categoria;
    private Integer registradoPor;
    private String fecha;
    private Integer idPedido; // null si es un egreso manual
    private String estado;    // Confirmado | Pendiente
    private String metodo;    // Efectivo | Transferencia | Tarjeta

    public MovimientoFinanciero() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Integer getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Integer registradoPor) { this.registradoPor = registradoPor; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
}

class ConfiguracionNegocio {
    private String nombreNegocio;
    private String direccion;
    private String telefono;
    private String emailContacto;

    public String getNombreNegocio() { return nombreNegocio; }
    public void setNombreNegocio(String n) { nombreNegocio = n; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String d) { direccion = d; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String t) { telefono = t; }
    public String getEmailContacto() { return emailContacto; }
    public void setEmailContacto(String e) { emailContacto = e; }
}

class Notificacion {
    private Integer id;
    private String tipo;
    private String mensaje;
    private String fecha;
    private boolean leida;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
}

/* =========================================================================
 *  UTILS
 * ========================================================================= */
class ApiResponse {

    public static Map<String, Object> error(String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }

    public static Map<String, Object> mensaje(String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("mensaje", mensaje);
        return body;
    }
}

/* =========================================================================
 *  INIT DB
 * ========================================================================= */
class DBSetup {
    public static void initialize() {
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
                         
            // ----- PARCHE TEMPORAL PARA LIMPIAR PRODUCTOS SIN IMAGEN -----
            // Borramos los productos 2 al 8 (que eran los de "Sin imagen")
            stmt.execute("DELETE FROM producto WHERE IdProducto BETWEEN 2 AND 8");
            // Borramos cualquier otro producto huérfano sin imagen válida
            stmt.execute("DELETE FROM producto WHERE ImagenURL IS NULL OR ImagenURL = ''");
            // -----------------------------------------------------------
            
            stmt.execute("INSERT INTO `configuracion_negocio` (`NombreNegocio`, `Direccion`, `Telefono`, `EmailContacto`) " +
                         "SELECT 'Shisha Pastelería', 'Av. Principal 123, Centro', '555-1234', 'contacto@shisha.com' " +
                         "WHERE NOT EXISTS (SELECT 1 FROM `configuracion_negocio`)");
                         
            stmt.execute("CREATE TABLE IF NOT EXISTS `carrito` (" +
                         "`IdUsuario` int(11) NOT NULL," +
                         "`IdProducto` int(11) NOT NULL," +
                         "`Cantidad` int(11) NOT NULL DEFAULT 1," +
                         "PRIMARY KEY (`IdUsuario`,`IdProducto`)," +
                         "FOREIGN KEY (`IdUsuario`) REFERENCES `usuario` (`IdUsuario`) ON DELETE CASCADE ON UPDATE CASCADE," +
                         "FOREIGN KEY (`IdProducto`) REFERENCES `producto` (`IdProducto`) ON DELETE CASCADE ON UPDATE CASCADE" +
                         ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;");

            // Crear usuarios por defecto si no existen
            String defaultPass = BCrypt.hashpw("12345678", BCrypt.gensalt());
            
            String insertAdmin = "INSERT INTO `usuario` (`Nombre`, `Apellido`, `Correo`, `Contraseña`, `Telefono`, `Rol`) " +
                                 "SELECT 'Admin', 'Root', 'admin@shisha.com', ?, '000000', 'admin' " +
                                 "WHERE NOT EXISTS (SELECT 1 FROM `usuario` WHERE `Correo` = 'admin@shisha.com')";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertAdmin)) {
                ps.setString(1, defaultPass);
                ps.executeUpdate();
            }

            String insertFinanzas = "INSERT INTO `usuario` (`Nombre`, `Apellido`, `Correo`, `Contraseña`, `Telefono`, `Rol`) " +
                                    "SELECT 'Finanzas', 'Manager', 'finanzas@shisha.com', ?, '000000', 'finanzas' " +
                                    "WHERE NOT EXISTS (SELECT 1 FROM `usuario` WHERE `Correo` = 'finanzas@shisha.com')";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertFinanzas)) {
                ps.setString(1, defaultPass);
                ps.executeUpdate();
            }

            String insertRepartidor = "INSERT INTO `usuario` (`Nombre`, `Apellido`, `Correo`, `Contraseña`, `Telefono`, `Rol`) " +
                                      "SELECT 'Repartidor', 'Express', 'repartidor@shisha.com', ?, '000000', 'repartidor' " +
                                      "WHERE NOT EXISTS (SELECT 1 FROM `usuario` WHERE `Correo` = 'repartidor@shisha.com')";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertRepartidor)) {
                ps.setString(1, defaultPass);
                ps.executeUpdate();
            }
                         
        } catch (Exception e) {
            System.err.println("Error al inicializar la base de datos: " + e.getMessage());
        }
    }
}

/* =========================================================================
 *  DAOs
 * ========================================================================= */

class UsuarioDao {

    /** Busca un usuario por correo (para login). Devuelve null si no existe. */
    public Usuario buscarPorCorreo(String correo) throws SQLException {
        String sql = "SELECT u.IdUsuario as id, u.Nombre as nombre, u.Apellido as apellido, " +
                     "u.Correo as correo, u.Contraseña as contrasena, u.Telefono as telefono, u.Rol as rol, " +
                     "c.Direccion as direccion, r.Vehiculo as vehiculo " +
                     "FROM usuario u " +
                     "LEFT JOIN cliente c ON u.IdUsuario = c.IdUsuario " +
                     "LEFT JOIN repartidor r ON u.IdUsuario = r.IdUsuario " +
                     "WHERE u.Correo = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Usuario buscarPorId(int id) throws SQLException {
        String sql = "SELECT u.IdUsuario as id, u.Nombre as nombre, u.Apellido as apellido, " +
                     "u.Correo as correo, u.Contraseña as contrasena, u.Telefono as telefono, u.Rol as rol, " +
                     "c.Direccion as direccion, r.Vehiculo as vehiculo " +
                     "FROM usuario u " +
                     "LEFT JOIN cliente c ON u.IdUsuario = c.IdUsuario " +
                     "LEFT JOIN repartidor r ON u.IdUsuario = r.IdUsuario " +
                     "WHERE u.IdUsuario = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /** Lista usuarios, opcionalmente filtrando por rol. */
    public List<Usuario> listar(String rol) throws SQLException {
        String sql = "SELECT u.IdUsuario as id, u.Nombre as nombre, u.Apellido as apellido, " +
                     "u.Correo as correo, u.Contraseña as contrasena, u.Telefono as telefono, u.Rol as rol, " +
                     "c.Direccion as direccion, r.Vehiculo as vehiculo " +
                     "FROM usuario u " +
                     "LEFT JOIN cliente c ON u.IdUsuario = c.IdUsuario " +
                     "LEFT JOIN repartidor r ON u.IdUsuario = r.IdUsuario " +
                     (rol != null ? " WHERE u.Rol = ?" : "");
        List<Usuario> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (rol != null) ps.setString(1, rol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    /** Crea un usuario nuevo. Hashea la contraseña con BCrypt antes de guardar. */
    public Usuario crear(Usuario u) throws SQLException {
        String hash = BCrypt.hashpw(u.getContrasena(), BCrypt.gensalt());
        String sql = "INSERT INTO usuario (Nombre, Apellido, Correo, Contraseña, Telefono, Rol) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.getNombre());
                ps.setString(2, u.getApellido());
                ps.setString(3, u.getCorreo());
                ps.setString(4, hash);
                ps.setString(5, u.getTelefono());
                ps.setString(6, u.getRol() != null ? u.getRol() : "cliente");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) u.setId(rs.getInt(1));
                }
                
                if ("cliente".equals(u.getRol()) || u.getRol() == null) {
                    String sql2 = "INSERT INTO cliente (IdUsuario, Direccion) VALUES (?, ?)";
                    try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                        ps2.setInt(1, u.getId());
                        ps2.setString(2, u.getDireccion() != null ? u.getDireccion() : "");
                        ps2.executeUpdate();
                    }
                } else if ("repartidor".equals(u.getRol())) {
                    String sql2 = "INSERT INTO repartidor (IdUsuario, Vehiculo, Estado) VALUES (?, ?, 'disponible')";
                    try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                        ps2.setInt(1, u.getId());
                        ps2.setString(2, u.getVehiculo() != null ? u.getVehiculo() : "");
                        ps2.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        u.setContrasena(null); // nunca regresar el hash al frontend
        return u;
    }

    public boolean actualizar(int id, Usuario u) throws SQLException {
        boolean actualizadoUsuario = false;
        boolean actualizadoCliente = false;
        
        StringBuilder sql = new StringBuilder("UPDATE usuario SET ");
        List<Object> params = new ArrayList<>();

        if (u.getNombre() != null) { sql.append("Nombre = ?, "); params.add(u.getNombre()); }
        if (u.getApellido() != null) { sql.append("Apellido = ?, "); params.add(u.getApellido()); }
        if (u.getCorreo() != null) { sql.append("Correo = ?, "); params.add(u.getCorreo()); }
        if (u.getTelefono() != null) { sql.append("Telefono = ?, "); params.add(u.getTelefono()); }
        if (u.getContrasena() != null) {
            sql.append("Contraseña = ?, ");
            params.add(BCrypt.hashpw(u.getContrasena(), BCrypt.gensalt()));
        }

        if (!params.isEmpty()) {
            sql.setLength(sql.length() - 2); 
            sql.append(" WHERE IdUsuario = ?");
            params.add(id);

            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
                actualizadoUsuario = ps.executeUpdate() > 0;
            }
        }
        
        if (u.getDireccion() != null) {
            try (Connection conn = Database.getConnection()) {
                String updateSql = "UPDATE cliente SET Direccion = ? WHERE IdUsuario = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setString(1, u.getDireccion());
                    psUpdate.setInt(2, id);
                    if (psUpdate.executeUpdate() == 0) {
                        String insertSql = "INSERT INTO cliente (IdUsuario, Direccion) VALUES (?, ?)";
                        try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                            psInsert.setInt(1, id);
                            psInsert.setString(2, u.getDireccion());
                            psInsert.executeUpdate();
                        }
                    }
                }
                actualizadoCliente = true;
            }
        }

        return actualizadoUsuario || actualizadoCliente || u.getDireccion() != null;
    }

    /** Verifica la contraseña en texto plano contra el hash guardado. */
    public boolean verificarPassword(String passwordPlano, String hashGuardado) {
        try {
            return BCrypt.checkpw(passwordPlano, hashGuardado);
        } catch (IllegalArgumentException e) {
            // Si el hash guardado no es de BCrypt (como los que vienen en el .txt), comparamos en texto plano
            return passwordPlano.equals(hashGuardado);
        }
    }

    private Usuario mapRow(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setId(rs.getInt("id"));
        u.setNombre(rs.getString("nombre"));
        u.setApellido(rs.getString("apellido"));
        u.setCorreo(rs.getString("correo"));
        u.setContrasena(rs.getString("contrasena")); 
        u.setTelefono(rs.getString("telefono"));
        u.setRol(rs.getString("rol"));
        u.setDireccion(rs.getString("direccion"));
        u.setVehiculo(rs.getString("vehiculo"));
        u.setActivo(true); // Siempre true, no hay campo en BD
        return u;
    }
}

class ProductoDao {

    public List<Producto> listar(boolean soloActivos) throws SQLException {
        String sql = "SELECT IdProducto as id, Nombre as nombre, Categoria as categoria, Precio as precio, Stock as stock, Descripcion as descripcion, ImagenURL as imagen_url, Activo as activo FROM producto" + (soloActivos ? " WHERE Activo = TRUE" : "");
        List<Producto> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
        }
        return lista;
    }

    public Producto buscarPorId(int id) throws SQLException {
        String sql = "SELECT IdProducto as id, Nombre as nombre, Categoria as categoria, Precio as precio, Stock as stock, Descripcion as descripcion, ImagenURL as imagen_url, Activo as activo FROM producto WHERE IdProducto = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Producto crear(Producto p) throws SQLException {
        String sql = "INSERT INTO producto (Nombre, Categoria, Precio, Stock, Descripcion, ImagenURL, Activo) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getCategoria());
            ps.setBigDecimal(3, p.getPrecio());
            ps.setInt(4, p.getStock() != null ? p.getStock() : 0);
            ps.setString(5, p.getDescripcion());
            ps.setString(6, p.getImagenURL());
            ps.setBoolean(7, p.isActivo());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) p.setId(rs.getInt(1));
            }
        }
        return p;
    }

    public boolean actualizar(int id, Producto p) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE producto SET ");
        List<Object> params = new ArrayList<>();

        if (p.getNombre() != null) { sql.append("Nombre = ?, "); params.add(p.getNombre()); }
        if (p.getCategoria() != null) { sql.append("Categoria = ?, "); params.add(p.getCategoria()); }
        if (p.getPrecio() != null) { sql.append("Precio = ?, "); params.add(p.getPrecio()); }
        if (p.getStock() != null) { sql.append("Stock = ?, "); params.add(p.getStock()); }
        if (p.getDescripcion() != null) { sql.append("Descripcion = ?, "); params.add(p.getDescripcion()); }
        if (p.getImagenURL() != null) { sql.append("ImagenURL = ?, "); params.add(p.getImagenURL()); }
        sql.append("Activo = ?, "); params.add(p.isActivo()); 

        sql.setLength(sql.length() - 2);
        sql.append(" WHERE IdProducto = ?");
        params.add(id);

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            return ps.executeUpdate() > 0;
        }
    }

    private Producto mapRow(ResultSet rs) throws SQLException {
        Producto p = new Producto();
        p.setId(rs.getInt("id"));
        p.setNombre(rs.getString("nombre"));
        p.setCategoria(rs.getString("categoria"));
        p.setPrecio(rs.getBigDecimal("precio"));
        p.setStock(rs.getInt("stock"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setImagenURL(rs.getString("imagen_url"));
        p.setActivo(rs.getBoolean("activo"));
        return p;
    }
}

class IngredienteDao {

    public List<Ingrediente> listar() throws SQLException {
        String sql = "SELECT IdIngrediente as id, Nombre as nombre, Categoria as categoria, Proveedor as proveedor, Stock as stock, UnidadMedida as unidad, StockMinimo, PrecioUnitario FROM ingrediente ORDER BY Nombre";
        List<Ingrediente> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapRow(rs));
        }
        return lista;
    }

    public boolean sumarStock(int id, BigDecimal cantidad) throws SQLException {
        String sql = "UPDATE ingrediente SET Stock = Stock + ? WHERE IdIngrediente = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, cantidad);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Ingrediente crear(Ingrediente ing) throws SQLException {
        String sql = "INSERT INTO ingrediente (Nombre, UnidadMedida, Stock, StockMinimo, PrecioUnitario, Categoria, Proveedor) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ing.getNombre());
            ps.setString(2, ing.getUnidad());
            ps.setBigDecimal(3, ing.getStock() != null ? ing.getStock() : BigDecimal.ZERO);
            ps.setBigDecimal(4, ing.getStockMinimo() != null ? ing.getStockMinimo() : new BigDecimal("3"));
            ps.setBigDecimal(5, ing.getPrecioUnitario());
            ps.setString(6, ing.getCategoria() != null ? ing.getCategoria() : "Otros");
            ps.setString(7, ing.getProveedor() != null ? ing.getProveedor() : "");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) ing.setId(rs.getInt(1));
            }
            return ing;
        }
    }

    public boolean actualizar(int id, Ingrediente ing) throws SQLException {
        String sql = "UPDATE ingrediente SET Nombre=?, UnidadMedida=?, StockMinimo=?, PrecioUnitario=?, Categoria=?, Proveedor=? WHERE IdIngrediente=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ing.getNombre());
            ps.setString(2, ing.getUnidad());
            ps.setBigDecimal(3, ing.getStockMinimo() != null ? ing.getStockMinimo() : new BigDecimal("3"));
            ps.setBigDecimal(4, ing.getPrecioUnitario());
            ps.setString(5, ing.getCategoria() != null ? ing.getCategoria() : "Otros");
            ps.setString(6, ing.getProveedor() != null ? ing.getProveedor() : "");
            ps.setInt(7, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Ingrediente mapRow(ResultSet rs) throws SQLException {
        Ingrediente ing = new Ingrediente();
        ing.setId(rs.getInt("id"));
        ing.setNombre(rs.getString("nombre"));
        ing.setStock(rs.getBigDecimal("stock"));
        ing.setUnidad(rs.getString("unidad"));
        ing.setStockMinimo(rs.getBigDecimal("StockMinimo"));
        ing.setPrecioUnitario(rs.getBigDecimal("PrecioUnitario"));
        
        try {
            ing.setCategoria(rs.getString("categoria"));
            ing.setProveedor(rs.getString("proveedor"));
        } catch(SQLException e) { /* Ignorar si no existen en select particular */ }
        
        return ing;
    }
}

class PedidoDao {

    public Pedido crearPedidoCompleto(Pedido pedido) throws SQLException {
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            BigDecimal total = BigDecimal.ZERO;
            List<Object[]> lineas = new ArrayList<>(); 

            String sqlProducto = "SELECT Precio as precio, Stock as stock FROM producto WHERE IdProducto = ? AND Activo = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(sqlProducto)) {
                for (DetalleItemRequest item : pedido.getDetalle()) {
                    ps.setInt(1, item.getIdProducto());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Producto no encontrado o inactivo: " + item.getIdProducto());
                        }
                        BigDecimal precio = rs.getBigDecimal("precio");
                        int stockActual = rs.getInt("stock");
                        if (stockActual < item.getCantidad()) {
                            throw new SQLException("Stock insuficiente para el producto " + item.getIdProducto());
                        }
                        BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.getCantidad()));
                        total = total.add(subtotal);
                        lineas.add(new Object[]{item.getIdProducto(), item.getCantidad(), precio, subtotal});
                    }
                }
            }

            int idClienteReal = 0;
            String sqlCliente = "SELECT IdCliente FROM cliente WHERE IdUsuario = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCliente)) {
                ps.setInt(1, pedido.getIdCliente());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idClienteReal = rs.getInt("IdCliente");
                    } else {
                        throw new SQLException("El usuario no está registrado como cliente en la BD.");
                    }
                }
            }

            String sqlPedido = "INSERT INTO pedido (IdCliente, MetodoPago, DireccionCalle, DireccionNumero, " +
                    "DireccionDescripcion, Estado, Total) VALUES (?, ?, ?, ?, ?, 'pendiente', ?)";
            int idPedido;
            try (PreparedStatement ps = conn.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idClienteReal);
                ps.setString(2, pedido.getMetodoPago());
                ps.setString(3, pedido.getDireccionCalle());
                ps.setString(4, pedido.getDireccionNumero());
                ps.setString(5, pedido.getDireccionDescripcion());
                ps.setBigDecimal(6, total);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    idPedido = rs.getInt(1);
                }
            }

            String sqlDetalle = "INSERT INTO detalle_pedido (IdPedido, IdProducto, Cantidad, PrecioUnitario) " +
                    "VALUES (?, ?, ?, ?)";
            String sqlStock = "UPDATE producto SET Stock = Stock - ? WHERE IdProducto = ?";
            try (PreparedStatement psDetalle = conn.prepareStatement(sqlDetalle);
                 PreparedStatement psStock = conn.prepareStatement(sqlStock)) {
                for (Object[] linea : lineas) {
                    psDetalle.setInt(1, idPedido);
                    psDetalle.setInt(2, (Integer) linea[0]);
                    psDetalle.setInt(3, (Integer) linea[1]);
                    psDetalle.setBigDecimal(4, (BigDecimal) linea[2]);
                    psDetalle.addBatch();

                    psStock.setInt(1, (Integer) linea[1]);
                    psStock.setInt(2, (Integer) linea[0]);
                    psStock.addBatch();
                }
                psDetalle.executeBatch();
                psStock.executeBatch();
            }

            String sqlPago = "INSERT INTO pago (IdPedido, Metodo, Monto, Estado) VALUES (?, ?, ?, 'pendiente')";
            try (PreparedStatement ps = conn.prepareStatement(sqlPago)) {
                ps.setInt(1, idPedido);
                ps.setString(2, pedido.getMetodoPago());
                ps.setBigDecimal(3, total);
                ps.executeUpdate();
            }

            String sqlFinanzas = "INSERT INTO finanzas_ingreso (Monto, Concepto, RegistradoPor) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlFinanzas)) {
                ps.setBigDecimal(1, total);
                ps.setString(2, "Venta - Pedido #" + idPedido);
                ps.setInt(3, pedido.getIdCliente());
                ps.executeUpdate();
            }

            conn.commit();

            pedido.setId(idPedido);
            pedido.setTotal(total);
            pedido.setEstado("pendiente");
            return pedido;

        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public List<Pedido> listar(Integer idCliente, Integer idRepartidor, String estado) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT p.IdPedido as id, p.IdCliente as id_cliente, r.IdUsuario as id_repartidor, p.MetodoPago as metodo_pago, p.DireccionCalle as direccion_calle, p.DireccionNumero as direccion_numero, p.DireccionDescripcion as direccion_descripcion, p.Estado as estado, p.Total as total, p.FechaPedido as fecha FROM pedido p LEFT JOIN repartidor r ON p.IdRepartidor = r.IdRepartidor WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        System.out.println("DEBUG listar pedidos - Filtros -> idCliente: " + idCliente + ", idRepartidor: " + idRepartidor + ", estado: " + estado);

        if (idCliente != null) { sql.append(" AND p.IdCliente IN (SELECT IdCliente FROM cliente WHERE IdUsuario = ?)"); params.add(idCliente); }
        if (idRepartidor != null) { sql.append(" AND r.IdUsuario = ?"); params.add(idRepartidor); }
        if (estado != null) { sql.append(" AND p.Estado = ?"); params.add(estado); }
        
        sql.append(" ORDER BY p.FechaPedido DESC");
        
        System.out.println("DEBUG listar pedidos - SQL: " + sql.toString());
        System.out.println("DEBUG listar pedidos - Params: " + params);

        List<Pedido> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
             
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pedido p = mapRow(rs);
                    cargarDetalles(conn, p);
                    lista.add(p);
                }
            }
        }
        System.out.println("DEBUG listar pedidos - Resultados: " + lista.size());
        return lista;
    }

    public Pedido buscarPorId(int id) throws SQLException {
        String sql = "SELECT p.IdPedido as id, p.IdCliente as id_cliente, r.IdUsuario as id_repartidor, p.MetodoPago as metodo_pago, p.DireccionCalle as direccion_calle, p.DireccionNumero as direccion_numero, p.DireccionDescripcion as direccion_descripcion, p.Estado as estado, p.Total as total, p.FechaPedido as fecha FROM pedido p LEFT JOIN repartidor r ON p.IdRepartidor = r.IdRepartidor WHERE p.IdPedido = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Pedido p = mapRow(rs);
                    cargarDetalles(conn, p);
                    return p;
                }
            }
        }
        return null;
    }

    public boolean actualizar(int id, Pedido p) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE pedido SET ");
        List<Object> params = new ArrayList<>();

        if (p.getIdRepartidor() != null) { sql.append("IdRepartidor = (SELECT IdRepartidor FROM repartidor WHERE IdUsuario = ?), "); params.add(p.getIdRepartidor()); }
        if (p.getEstado() != null) { sql.append("Estado = ?, "); params.add(p.getEstado()); }

        if (params.isEmpty()) return false;

        sql.setLength(sql.length() - 2);
        sql.append(" WHERE IdPedido = ?");
        params.add(id);

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            return ps.executeUpdate() > 0;
        }
    }

    private Pedido mapRow(ResultSet rs) throws SQLException {
        Pedido p = new Pedido();
        p.setId(rs.getInt("id"));
        p.setIdCliente(rs.getInt("id_cliente"));
        int idRep = rs.getInt("id_repartidor");
        p.setIdRepartidor(rs.wasNull() ? null : idRep);
        p.setMetodoPago(rs.getString("metodo_pago"));
        p.setDireccionCalle(rs.getString("direccion_calle"));
        p.setDireccionNumero(rs.getString("direccion_numero"));
        p.setDireccionDescripcion(rs.getString("direccion_descripcion"));
        p.setEstado(rs.getString("estado"));
        p.setTotal(rs.getBigDecimal("total"));
        java.sql.Timestamp ts = rs.getTimestamp("fecha");
        p.setFecha(ts != null ? ts.toString() : null);
        return p;
    }

    private void cargarDetalles(Connection conn, Pedido p) throws SQLException {
        String sql = "SELECT dp.IdProducto, dp.Cantidad, pr.Nombre FROM detalle_pedido dp " +
                     "JOIN producto pr ON dp.IdProducto = pr.IdProducto " +
                     "WHERE dp.IdPedido = ?";
        List<DetalleItemRequest> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, p.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DetalleItemRequest item = new DetalleItemRequest();
                    item.setIdProducto(rs.getInt("IdProducto"));
                    item.setCantidad(rs.getInt("Cantidad"));
                    item.setNombre(rs.getString("Nombre"));
                    items.add(item);
                }
            }
        }
        p.setDetalle(items);
    }

    public boolean cancelarPedido(int idPedido) throws SQLException {
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            String sqlInfo = "SELECT Estado as estado, Total as total, IdCliente as id_cliente FROM pedido WHERE IdPedido = ?";
            String estado = null;
            BigDecimal total = BigDecimal.ZERO;
            int idCliente = 0;
            try (PreparedStatement ps = conn.prepareStatement(sqlInfo)) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        estado = rs.getString("estado");
                        total = rs.getBigDecimal("total");
                        idCliente = rs.getInt("id_cliente");
                    } else {
                        throw new SQLException("Pedido no encontrado");
                    }
                }
            }

            if ("entregado".equals(estado) || "cancelado".equals(estado)) {
                throw new SQLException("No se puede cancelar un pedido entregado o ya cancelado");
            }

            String sqlUpdate = "UPDATE pedido SET Estado = 'cancelado' WHERE IdPedido = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }

            String sqlDetalle = "SELECT IdProducto as id_producto, Cantidad as cantidad FROM detalle_pedido WHERE IdPedido = ?";
            String sqlStock = "UPDATE producto SET Stock = Stock + ? WHERE IdProducto = ?";
            try (PreparedStatement psDetalle = conn.prepareStatement(sqlDetalle);
                 PreparedStatement psStock = conn.prepareStatement(sqlStock)) {
                psDetalle.setInt(1, idPedido);
                try (ResultSet rs = psDetalle.executeQuery()) {
                    while (rs.next()) {
                        psStock.setInt(1, rs.getInt("cantidad"));
                        psStock.setInt(2, rs.getInt("id_producto"));
                        psStock.addBatch();
                    }
                }
                psStock.executeBatch();
            }

            BigDecimal reembolso = "pendiente".equals(estado) ? total.multiply(new BigDecimal("0.80")) : total.multiply(new BigDecimal("0.50"));
            String sqlReembolso = "INSERT INTO finanzas_egreso (Monto, Concepto, Categoria, RegistradoPor) VALUES (?, ?, 'Reembolsos', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlReembolso)) {
                ps.setBigDecimal(1, reembolso);
                ps.setString(2, "Reembolso - Pedido #" + idPedido + " (" + estado + ")");
                ps.setInt(3, idCliente); 
                ps.executeUpdate();
            }

            String sqlPago = "UPDATE pago SET Estado = 'rechazado' WHERE IdPedido = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlPago)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
}

class FinanzasDao {

    public List<MovimientoFinanciero> listarMovimientos(String tipo) throws SQLException {
        String sqlEgreso =
            "SELECT IdEgreso AS id, 'egreso' AS tipo, Monto AS monto, Concepto AS concepto, " +
            "Categoria AS categoria, RegistradoPor AS registrado_por, Fecha AS fecha, " +
            "NULL AS id_pedido, 'Confirmado' AS estado, 'Efectivo' AS metodo " +
            "FROM finanzas_egreso";

        String sqlIngreso =
            "SELECT fi.IdIngreso AS id, 'ingreso' AS tipo, fi.Monto AS monto, fi.Concepto AS concepto, " +
            "'Venta' AS categoria, fi.RegistradoPor AS registrado_por, fi.Fecha AS fecha, " +
            "NULL AS id_pedido, 'Confirmado' AS estado, 'Transferencia' AS metodo " +
            "FROM finanzas_ingreso fi";

        if ("egreso".equals(tipo)) {
            return ejecutarQuery(sqlEgreso + " ORDER BY fecha DESC", null);
        } else if ("ingreso".equals(tipo)) {
            return ejecutarQuery(sqlIngreso + " ORDER BY fecha DESC", null);
        } else {
            return ejecutarQuery("(" + sqlEgreso + ") UNION ALL (" + sqlIngreso + ") ORDER BY fecha DESC", null);
        }
    }

    private List<MovimientoFinanciero> ejecutarQuery(String sql, String param) throws SQLException {
        List<MovimientoFinanciero> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
        }
        return lista;
    }

    public MovimientoFinanciero registrarEgreso(MovimientoFinanciero m) throws SQLException {
        String sql = "INSERT INTO finanzas_egreso (Monto, Concepto, Categoria, RegistradoPor) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setBigDecimal(1, m.getMonto());
            ps.setString(2, m.getConcepto());
            ps.setString(3, m.getCategoria() != null ? m.getCategoria() : "General");
            ps.setInt(4, m.getRegistradoPor() != null ? m.getRegistradoPor() : 4);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) m.setId(rs.getInt(1));
            }
        }
        m.setTipo("egreso");
        m.setEstado("Confirmado");
        return m;
    }

    public MovimientoFinanciero registrarIngreso(MovimientoFinanciero m) throws SQLException {
        String sql = "INSERT INTO finanzas_ingreso (Monto, Concepto, RegistradoPor) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setBigDecimal(1, m.getMonto());
            ps.setString(2, m.getConcepto());
            ps.setInt(3, m.getRegistradoPor() != null ? m.getRegistradoPor() : 4);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) m.setId(rs.getInt(1));
            }
        }
        m.setTipo("ingreso");
        m.setEstado("Confirmado");
        return m;
    }

    private MovimientoFinanciero mapRow(ResultSet rs) throws SQLException {
        MovimientoFinanciero m = new MovimientoFinanciero();
        m.setId(rs.getInt("id"));
        m.setTipo(rs.getString("tipo"));
        m.setMonto(rs.getBigDecimal("monto"));
        m.setConcepto(rs.getString("concepto"));
        m.setCategoria(rs.getString("categoria"));
        m.setRegistradoPor(rs.getInt("registrado_por"));
        Timestamp ts = rs.getTimestamp("fecha");
        m.setFecha(ts != null ? ts.toString().substring(0, 10) : null);
        int idPedido = rs.getInt("id_pedido");
        m.setIdPedido(rs.wasNull() ? null : idPedido);
        m.setEstado(rs.getString("estado"));
        m.setMetodo(rs.getString("metodo"));
        return m;
    }

    public List<Map<String, Object>> listarPagosPendientes() throws SQLException {
        String sql = "SELECT p.IdPago, p.Monto, p.Metodo, p.Estado, p.FechaPago, p.IdPedido, " +
                     "u.Nombre as nombre, u.Apellido as apellido " +
                     "FROM pago p " +
                     "JOIN pedido ped ON ped.IdPedido = p.IdPedido " +
                     "JOIN cliente c ON c.IdCliente = ped.IdCliente " +
                     "JOIN usuario u   ON u.IdUsuario  = c.IdUsuario " +
                     "WHERE p.Estado = 'pendiente' " +
                     "ORDER BY p.FechaPago DESC";
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id",       rs.getInt("IdPago"));
                row.put("cliente",  rs.getString("nombre") + " " + rs.getString("apellido"));
                row.put("monto",    rs.getBigDecimal("Monto"));
                row.put("metodo",   rs.getString("Metodo"));
                row.put("estado",   rs.getString("Estado"));
                Timestamp ts = rs.getTimestamp("FechaPago");
                row.put("fecha",    ts != null ? ts.toString().substring(0, 10) : "—");
                row.put("pedido",   "#PED-" + rs.getInt("IdPedido"));
                row.put("isNew",    true);
                lista.add(row);
            }
        }
        return lista;
    }

    public void confirmarPago(int idPago) throws SQLException {
        String sql = "UPDATE pago SET Estado = 'confirmado', FechaConfirmacion = NOW() WHERE IdPago = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPago);
            ps.executeUpdate();
        }
        String sqlPed = "UPDATE pedido SET Estado = 'en_preparacion' " +
                        "WHERE IdPedido = (SELECT IdPedido FROM pago WHERE IdPago = ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlPed)) {
            ps.setInt(1, idPago);
            ps.executeUpdate();
        }
    }

    public void rechazarPago(int idPago) throws SQLException {
        String sql = "UPDATE pago SET Estado = 'rechazado' WHERE IdPago = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idPago);
            ps.executeUpdate();
        }
    }
}

class DashboardDao {

    public List<Map<String, Object>> topProductos() throws SQLException {
        String sql = "SELECT p.Nombre as nombre, SUM(dp.Cantidad) AS total_vendido " +
                "FROM detalle_pedido dp JOIN producto p ON p.IdProducto = dp.IdProducto " +
                "GROUP BY p.IdProducto, p.Nombre ORDER BY total_vendido DESC LIMIT 5";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("nombre", rs.getString("nombre"));
                fila.put("totalVendido", rs.getInt("total_vendido"));
                resultado.add(fila);
            }
        }
        return resultado;
    }

    public List<Map<String, Object>> ventasPorDia() throws SQLException {
        String sql = "SELECT DATE(FechaPedido) AS dia, SUM(Total) AS total_dia " +
                "FROM pedido WHERE FechaPedido >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                "GROUP BY DATE(FechaPedido) ORDER BY dia";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("dia", rs.getDate("dia").toString());
                fila.put("totalVentas", rs.getBigDecimal("total_dia"));
                resultado.add(fila);
            }
        }
        return resultado;
    }

    public List<Map<String, Object>> ventasPorCategoria() throws SQLException {
        String sql = "SELECT p.Categoria as categoria, SUM(dp.Subtotal) AS total " +
                "FROM detalle_pedido dp JOIN producto p ON p.IdProducto = dp.IdProducto " +
                "GROUP BY p.Categoria";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("categoria", rs.getString("categoria"));
                fila.put("total", rs.getBigDecimal("total"));
                resultado.add(fila);
            }
        }
        return resultado;
    }

    public List<Map<String, Object>> stockBajo() throws SQLException {
        String sql = "SELECT Nombre, Stock, UnidadMedida FROM ingrediente WHERE Stock < 10 ORDER BY Stock ASC";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("nombre", rs.getString("Nombre"));
                fila.put("stock", rs.getBigDecimal("Stock"));
                fila.put("unidad", rs.getString("UnidadMedida"));
                resultado.add(fila);
            }
        }
        return resultado;
    }
}

class ConfiguracionDao {
    public ConfiguracionNegocio obtener() throws SQLException {
        String sql = "SELECT * FROM configuracion_negocio LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                ConfiguracionNegocio c = new ConfiguracionNegocio();
                c.setNombreNegocio(rs.getString("NombreNegocio"));
                c.setDireccion(rs.getString("Direccion"));
                c.setTelefono(rs.getString("Telefono"));
                c.setEmailContacto(rs.getString("EmailContacto"));
                return c;
            }
        }
        return new ConfiguracionNegocio();
    }

    public boolean actualizar(ConfiguracionNegocio c) throws SQLException {
        String sql = "UPDATE configuracion_negocio SET NombreNegocio=?, Direccion=?, Telefono=?, EmailContacto=? WHERE IdConfiguracion=(SELECT IdConfiguracion FROM (SELECT IdConfiguracion FROM configuracion_negocio LIMIT 1) as t)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getNombreNegocio());
            ps.setString(2, c.getDireccion());
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getEmailContacto());
            return ps.executeUpdate() > 0;
        }
    }
}

class NotificacionDao {
    public List<Notificacion> listarPorUsuario(int idUsuario) throws SQLException {
        String sql = "SELECT IdNotificacion as id, Tipo as tipo, Mensaje as mensaje, FechaNotificacion as fecha, Leida as leida " +
                     "FROM notificacion WHERE EnviadaA = ? ORDER BY FechaNotificacion DESC";
        List<Notificacion> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notificacion n = new Notificacion();
                    n.setId(rs.getInt("id"));
                    n.setTipo(rs.getString("tipo"));
                    n.setMensaje(rs.getString("mensaje"));
                    java.sql.Timestamp ts = rs.getTimestamp("fecha");
                    n.setFecha(ts != null ? ts.toString() : "");
                    n.setLeida(rs.getBoolean("leida"));
                    lista.add(n);
                }
            }
        }
        return lista;
    }

    public boolean marcarLeida(int idNotificacion) throws SQLException {
        String sql = "UPDATE notificacion SET Leida = TRUE WHERE IdNotificacion = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idNotificacion);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean marcarTodasLeidas(int idUsuario) throws SQLException {
        String sql = "UPDATE notificacion SET Leida = TRUE WHERE EnviadaA = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }
    
    public void crear(int enviadaA, String tipo, String mensaje) throws SQLException {
        String sql = "INSERT INTO notificacion (EnviadaA, Tipo, Mensaje) VALUES (?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enviadaA);
            ps.setString(2, tipo);
            ps.setString(3, mensaje);
            ps.executeUpdate();
        }
    }
}

class ReporteDao {
    public Map<String, Object> obtenerEstadisticas(String periodo) throws SQLException {
        int dias = 30; // default
        if (periodo != null) {
            try { dias = Integer.parseInt(periodo); } catch(Exception e){}
        }

        Map<String, Object> reporte = new LinkedHashMap<>();
        
        try (Connection conn = Database.getConnection()) {
            // 1. KPIs
            Map<String, String> kpi = new LinkedHashMap<>();
            String sqlVentas = "SELECT SUM(Total) as t FROM pedido WHERE EstadoPago = 'confirmado' AND FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY)";
            try (PreparedStatement ps = conn.prepareStatement(sqlVentas)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBigDecimal("t") != null) kpi.put("ventas", "$" + String.format("%,.2f", rs.getBigDecimal("t")));
                    else kpi.put("ventas", "$0.00");
                }
            }
            String sqlPedidos = "SELECT COUNT(*) as t FROM pedido WHERE Estado = 'entregado' AND FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY)";
            try (PreparedStatement ps = conn.prepareStatement(sqlPedidos)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) kpi.put("pedidos", String.valueOf(rs.getInt("t")));
                    else kpi.put("pedidos", "0");
                }
            }
            String sqlRating = "SELECT AVG(ProductoEstrellas) as t FROM calificacion c JOIN pedido p ON c.IdPedido = p.IdPedido WHERE p.FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY)";
            try (PreparedStatement ps = conn.prepareStatement(sqlRating)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBigDecimal("t") != null) kpi.put("rating", String.format("%.1f", rs.getFloat("t")));
                    else kpi.put("rating", "N/A");
                }
            }
            reporte.put("kpi", kpi);

            // 2. Bars
            List<Double> bars = new ArrayList<>();
            String sqlBars = "SELECT DATE(FechaPedido) as d, SUM(Total) as t FROM pedido WHERE FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY DATE(FechaPedido) ORDER BY d ASC";
            try (PreparedStatement ps = conn.prepareStatement(sqlBars)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) bars.add(rs.getDouble("t"));
                }
            }
            if(bars.isEmpty()) bars.add(0.0);
            reporte.put("bars", bars);

            // 3. Cats
            List<Map<String, Object>> cats = new ArrayList<>();
            String sqlCats = "SELECT p.Categoria, SUM(dp.Subtotal) as total FROM detalle_pedido dp JOIN producto p ON dp.IdProducto = p.IdProducto JOIN pedido ped ON dp.IdPedido = ped.IdPedido WHERE ped.FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY p.Categoria ORDER BY total DESC LIMIT 5";
            double totalG = 0;
            try (PreparedStatement ps = conn.prepareStatement(sqlCats)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> cat = new LinkedHashMap<>();
                        String name = rs.getString("Categoria");
                        cat.put("name", (name == null || name.isEmpty()) ? "Otros" : name);
                        cat.put("val", rs.getDouble("total"));
                        totalG += rs.getDouble("total");
                        cats.add(cat);
                    }
                }
            }
            for (Map<String, Object> c : cats) {
                double v = (double) c.get("val");
                c.put("pct", totalG > 0 ? (int)((v / totalG) * 100) : 0);
            }
            reporte.put("cats", cats);

            // 4. Top
            List<Map<String, Object>> top = new ArrayList<>();
            String sqlTop = "SELECT p.Nombre, SUM(dp.Cantidad) as s, SUM(dp.Subtotal) as r FROM detalle_pedido dp JOIN producto p ON dp.IdProducto = p.IdProducto JOIN pedido ped ON dp.IdPedido = ped.IdPedido WHERE ped.FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY p.IdProducto ORDER BY s DESC LIMIT 5";
            try (PreparedStatement ps = conn.prepareStatement(sqlTop)) {
                ps.setInt(1, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    while(rs.next()){
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("e", "<img src=\"../../componetes/caja-postre.png\" class=\"png-icon\">");
                        t.put("n", rs.getString("Nombre"));
                        t.put("s", rs.getInt("s"));
                        t.put("r", "$" + String.format("%,.2f", rs.getDouble("r")));
                        top.add(t);
                    }
                }
            }
            reporte.put("top", top);

            // 5. Reps
            List<Map<String, Object>> reps = new ArrayList<>();
            String sqlReps = "SELECT u.Nombre, u.Apellido, r.Estado, COUNT(p.IdPedido) as del, SUM(p.Total) as ing, (SELECT AVG(RepartidorEstrellas) FROM calificacion c JOIN pedido p2 ON c.IdPedido = p2.IdPedido WHERE p2.IdRepartidor = r.IdRepartidor AND p2.FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY)) as rat FROM repartidor r JOIN usuario u ON r.IdUsuario = u.IdUsuario LEFT JOIN pedido p ON r.IdRepartidor = p.IdRepartidor AND p.Estado = 'entregado' AND p.FechaPedido >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY r.IdRepartidor, u.Nombre, u.Apellido, r.Estado";
            try (PreparedStatement ps = conn.prepareStatement(sqlReps)) {
                ps.setInt(1, dias);
                ps.setInt(2, dias);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("n", rs.getString("Nombre") + " " + rs.getString("Apellido"));
                        r.put("st", rs.getString("Estado").equalsIgnoreCase("disponible") ? "Activo" : "En revisión");
                        r.put("del", rs.getInt("del"));
                        r.put("ing", "$" + String.format("%,.2f", rs.getDouble("ing") > 0 ? rs.getDouble("ing") : 0));
                        r.put("rat", rs.getBigDecimal("rat") != null ? String.format("%.1f", rs.getFloat("rat")) : "N/A");
                        reps.add(r);
                    }
                }
            }
            reporte.put("reps", reps);
            reporte.put("label", dias == 7 ? "Últimos 7 días" : (dias == 30 ? "Últimos 30 días" : "Este mes (90 días)"));
        }
        return reporte;
    }
}

/* =========================================================================
 *  CONTROLLERS
 * ========================================================================= */

class AuthController {

    private final UsuarioDao usuarioDao = new UsuarioDao();

    public void login(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String correo = body.get("correo");
            String contrasena = body.get("contraseña") != null ? body.get("contraseña") : body.get("contrasena");

            if (correo == null || contrasena == null) {
                ctx.status(400).json(ApiResponse.error("correo y contraseña son obligatorios"));
                return;
            }

            Usuario usuario = usuarioDao.buscarPorCorreo(correo);
            if (usuario == null || !usuarioDao.verificarPassword(contrasena, usuario.getContrasena())) {
                ctx.status(401).json(ApiResponse.error("Correo o contraseña incorrectos"));
                return;
            }

            if (!usuario.isActivo()) {
                ctx.status(403).json(ApiResponse.error("Este usuario está deshabilitado"));
                return;
            }

            usuario.setContrasena(null); 
            ctx.sessionAttribute("usuario", usuario); // Guardar en sesión HTTP

            String pantalla = switch (usuario.getRol()) {
                case "admin" -> "dashboard_admin";
                case "repartidor" -> "pantalla_envios";
                case "finanzas" -> "pantalla_finanzas";
                default -> "catalogo_cliente";
            };

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("usuario", usuario);
            respuesta.put("redirigirA", pantalla);

            ctx.status(200).json(respuesta);

        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    public void session(Context ctx) {
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario != null) {
            ctx.status(200).json(usuario);
        } else {
            ctx.status(401).json(ApiResponse.error("No hay sesión activa"));
        }
    }

    public void logout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.status(200).json(ApiResponse.mensaje("Sesión cerrada correctamente"));
    }
}

class ProductoController {

    private final ProductoDao productoDao = new ProductoDao();

    /**
     * GET /api/productos
     * GET /api/productos?soloActivos=false  -> el admin puede pedir también los inactivos
     */
    public void listar(Context ctx) {
        try {
            boolean soloActivos = !"false".equalsIgnoreCase(ctx.queryParam("soloActivos"));
            List<Producto> productos = productoDao.listar(soloActivos);
            ctx.status(200).json(productos);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** GET /api/productos/{id} - obtener producto por ID */
    public void obtener(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Producto p = productoDao.buscarPorId(id);
            if (p != null) {
                ctx.status(200).json(p);
            } else {
                ctx.status(404).json(ApiResponse.error("Producto no encontrado"));
            }
        } catch (NumberFormatException e) {
            ctx.status(400).json(ApiResponse.error("ID inválido"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** POST /api/productos - el admin agrega un producto nuevo */
    public void crear(Context ctx) {
        try {
            Producto p = ctx.bodyAsClass(Producto.class);
            if (p.getNombre() == null || p.getPrecio() == null) {
                ctx.status(400).json(ApiResponse.error("nombre y precio son obligatorios"));
                return;
            }
            Producto creado = productoDao.crear(p);
            ctx.status(201).json(creado);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** PUT /api/productos/{id} - editar (precio, nombre, stock) o desactivar (activo: false) */
    public void actualizar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Producto p = ctx.bodyAsClass(Producto.class);
            boolean actualizado = productoDao.actualizar(id, p);
            if (actualizado) {
                ctx.status(200).json(ApiResponse.mensaje("Producto actualizado correctamente"));
            } else {
                ctx.status(404).json(ApiResponse.error("Producto no encontrado"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }
}

class PedidoController {

    private final PedidoDao pedidoDao = new PedidoDao();

    /**
     * POST /api/pedidos
     * El cliente confirma su compra. El backend calcula el total y guarda
     * pedido + detalle_pedido + pago + el ingreso en finanzas, todo en una transacción.
     */
    public void crear(Context ctx) {
        try {
            Pedido pedido = ctx.bodyAsClass(Pedido.class);

            if (pedido.getIdCliente() == null || pedido.getDetalle() == null || pedido.getDetalle().isEmpty()) {
                ctx.status(400).json(ApiResponse.error("idCliente y detalle (con al menos un producto) son obligatorios"));
                return;
            }
            Pedido creado = pedidoDao.crearPedidoCompleto(pedido);
            ctx.status(201).json(creado);

        } catch (SQLException e) {
            ctx.status(400).json(ApiResponse.error("No se pudo crear el pedido: " + e.getMessage()));
        }
    }

    /**
     * GET /api/pedidos
     *   ?idCliente=2                       -> historial del cliente
     *   ?idRepartidor=3&estado=en_camino   -> entregas pendientes del repartidor
     *   (sin filtros)                      -> todos los pedidos (uso del admin)
     */
    public void listar(Context ctx) {
        try {
            Integer idCliente = parseIntOrNull(ctx.queryParam("idCliente"));
            Integer idRepartidor = parseIntOrNull(ctx.queryParam("idRepartidor"));
            String estado = ctx.queryParam("estado");

            List<Pedido> pedidos = pedidoDao.listar(idCliente, idRepartidor, estado);
            ctx.status(200).json(pedidos);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    public void obtener(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            List<Pedido> pedidos = pedidoDao.listar(null, null, null);
            Pedido encontrado = pedidos.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
            if (encontrado != null) {
                ctx.status(200).json(encontrado);
            } else {
                ctx.status(404).json(ApiResponse.error("Pedido no encontrado"));
            }
        } catch (SQLException | NumberFormatException e) {
            ctx.status(500).json(ApiResponse.error("Error al obtener pedido: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/pedidos/{id}
     * Admin: { "idRepartidor": 3, "estado": "en_camino" } -> asigna repartidor
     * Repartidor: { "estado": "entregado" } -> marca como entregado
     */
    public void actualizar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Pedido p = ctx.bodyAsClass(Pedido.class);
            boolean actualizado = pedidoDao.actualizar(id, p);
            if (actualizado) {
                ctx.status(200).json(ApiResponse.mensaje("Pedido actualizado correctamente"));
            } else {
                ctx.status(404).json(ApiResponse.error("Pedido no encontrado o sin cambios"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    public void cancelar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            boolean cancelado = pedidoDao.cancelarPedido(id);
            if (cancelado) {
                ctx.status(200).json(ApiResponse.mensaje("Pedido cancelado exitosamente"));
            } else {
                ctx.status(400).json(ApiResponse.error("No se pudo cancelar el pedido"));
            }
        } catch (SQLException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        }
    }

    private Integer parseIntOrNull(String valor) {
        return valor != null ? Integer.parseInt(valor) : null;
    }
}

class IngredienteController {

    private final IngredienteDao ingredienteDao = new IngredienteDao();

    /** GET /api/ingredientes - el admin ve el stock actual de insumos */
    public void listar(Context ctx) {
        try {
            List<Ingrediente> lista = ingredienteDao.listar();
            ctx.status(200).json(lista);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** POST /api/ingredientes - el admin agrega un nuevo ingrediente */
    public void crear(Context ctx) {
        try {
            Ingrediente ing = ctx.bodyAsClass(Ingrediente.class);
            if (ing.getNombre() == null || ing.getPrecioUnitario() == null) {
                ctx.status(400).json(ApiResponse.error("nombre y precioUnitario son obligatorios"));
                return;
            }
            Ingrediente creado = ingredienteDao.crear(ing);
            ctx.status(201).json(creado);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** PUT /api/ingredientes/{id}/editar - el admin edita datos base del ingrediente */
    public void actualizar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Ingrediente ing = ctx.bodyAsClass(Ingrediente.class);
            boolean actualizado = ingredienteDao.actualizar(id, ing);
            if (actualizado) {
                ctx.status(200).json(ApiResponse.mensaje("Ingrediente actualizado correctamente"));
            } else {
                ctx.status(404).json(ApiResponse.error("Ingrediente no encontrado"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/ingredientes/{id}
     * Body: { "cantidad": 5.5 }  -> suma esa cantidad al stock actual
     */
    public void actualizarStock(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            if (body.get("cantidad") == null) {
                ctx.status(400).json(ApiResponse.error("El campo 'cantidad' es obligatorio"));
                return;
            }
            BigDecimal cantidad = new BigDecimal(body.get("cantidad").toString());
            boolean actualizado = ingredienteDao.sumarStock(id, cantidad);
            if (actualizado) {
                ctx.status(200).json(ApiResponse.mensaje("Stock actualizado correctamente"));
            } else {
                ctx.status(404).json(ApiResponse.error("Ingrediente no encontrado"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }
}

class UsuarioController {

    private final UsuarioDao usuarioDao = new UsuarioDao();

    /**
     * GET /api/usuarios
     * GET /api/usuarios?rol=cliente  -> filtra por rol (cliente, admin, repartidor, finanzas)
     */
    public void listar(Context ctx) {
        try {
            String rol = ctx.queryParam("rol");
            List<Usuario> usuarios = usuarioDao.listar(rol);
            usuarios.forEach(u -> u.setContrasena(null)); // nunca exponer el hash
            ctx.status(200).json(usuarios);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    public void obtener(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Usuario u = usuarioDao.buscarPorId(id);
            if (u != null) {
                u.setContrasena(null);
                ctx.status(200).json(u);
            } else {
                ctx.status(404).json(ApiResponse.error("Usuario no encontrado"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de BD: " + e.getMessage()));
        } catch (NumberFormatException e) {
            ctx.status(400).json(ApiResponse.error("ID inválido"));
        }
    }

    /**
     * POST /api/usuarios
     * Se usa tanto para el registro público de clientes como para que el Admin
     * dé de alta a un repartidor o personal (el campo "rol" define cuál es).
     */
    public void crear(Context ctx) {
        try {
            Usuario u = ctx.bodyAsClass(Usuario.class);

            if (u.getCorreo() == null || u.getContrasena() == null || u.getNombre() == null) {
                ctx.status(400).json(ApiResponse.error("nombre, correo y contraseña son obligatorios"));
                return;
            }

            if (usuarioDao.buscarPorCorreo(u.getCorreo()) != null) {
                ctx.status(409).json(ApiResponse.error("Ya existe un usuario con ese correo"));
                return;
            }

            Usuario creado = usuarioDao.crear(u);
            ctx.status(201).json(creado);

        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/usuarios/{id}
     * Cualquier usuario autenticado actualiza su propio perfil (password, dirección, vehículo, etc.)
     * Solo se actualizan los campos que vengan en el body.
     */
    public void actualizar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Usuario u = ctx.bodyAsClass(Usuario.class);
            boolean actualizado = usuarioDao.actualizar(id, u);
            if (actualizado) {
                // Si el usuario actualizado es el que está logueado, actualizar su sesión
                Usuario sessionUser = ctx.sessionAttribute("usuario");
                if (sessionUser != null && sessionUser.getId() != null && sessionUser.getId() == id) {
                    Usuario actualizadoDb = usuarioDao.buscarPorId(id);
                    if (actualizadoDb != null) {
                        actualizadoDb.setContrasena(null);
                        ctx.sessionAttribute("usuario", actualizadoDb);
                    }
                }
                ctx.status(200).json(ApiResponse.mensaje("Perfil actualizado correctamente"));
            } else {
                ctx.status(404).json(ApiResponse.error("Usuario no encontrado o sin cambios"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }
}

class FinanzasController {

    private final FinanzasDao finanzasDao = new FinanzasDao();

    /**
     * GET /api/finanzas/movimientos
     * GET /api/finanzas/movimientos?tipo=egreso  -> filtra solo ingresos o egresos
     */
    public void listarMovimientos(Context ctx) {
        try {
            String tipo = ctx.queryParam("tipo");
            List<MovimientoFinanciero> lista = finanzasDao.listarMovimientos(tipo);
            ctx.status(200).json(lista);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /**
     * POST /api/finanzas/egresos
     * Finanzas registra un gasto manual (luz, agua, insumos, etc.)
     */
    public void registrarEgreso(Context ctx) {
        try {
            MovimientoFinanciero m = ctx.bodyAsClass(MovimientoFinanciero.class);
            if (m.getMonto() == null || m.getConcepto() == null) {
                ctx.status(400).json(ApiResponse.error("monto y concepto son obligatorios"));
                return;
            }
            MovimientoFinanciero creado = finanzasDao.registrarEgreso(m);
            ctx.status(201).json(creado);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /**
     * POST /api/finanzas/ingresos
     * Registra un ingreso manual (ajuste contable, etc.)
     */
    public void registrarIngreso(Context ctx) {
        try {
            MovimientoFinanciero m = ctx.bodyAsClass(MovimientoFinanciero.class);
            if (m.getMonto() == null || m.getConcepto() == null) {
                ctx.status(400).json(ApiResponse.error("monto y concepto son obligatorios"));
                return;
            }
            MovimientoFinanciero creado = finanzasDao.registrarIngreso(m);
            ctx.status(201).json(creado);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** GET /api/finanzas/pagos-pendientes */
    public void listarPagosPendientes(Context ctx) {
        try {
            ctx.status(200).json(finanzasDao.listarPagosPendientes());
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** PUT /api/finanzas/pagos/{id}/confirmar */
    public void confirmarPago(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            finanzasDao.confirmarPago(id);
            ctx.status(200).json(ApiResponse.mensaje("Pago confirmado correctamente"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }

    /** PUT /api/finanzas/pagos/{id}/rechazar */
    public void rechazarPago(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            finanzasDao.rechazarPago(id);
            ctx.status(200).json(ApiResponse.mensaje("Pago rechazado"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }
}

class DashboardController {

    private final DashboardDao dashboardDao = new DashboardDao();

    /**
     * GET /api/dashboard
     * Una sola llamada que corre las 4 queries para las 4 gráficas y regresa
     * todo listo para que el frontend del Admin pinte sus gráficas.
     */
    public void obtenerDashboard(Context ctx) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("topProductos", dashboardDao.topProductos());          // gráfica 1
            data.put("ventasPorDia", dashboardDao.ventasPorDia());          // gráfica 2
            data.put("ventasPorCategoria", dashboardDao.ventasPorCategoria()); // gráfica 3
            data.put("stockBajo", dashboardDao.stockBajo());                // gráfica 4

            ctx.status(200).json(data);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error de base de datos: " + e.getMessage()));
        }
    }
}

class ConfiguracionController {
    private final ConfiguracionDao dao = new ConfiguracionDao();

    public void obtener(Context ctx) {
        try {
            ctx.status(200).json(dao.obtener());
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }

    public void actualizar(Context ctx) {
        try {
            ConfiguracionNegocio c = ctx.bodyAsClass(ConfiguracionNegocio.class);
            if (dao.actualizar(c)) {
                ctx.status(200).json(ApiResponse.mensaje("Configuración actualizada"));
            } else {
                ctx.status(400).json(ApiResponse.error("No se pudo actualizar"));
            }
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }
}

class NotificacionController {
    private final NotificacionDao dao = new NotificacionDao();

    public void listar(Context ctx) {
        try {
            ctx.status(200).json(dao.listarPorUsuario(1));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }

    public void marcarTodasLeidas(Context ctx) {
        try {
            dao.marcarTodasLeidas(1);
            ctx.status(200).json(ApiResponse.mensaje("Todas marcadas como leídas"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }

    public void marcarLeida(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            dao.marcarLeida(id);
            ctx.status(200).json(ApiResponse.mensaje("Notificación leída"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }
}

class ReporteController {
    private final ReporteDao dao = new ReporteDao();

    public void obtenerReportes(Context ctx) {
        try {
            String period = ctx.queryParam("period");
            Map<String, Object> data = dao.obtenerEstadisticas(period);
            ctx.status(200).json(data);
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }
}

// ==============================
//  MODELOS Y CONTROLADORES DE CALIFICACIONES
// ==============================

class Calificacion {
    private Integer id;
    private Integer idProducto;
    private Integer idPedido;
    private Integer puntuacion;
    private String comentario;
    private String fecha;
    private Integer sabor;
    private Integer presentacion;
    private Integer entrega;
    private Integer precio;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getIdProducto() { return idProducto; }
    public void setIdProducto(Integer idProducto) { this.idProducto = idProducto; }
    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }
    public Integer getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Integer puntuacion) { this.puntuacion = puntuacion; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public Integer getSabor() { return sabor; }
    public void setSabor(Integer sabor) { this.sabor = sabor; }
    public Integer getPresentacion() { return presentacion; }
    public void setPresentacion(Integer presentacion) { this.presentacion = presentacion; }
    public Integer getEntrega() { return entrega; }
    public void setEntrega(Integer entrega) { this.entrega = entrega; }
    public Integer getPrecio() { return precio; }
    public void setPrecio(Integer precio) { this.precio = precio; }
}

class CalificacionDao {
    public void crear(Calificacion c) throws SQLException {
        String sql = "INSERT INTO calificacion (IdProducto, IdPedido, Puntuacion, Comentario, Sabor, Presentacion, Entrega, Precio) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, c.getIdProducto());
            ps.setObject(2, c.getIdPedido());
            ps.setObject(3, c.getPuntuacion());
            ps.setObject(4, c.getComentario());
            ps.setObject(5, c.getSabor());
            ps.setObject(6, c.getPresentacion());
            ps.setObject(7, c.getEntrega());
            ps.setObject(8, c.getPrecio());
            ps.executeUpdate();
        }
    }

    public List<Calificacion> listarPorProducto(int idProducto) throws SQLException {
        List<Calificacion> lista = new ArrayList<>();
        String sql = "SELECT * FROM calificacion WHERE IdProducto = ? ORDER BY Fecha DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Calificacion c = new Calificacion();
                    c.setId(rs.getInt("IdCalificacion"));
                    c.setIdProducto(rs.getInt("IdProducto"));
                    c.setIdPedido(rs.getInt("IdPedido"));
                    c.setPuntuacion(rs.getInt("Puntuacion"));
                    c.setComentario(rs.getString("Comentario"));
                    c.setFecha(rs.getString("Fecha"));
                    c.setSabor(rs.getObject("Sabor") != null ? rs.getInt("Sabor") : null);
                    c.setPresentacion(rs.getObject("Presentacion") != null ? rs.getInt("Presentacion") : null);
                    c.setEntrega(rs.getObject("Entrega") != null ? rs.getInt("Entrega") : null);
                    c.setPrecio(rs.getObject("Precio") != null ? rs.getInt("Precio") : null);
                    lista.add(c);
                }
            }
        }
        return lista;
    }
}

class CalificacionController {
    private final CalificacionDao dao = new CalificacionDao();

    public void crear(Context ctx) {
        try {
            Calificacion c = ctx.bodyAsClass(Calificacion.class);
            dao.crear(c);
            ctx.status(201).json(ApiResponse.mensaje("Calificación guardada exitosamente"));
        } catch (SQLException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error("Datos inválidos: " + e.getMessage()));
        }
    }

    public void listarPorProducto(Context ctx) {
        try {
            String prodStr = ctx.queryParam("productoId");
            if (prodStr == null) {
                ctx.status(400).json(ApiResponse.error("Falta productoId"));
                return;
            }
            int prodId = Integer.parseInt(prodStr);
            ctx.status(200).json(dao.listarPorProducto(prodId));
        } catch (SQLException | NumberFormatException e) {
            ctx.status(500).json(ApiResponse.error("Error BD: " + e.getMessage()));
        }
    }
}

// -- Carrito --

class CarritoItem {
    private int idProducto;
    private int cantidad;
    private Producto producto;

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
}

class CarritoDao {
    public List<CarritoItem> obtenerCarrito(int idUsuario) throws java.sql.SQLException {
        List<CarritoItem> carrito = new java.util.ArrayList<>();
        String sql = "SELECT c.IdProducto, c.Cantidad, p.Nombre, p.Precio, p.ImagenUrl, p.Categoria " +
                     "FROM carrito c JOIN producto p ON c.IdProducto = p.IdProducto WHERE c.IdUsuario = ?";
        try (java.sql.Connection conn = Database.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CarritoItem item = new CarritoItem();
                    item.setIdProducto(rs.getInt("IdProducto"));
                    item.setCantidad(rs.getInt("Cantidad"));
                    
                    Producto p = new Producto();
                    p.setId(rs.getInt("IdProducto"));
                    p.setNombre(rs.getString("Nombre"));
                    p.setPrecio(rs.getBigDecimal("Precio"));
                    p.setImagenURL(rs.getString("ImagenUrl"));
                    p.setCategoria(rs.getString("Categoria"));
                    item.setProducto(p);
                    
                    carrito.add(item);
                }
            }
        }
        return carrito;
    }

    public void agregarItem(int idUsuario, int idProducto, int cantidad) throws java.sql.SQLException {
        String checkSql = "SELECT Cantidad FROM carrito WHERE IdUsuario = ? AND IdProducto = ?";
        try (java.sql.Connection conn = Database.getConnection();
             java.sql.PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, idUsuario);
            checkStmt.setInt(2, idProducto);
            try (java.sql.ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int nuevaCantidad = rs.getInt("Cantidad") + cantidad;
                    String upSql = "UPDATE carrito SET Cantidad = ? WHERE IdUsuario = ? AND IdProducto = ?";
                    try (java.sql.PreparedStatement upStmt = conn.prepareStatement(upSql)) {
                        upStmt.setInt(1, nuevaCantidad);
                        upStmt.setInt(2, idUsuario);
                        upStmt.setInt(3, idProducto);
                        upStmt.executeUpdate();
                    }
                } else {
                    String inSql = "INSERT INTO carrito (IdUsuario, IdProducto, Cantidad) VALUES (?, ?, ?)";
                    try (java.sql.PreparedStatement inStmt = conn.prepareStatement(inSql)) {
                        inStmt.setInt(1, idUsuario);
                        inStmt.setInt(2, idProducto);
                        inStmt.setInt(3, cantidad);
                        inStmt.executeUpdate();
                    }
                }
            }
        }
    }

    public void eliminarItem(int idUsuario, int idProducto) throws java.sql.SQLException {
        String sql = "DELETE FROM carrito WHERE IdUsuario = ? AND IdProducto = ?";
        try (java.sql.Connection conn = Database.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idProducto);
            stmt.executeUpdate();
        }
    }

    public void vaciarCarrito(int idUsuario) throws java.sql.SQLException {
        String sql = "DELETE FROM carrito WHERE IdUsuario = ?";
        try (java.sql.Connection conn = Database.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            stmt.executeUpdate();
        }
    }
}

class CarritoController {
    private final CarritoDao carritoDao = new CarritoDao();

    public void obtener(io.javalin.http.Context ctx) {
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) { ctx.status(401).json(ApiResponse.error("No autorizado")); return; }
        try {
            ctx.status(200).json(carritoDao.obtenerCarrito(usuario.getId()));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public void agregar(io.javalin.http.Context ctx) {
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) { ctx.status(401).json(ApiResponse.error("No autorizado")); return; }
        try {
            CarritoItem req = ctx.bodyAsClass(CarritoItem.class);
            carritoDao.agregarItem(usuario.getId(), req.getIdProducto(), req.getCantidad());
            ctx.status(200).json(ApiResponse.mensaje("Item agregado"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public void eliminar(io.javalin.http.Context ctx) {
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) { ctx.status(401).json(ApiResponse.error("No autorizado")); return; }
        try {
            int idProducto = Integer.parseInt(ctx.pathParam("id"));
            carritoDao.eliminarItem(usuario.getId(), idProducto);
            ctx.status(200).json(ApiResponse.mensaje("Item eliminado"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }

    public void vaciar(io.javalin.http.Context ctx) {
        Usuario usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) { ctx.status(401).json(ApiResponse.error("No autorizado")); return; }
        try {
            carritoDao.vaciarCarrito(usuario.getId());
            ctx.status(200).json(ApiResponse.mensaje("Carrito vaciado"));
        } catch (Exception e) {
            ctx.status(500).json(ApiResponse.error(e.getMessage()));
        }
    }
}


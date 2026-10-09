package py.librarium.conexion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ConexionBD {

    private final String url;

    public ConexionBD(Path archivo) {
        this.url = "jdbc:sqlite:" + archivo.toAbsolutePath();
    }

    public Connection abrir() throws SQLException {
        Connection conexion = DriverManager.getConnection(url);
        try (Statement st = conexion.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conexion;
    }

    /** Crea las tablas y, si la base está vacía, carga los datos iniciales. */
    public void inicializar() throws SQLException, IOException {
        try (Connection conexion = abrir()) {
            ejecutarScript(conexion, "/db/schema.sql");
            if (contarAutores(conexion) > 0) {
                return;
            }
            conexion.setAutoCommit(false);
            try {
                ejecutarScript(conexion, "/db/datos.sql");
                conexion.commit();
            } catch (SQLException | IOException e) {
                conexion.rollback();
                throw e;
            } finally {
                conexion.setAutoCommit(true);
            }
        }
    }

    private int contarAutores(Connection conexion) throws SQLException {
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM autor")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void ejecutarScript(Connection conexion, String recurso) throws SQLException, IOException {
        String sql;
        try (InputStream in = ConexionBD.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IOException("No se encontró " + recurso);
            }
            // el Bloc de notas de Windows a veces agrega un BOM al principio
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        }
        try (Statement st = conexion.createStatement()) {
            for (String sentencia : separarSentencias(sql)) {
                st.execute(sentencia);
            }
        }
    }

    // Separa por ';' sin cortar los textos entre comillas y sin las líneas de comentario
    static List<String> separarSentencias(String sql) {
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : sql.split("\\R")) {
            if (!linea.strip().startsWith("--")) {
                sinComentarios.append(linea).append('\n');
            }
        }

        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean dentroDeTexto = false;
        for (char c : sinComentarios.toString().toCharArray()) {
            if (c == '\'') {
                dentroDeTexto = !dentroDeTexto;
            }
            if (c == ';' && !dentroDeTexto) {
                agregarSiNoEstaVacia(sentencias, actual);
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        agregarSiNoEstaVacia(sentencias, actual);
        return sentencias;
    }

    private static void agregarSiNoEstaVacia(List<String> sentencias, StringBuilder sentencia) {
        String texto = sentencia.toString().strip();
        if (!texto.isEmpty()) {
            sentencias.add(texto);
        }
    }
}

package py.librarium.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import py.librarium.conexion.ConexionBD;
import py.librarium.modelo.Autor;
import py.librarium.modelo.Multimedia;
import py.librarium.modelo.Obra;

public class AutorDAO {

    private static final String SELECT_AUTOR = "SELECT id, nombre, fecha_nacimiento, lugar_nacimiento, "
            + "fallecimiento, destacado, biografia, foto FROM autor";

    private final ConexionBD conexionBD;

    public AutorDAO(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    public List<Autor> listar() throws SQLException {
        try (Connection con = conexionBD.abrir();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(SELECT_AUTOR + " ORDER BY orden, nombre")) {
            List<Autor> autores = new ArrayList<>();
            while (rs.next()) {
                autores.add(mapearAutor(con, rs));
            }
            return autores;
        }
    }

    public Optional<Autor> buscarPorId(String id) throws SQLException {
        try (Connection con = conexionBD.abrir();
             PreparedStatement ps = con.prepareStatement(SELECT_AUTOR + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearAutor(con, rs)) : Optional.empty();
            }
        }
    }

    private Autor mapearAutor(Connection con, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new Autor(
                id,
                rs.getString("nombre"),
                rs.getString("fecha_nacimiento"),
                rs.getString("lugar_nacimiento"),
                rs.getString("fallecimiento"),
                rs.getString("destacado"),
                rs.getString("biografia"),
                rs.getString("foto"),
                listarObras(con, id),
                listarMultimedia(con, id));
    }

    private List<Obra> listarObras(Connection con, String autorId) throws SQLException {
        String sql = "SELECT titulo, anio, genero, descripcion FROM obra WHERE autor_id = ? ORDER BY orden, id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, autorId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Obra> obras = new ArrayList<>();
                while (rs.next()) {
                    int anio = rs.getInt("anio");
                    obras.add(new Obra(
                            rs.getString("titulo"),
                            rs.wasNull() ? null : anio,
                            rs.getString("genero"),
                            rs.getString("descripcion")));
                }
                return obras;
            }
        }
    }

    private List<Multimedia> listarMultimedia(Connection con, String autorId) throws SQLException {
        String sql = "SELECT tipo, url, descripcion FROM multimedia WHERE autor_id = ? ORDER BY id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, autorId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Multimedia> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new Multimedia(rs.getString("tipo"), rs.getString("url"), rs.getString("descripcion")));
                }
                return lista;
            }
        }
    }
}

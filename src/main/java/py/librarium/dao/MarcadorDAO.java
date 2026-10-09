package py.librarium.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import py.librarium.conexion.ConexionBD;
import py.librarium.modelo.Marcador;

public class MarcadorDAO {

    private final ConexionBD conexionBD;

    public MarcadorDAO(ConexionBD conexionBD) {
        this.conexionBD = conexionBD;
    }

    public List<Marcador> listar() throws SQLException {
        try (Connection con = conexionBD.abrir();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, autor_id, imagen FROM marcador ORDER BY id")) {
            List<Marcador> marcadores = new ArrayList<>();
            while (rs.next()) {
                marcadores.add(new Marcador(rs.getString("id"), rs.getString("autor_id"), rs.getString("imagen")));
            }
            return marcadores;
        }
    }
}

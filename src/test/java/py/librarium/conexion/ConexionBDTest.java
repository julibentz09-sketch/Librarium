package py.librarium.conexion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.librarium.dao.AutorDAO;
import py.librarium.dao.MarcadorDAO;
import py.librarium.modelo.Autor;

class ConexionBDTest {

    @TempDir
    Path carpeta;

    @Test
    void separaSentenciasSinCortarTextos() {
        String sql = """
                -- comentario; que se ignora
                INSERT INTO t VALUES ('uno; dos');
                INSERT INTO t VALUES ('tres');
                """;
        List<String> sentencias = ConexionBD.separarSentencias(sql);

        assertEquals(2, sentencias.size());
        assertEquals("INSERT INTO t VALUES ('uno; dos')", sentencias.get(0));
    }

    @Test
    void cargaLosDatosInicialesUnaSolaVez() throws Exception {
        ConexionBD conexion = new ConexionBD(carpeta.resolve("prueba.db"));
        conexion.inicializar();
        conexion.inicializar();

        AutorDAO autorDAO = new AutorDAO(conexion);
        List<Autor> autores = autorDAO.listar();
        assertEquals(4, autores.size());
        assertEquals("roa-bastos", autores.get(0).id());
        assertTrue(autores.stream().allMatch(a -> a.obras().size() == 3));
        assertEquals(4, new MarcadorDAO(conexion).listar().size());

        assertTrue(autorDAO.buscarPorId("josefina-pla").isPresent());
        assertFalse(autorDAO.buscarPorId("no-existe").isPresent());
    }
}

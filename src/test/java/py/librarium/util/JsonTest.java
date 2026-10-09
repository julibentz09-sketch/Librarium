package py.librarium.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import py.librarium.modelo.Obra;

class JsonTest {

    @Test
    void convierteRecords() {
        Obra obra = new Obra("Yo el Supremo", 1974, "Novela", null);

        assertEquals("{\"titulo\":\"Yo el Supremo\",\"anio\":1974,\"genero\":\"Novela\",\"descripcion\":null}",
                Json.convertir(obra));
    }

    @Test
    void convierteMapasYListas() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ok", true);
        datos.put("numeros", List.of(1, 2));

        assertEquals("{\"ok\":true,\"numeros\":[1,2]}", Json.convertir(datos));
    }

    @Test
    void escapaComillasYSaltosDeLinea() {
        assertEquals("\"dijo \\\"hola\\\"\\nchau\"", Json.convertir("dijo \"hola\"\nchau"));
    }
}

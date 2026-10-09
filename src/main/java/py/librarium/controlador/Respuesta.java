package py.librarium.controlador;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import py.librarium.util.Json;

final class Respuesta {

    private Respuesta() {
    }

    static void json(HttpExchange ex, int estado, Object datos) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(ex, estado, "application/json; charset=utf-8", Json.convertir(datos).getBytes(StandardCharsets.UTF_8));
    }

    static void texto(HttpExchange ex, int estado, String texto) throws IOException {
        enviar(ex, estado, "text/plain; charset=utf-8", texto.getBytes(StandardCharsets.UTF_8));
    }

    static void enviar(HttpExchange ex, int estado, String tipo, byte[] cuerpo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.sendResponseHeaders(estado, -1);
            return;
        }
        ex.sendResponseHeaders(estado, cuerpo.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(cuerpo);
        }
    }
}

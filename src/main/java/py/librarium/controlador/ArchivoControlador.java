package py.librarium.controlador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import py.librarium.util.Recursos;

/** Sirve la página web (HTML, CSS, JS e imágenes) que está en src/main/resources/web. */
public class ArchivoControlador implements HttpHandler {

    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "mp3", "audio/mpeg");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String metodo = ex.getRequestMethod();
            if (!metodo.equals("GET") && !metodo.equals("HEAD")) {
                Respuesta.texto(ex, 405, "Método no permitido");
                return;
            }

            String ruta = ex.getRequestURI().getPath();
            if (ruta.equals("/")) {
                ruta = "/index.html";
            }
            if (ruta.contains("..") || ruta.contains("\\")) {
                Respuesta.texto(ex, 404, "No encontrado");
                return;
            }

            try (InputStream in = Recursos.abrir(ruta)) {
                if (in == null) {
                    Respuesta.texto(ex, 404, "No encontrado");
                    return;
                }
                String extension = ruta.substring(ruta.lastIndexOf('.') + 1).toLowerCase();
                ex.getResponseHeaders().set("Cache-Control", "no-cache");
                Respuesta.enviar(ex, 200, TIPOS.getOrDefault(extension, "application/octet-stream"), in.readAllBytes());
            }
        }
    }
}

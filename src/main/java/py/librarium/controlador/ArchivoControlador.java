package py.librarium.controlador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import py.librarium.util.Recursos;

/** Sirve la página web (HTML, CSS, JS, imágenes y modelos 3D) que está en src/main/resources/web. */
public class ArchivoControlador implements HttpHandler {

    private static final Map<String, String> TIPOS = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("glb", "model/gltf-binary"));

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

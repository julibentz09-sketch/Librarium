package py.librarium.controlador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import py.librarium.dao.AutorDAO;
import py.librarium.modelo.Autor;
import py.librarium.servicio.ReconocedorImagen;

/**
 * GET  /api/autores        lista de autores con sus obras
 * GET  /api/autores/{id}   un autor
 * POST /api/reconocer      recibe la imagen del marco de la cámara y dice qué autor es
 * GET  /api/info           direcciones para abrir la app desde el celular
 */
public class ApiControlador implements HttpHandler {

    private static final int TAMANO_MAXIMO_IMAGEN = 2 * 1024 * 1024;

    private final AutorDAO autorDAO;
    private final ReconocedorImagen reconocedor;
    private final Map<String, Object> info = new LinkedHashMap<>();

    public ApiControlador(AutorDAO autorDAO, ReconocedorImagen reconocedor,
                          List<String> direccionesCelular, boolean httpsActivo, int puertoHttps) {
        this.autorDAO = autorDAO;
        this.reconocedor = reconocedor;
        info.put("urlsCelular", direccionesCelular);
        info.put("https", httpsActivo);
        info.put("puertoHttps", puertoHttps);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String ruta = ex.getRequestURI().getPath();
            String metodo = ex.getRequestMethod();
            try {
                if (metodo.equals("GET") && ruta.equals("/api/autores")) {
                    Respuesta.json(ex, 200, autorDAO.listar());
                } else if (metodo.equals("GET") && ruta.startsWith("/api/autores/")) {
                    buscarAutor(ex, ruta.substring("/api/autores/".length()));
                } else if (metodo.equals("POST") && ruta.equals("/api/reconocer")) {
                    reconocer(ex);
                } else if (metodo.equals("GET") && ruta.equals("/api/info")) {
                    Respuesta.json(ex, 200, info);
                } else {
                    Respuesta.json(ex, 404, Map.of("error", "Ruta no encontrada"));
                }
            } catch (Exception e) {
                System.err.println("Error en " + metodo + " " + ruta + ": " + e);
                Respuesta.json(ex, 500, Map.of("error", "Error interno del servidor"));
            }
        }
    }

    private void buscarAutor(HttpExchange ex, String id) throws Exception {
        Optional<Autor> autor = autorDAO.buscarPorId(id);
        if (autor.isPresent()) {
            Respuesta.json(ex, 200, autor.get());
        } else {
            Respuesta.json(ex, 404, Map.of("error", "Autor no encontrado"));
        }
    }

    private void reconocer(HttpExchange ex) throws IOException {
        byte[] cuerpo = ex.getRequestBody().readNBytes(TAMANO_MAXIMO_IMAGEN + 1);
        if (cuerpo.length > TAMANO_MAXIMO_IMAGEN) {
            Respuesta.json(ex, 413, Map.of("error", "Imagen demasiado grande"));
            return;
        }
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(cuerpo));
        if (imagen == null) {
            Respuesta.json(ex, 400, Map.of("error", "La imagen no es válida"));
            return;
        }

        ReconocedorImagen.Resultado resultado = reconocedor.analizar(imagen);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("coincide", resultado.coincide());
        respuesta.put("autorId", resultado.coincide() ? resultado.autorId() : null);
        respuesta.put("puntaje", Math.round(resultado.puntaje() * 1000) / 1000.0);
        respuesta.put("umbral", ReconocedorImagen.UMBRAL);
        Respuesta.json(ex, 200, respuesta);
    }
}

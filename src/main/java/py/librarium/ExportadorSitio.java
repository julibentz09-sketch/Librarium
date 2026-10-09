package py.librarium;

import java.nio.file.Files;
import java.nio.file.Path;
import py.librarium.conexion.ConexionBD;
import py.librarium.dao.AutorDAO;
import py.librarium.dao.MarcadorDAO;
import py.librarium.util.Json;

/**
 * Genera los datos de la versión estática para GitHub Pages, donde no corre el servidor.
 * Uso: java -cp librarium.jar py.librarium.ExportadorSitio carpeta-del-sitio
 */
public class ExportadorSitio {

    public static void main(String[] args) throws Exception {
        Path destino = Path.of(args.length > 0 ? args[0] : "sitio");
        Path carpetaDatos = destino.resolve("datos");
        Files.createDirectories(carpetaDatos);

        Path baseTemporal = Files.createTempFile("librarium", ".db");
        try {
            ConexionBD conexion = new ConexionBD(baseTemporal);
            conexion.inicializar();
            Files.writeString(carpetaDatos.resolve("autores.json"), Json.convertir(new AutorDAO(conexion).listar()));
            Files.writeString(carpetaDatos.resolve("marcadores.json"), Json.convertir(new MarcadorDAO(conexion).listar()));
        } finally {
            Files.deleteIfExists(baseTemporal);
        }
        System.out.println("Datos del sitio generados en " + carpetaDatos.toAbsolutePath());
    }
}

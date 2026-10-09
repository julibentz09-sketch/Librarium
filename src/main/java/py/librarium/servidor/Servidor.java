package py.librarium.servidor;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import py.librarium.controlador.ApiControlador;
import py.librarium.controlador.ArchivoControlador;
import py.librarium.dao.AutorDAO;
import py.librarium.servicio.ReconocedorImagen;
import py.librarium.util.Red;

/**
 * Levanta la app en dos puertos:
 * HTTP para usarla en la misma computadora (localhost) y HTTPS para el celular,
 * porque los navegadores del celular solo dejan usar la cámara en páginas seguras.
 */
public class Servidor {

    private final int puertoHttp;
    private final int puertoHttps;
    private final List<String> direccionesCelular = new ArrayList<>();

    public Servidor(int puertoHttp, int puertoHttps) {
        this.puertoHttp = puertoHttp;
        this.puertoHttps = puertoHttps;
    }

    public void iniciar(AutorDAO autorDAO, ReconocedorImagen reconocedor) throws IOException {
        List<String> ips = Red.ipsLocales();
        HttpServer http = HttpServer.create(new InetSocketAddress(puertoHttp), 0);
        HttpsServer https = crearServidorHttps(ips);

        if (https != null) {
            for (String ip : ips) {
                direccionesCelular.add("https://" + ip + ":" + puertoHttps);
            }
        }

        ApiControlador api = new ApiControlador(autorDAO, reconocedor, direccionesCelular, https != null, puertoHttps);
        ArchivoControlador archivos = new ArchivoControlador();
        ExecutorService hilos = Executors.newFixedThreadPool(16);

        for (HttpServer servidor : https == null ? List.of(http) : List.of(http, https)) {
            servidor.createContext("/api/", api);
            servidor.createContext("/", archivos);
            servidor.setExecutor(hilos);
            servidor.start();
        }
    }

    public List<String> direccionesCelular() {
        return direccionesCelular;
    }

    private HttpsServer crearServidorHttps(List<String> ips) {
        try {
            SSLContext ssl = CertificadoSSL.crearContexto(Path.of("librarium-cert.p12"), ips);
            HttpsServer https = HttpsServer.create(new InetSocketAddress(puertoHttps), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(ssl));
            return https;
        } catch (Exception e) {
            System.err.println("Aviso: no se pudo iniciar HTTPS (" + e.getMessage() + ").");
            System.err.println("La app funciona en esta computadora, pero el celular no va a poder usar la cámara.");
            return null;
        }
    }
}

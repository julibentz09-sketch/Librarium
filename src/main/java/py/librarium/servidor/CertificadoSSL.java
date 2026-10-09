package py.librarium.servidor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Certificado autofirmado para HTTPS en la red local. Se genera una sola vez con keytool (viene con el JDK). */
class CertificadoSSL {

    private static final String CLAVE = "librarium";

    private CertificadoSSL() {
    }

    static SSLContext crearContexto(Path almacen, List<String> ips) throws Exception {
        if (!Files.exists(almacen)) {
            generar(almacen, ips);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(almacen)) {
            ks.load(in, CLAVE.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, CLAVE.toCharArray());
        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(kmf.getKeyManagers(), null, null);
        return contexto;
    }

    private static void generar(Path almacen, List<String> ips) throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
        if (!Files.exists(keytool)) {
            throw new IOException("no se encontró keytool en " + keytool.getParent());
        }

        StringBuilder nombres = new StringBuilder("SAN=dns:localhost,ip:127.0.0.1");
        for (String ip : ips) {
            nombres.append(",ip:").append(ip);
        }
        List<String> comando = new ArrayList<>(List.of(
                keytool.toString(), "-genkeypair",
                "-alias", "librarium",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650",
                "-storetype", "PKCS12",
                "-keystore", almacen.toString(),
                "-storepass", CLAVE,
                "-keypass", CLAVE,
                "-dname", "CN=Librarium, O=Librarium, C=PY",
                "-ext", nombres.toString()));

        Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (proceso.waitFor() != 0) {
            Files.deleteIfExists(almacen);
            throw new IOException("keytool falló: " + salida.strip());
        }
    }
}

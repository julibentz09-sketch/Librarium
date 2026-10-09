package py.librarium;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import py.librarium.conexion.ConexionBD;
import py.librarium.dao.AutorDAO;
import py.librarium.dao.MarcadorDAO;
import py.librarium.modelo.Marcador;
import py.librarium.servicio.ReconocedorImagen;
import py.librarium.servidor.Servidor;
import py.librarium.util.Recursos;

public class Librarium {

    public static void main(String[] args) {
        configurarConsola();

        int puertoHttp = 8080;
        int puertoHttps = 8443;
        boolean abrirNavegador = true;
        for (String arg : args) {
            if (arg.startsWith("--puerto=")) {
                puertoHttp = Integer.parseInt(arg.substring("--puerto=".length()));
            } else if (arg.startsWith("--puerto-https=")) {
                puertoHttps = Integer.parseInt(arg.substring("--puerto-https=".length()));
            } else if (arg.equals("--sin-navegador")) {
                abrirNavegador = false;
            }
        }

        try {
            ConexionBD conexion = new ConexionBD(Path.of("librarium.db"));
            conexion.inicializar();
            AutorDAO autorDAO = new AutorDAO(conexion);
            MarcadorDAO marcadorDAO = new MarcadorDAO(conexion);

            ReconocedorImagen reconocedor = new ReconocedorImagen();
            for (Marcador marcador : marcadorDAO.listar()) {
                reconocedor.agregarReferencia(marcador.autorId(), Recursos.leerImagen(marcador.imagen()));
            }

            Servidor servidor = new Servidor(puertoHttp, puertoHttps);
            servidor.iniciar(autorDAO, reconocedor);

            mostrarBienvenida(autorDAO.listar().size(), reconocedor.cantidadReferencias(),
                    "http://localhost:" + puertoHttp, servidor.direccionesCelular());
            if (abrirNavegador) {
                abrirEnNavegador("http://localhost:" + puertoHttp);
            }
        } catch (BindException e) {
            System.err.println("El puerto " + puertoHttp + " ya está en uso. ¿Librarium ya está abierto?");
            System.err.println("Cerralo o iniciá con otro puerto: java -jar librarium.jar --puerto=8081");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("No se pudo iniciar Librarium: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void mostrarBienvenida(int autores, int marcadores, String direccionLocal, List<String> direccionesCelular) {
        System.out.println();
        System.out.println("  ==================================================");
        System.out.println("   LIBRARIUM - Literatura Paraguaya Interactiva");
        System.out.println("  ==================================================");
        System.out.println("   Autores: " + autores + "   Marcadores RA: " + marcadores);
        System.out.println();
        System.out.println("   En esta computadora:   " + direccionLocal);
        if (!direccionesCelular.isEmpty()) {
            System.out.println("   En el celular (misma red WiFi):");
            for (String direccion : direccionesCelular) {
                System.out.println("       " + direccion);
            }
            System.out.println("   Si el celular muestra un aviso de seguridad,");
            System.out.println("   tocá \"Avanzado\" y luego \"Continuar\".");
        }
        System.out.println();
        System.out.println("   Para detener el servidor: Ctrl + C");
        System.out.println("  ==================================================");
    }

    private static void abrirEnNavegador(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.out.println("   Abrí " + url + " en el navegador.");
        }
    }

    // Con Java 17 en la consola de Windows los acentos salen mal si no se usa la codificación de la consola
    private static void configurarConsola() {
        String codificacion = System.getProperty("stdout.encoding", System.getProperty("sun.stdout.encoding"));
        if (codificacion == null) {
            return;
        }
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, codificacion));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, codificacion));
        } catch (UnsupportedEncodingException e) {
            // se queda con la salida por defecto
        }
    }
}

package py.librarium.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Acceso a los archivos de la carpeta web empaquetados dentro del jar. */
public final class Recursos {

    private Recursos() {
    }

    public static InputStream abrir(String ruta) {
        String limpia = ruta.startsWith("/") ? ruta : "/" + ruta;
        return Recursos.class.getResourceAsStream("/web" + limpia);
    }

    public static BufferedImage leerImagen(String ruta) throws IOException {
        try (InputStream in = abrir(ruta)) {
            if (in == null) {
                throw new IOException("No se encontró la imagen " + ruta);
            }
            BufferedImage imagen = ImageIO.read(in);
            if (imagen == null) {
                throw new IOException("Formato de imagen no soportado: " + ruta);
            }
            return imagen;
        }
    }
}

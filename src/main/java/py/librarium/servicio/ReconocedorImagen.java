package py.librarium.servicio;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reconoce la foto de un autor en la imagen que manda la cámara.
 *
 * Cada recorte se reduce a 32x32 y se compara con las fotos de referencia usando:
 * la imagen en gris normalizada (correlación), un histograma de orientación de bordes
 * por zonas (HOG) y el color de cada zona. El navegador manda el marco verde con un
 * margen, así que se prueban varios tamaños y posiciones del recorte.
 */
public class ReconocedorImagen {

    public static final double UMBRAL = 0.50;
    public static final double MARGEN = 0.08;

    private static final int LADO = 32;
    private static final int ZONAS = 4;
    private static final int ORIENTACIONES = 8;
    private static final double CONTRASTE_MINIMO = 6.0;
    private static final double[] ESCALAS = {1.0, 0.87, 0.76, 0.66, 0.57, 0.49, 0.42};
    private static final double[] CORRIMIENTOS = {-0.12, 0, 0.12};
    private static final double TOLERANCIA_COLOR = 6.0;
    private static final double ESCALA_COLOR = 40.0;

    public record Resultado(String autorId, double puntaje, double segundo, boolean coincide) {
    }

    private record Firma(float[] gris, float[] bordes, float[] color) {
    }

    private record Referencia(String autorId, double proporcion, Firma firma) {
    }

    private final List<Referencia> referencias = new CopyOnWriteArrayList<>();

    public void agregarReferencia(String autorId, BufferedImage foto) {
        ImagenGris imagen = ImagenGris.desde(foto);
        Firma firma = calcularFirma(imagen, 0, 0, imagen.ancho, imagen.alto);
        if (firma == null) {
            throw new IllegalArgumentException("La foto de " + autorId + " no tiene contraste suficiente");
        }
        referencias.add(new Referencia(autorId, (double) imagen.ancho / imagen.alto, firma));
    }

    public int cantidadReferencias() {
        return referencias.size();
    }

    public Resultado analizar(BufferedImage cuadro) {
        ImagenGris imagen = ImagenGris.desde(cuadro);
        String mejorId = null;
        double mejor = 0;
        double segundo = 0;

        for (Referencia ref : referencias) {
            double puntaje = buscar(imagen, ref);
            if (puntaje > mejor) {
                segundo = mejor;
                mejor = puntaje;
                mejorId = ref.autorId();
            } else if (puntaje > segundo) {
                segundo = puntaje;
            }
        }
        boolean coincide = mejorId != null && mejor >= UMBRAL && mejor - segundo >= MARGEN;
        return new Resultado(mejorId, mejor, segundo, coincide);
    }

    private double buscar(ImagenGris imagen, Referencia ref) {
        // el rectángulo más grande con la proporción de la foto que entra en la imagen
        double baseAncho = imagen.ancho;
        double baseAlto = imagen.ancho / ref.proporcion();
        if (baseAlto > imagen.alto) {
            baseAlto = imagen.alto;
            baseAncho = imagen.alto * ref.proporcion();
        }

        double mejor = 0;
        for (double escala : ESCALAS) {
            double w = baseAncho * escala;
            double h = baseAlto * escala;
            for (double dy : CORRIMIENTOS) {
                for (double dx : CORRIMIENTOS) {
                    double x = Math.max(0, Math.min(imagen.ancho - w, (imagen.ancho - w) / 2 + dx * w));
                    double y = Math.max(0, Math.min(imagen.alto - h, (imagen.alto - h) / 2 + dy * h));
                    Firma firma = calcularFirma(imagen, x, y, w, h);
                    if (firma != null) {
                        mejor = Math.max(mejor, comparar(firma, ref.firma()));
                    }
                }
            }
        }
        return mejor;
    }

    // El color solo resta si la cámara ve color donde la foto no tiene (una cara real frente
    // a una foto en blanco y negro). Al revés no resta: una foto impresa sin color se sigue reconociendo.
    private double comparar(Firma cuadro, Firma referencia) {
        double forma = 0.5 * productoPunto(cuadro.gris(), referencia.gris())
                + 0.5 * productoPunto(cuadro.bordes(), referencia.bordes());
        double exceso = 0;
        for (int i = 0; i < cuadro.color().length; i++) {
            exceso += Math.max(0, cuadro.color()[i] - referencia.color()[i] - TOLERANCIA_COLOR);
        }
        return forma - Math.min(0.5, exceso / (cuadro.color().length * ESCALA_COLOR));
    }

    private Firma calcularFirma(ImagenGris imagen, double x, double y, double w, double h) {
        float[] p = imagen.reducir(imagen.gris, x, y, w, h, LADO, LADO);
        if (desvioEstandar(p) < CONTRASTE_MINIMO) {
            return null;
        }
        return new Firma(normalizar(p), normalizar(histogramaBordes(p)), colorPorZona(imagen, x, y, w, h));
    }

    private float[] histogramaBordes(float[] p) {
        float[] hist = new float[ZONAS * ZONAS * ORIENTACIONES];
        int tamZona = LADO / ZONAS;
        for (int y = 1; y < LADO - 1; y++) {
            for (int x = 1; x < LADO - 1; x++) {
                float gx = p[y * LADO + x + 1] - p[y * LADO + x - 1];
                float gy = p[(y + 1) * LADO + x] - p[(y - 1) * LADO + x];
                double magnitud = Math.hypot(gx, gy);
                if (magnitud == 0) {
                    continue;
                }
                double angulo = Math.atan2(gy, gx);
                if (angulo < 0) {
                    angulo += Math.PI;
                }
                int orientacion = Math.min(ORIENTACIONES - 1, (int) (angulo / Math.PI * ORIENTACIONES));
                int zona = (y / tamZona) * ZONAS + x / tamZona;
                hist[zona * ORIENTACIONES + orientacion] += (float) magnitud;
            }
        }
        return hist;
    }

    // Se resta el tono promedio para compensar la luz cálida o fría y el color del papel
    private float[] colorPorZona(ImagenGris imagen, double x, double y, double w, double h) {
        float[] u = imagen.reducir(imagen.u, x, y, w, h, ZONAS, ZONAS);
        float[] v = imagen.reducir(imagen.v, x, y, w, h, ZONAS, ZONAS);
        double promedioU = promedio(u);
        double promedioV = promedio(v);
        float[] color = new float[u.length];
        for (int i = 0; i < color.length; i++) {
            color[i] = (float) Math.hypot(u[i] - promedioU, v[i] - promedioV);
        }
        return color;
    }

    // Resta el promedio y divide por la norma: así el producto punto es la correlación
    private static float[] normalizar(float[] valores) {
        double media = promedio(valores);
        float[] r = new float[valores.length];
        double norma = 0;
        for (int i = 0; i < valores.length; i++) {
            r[i] = (float) (valores[i] - media);
            norma += r[i] * r[i];
        }
        norma = Math.sqrt(norma);
        if (norma > 0) {
            for (int i = 0; i < r.length; i++) {
                r[i] /= norma;
            }
        }
        return r;
    }

    private static double promedio(float[] valores) {
        double suma = 0;
        for (float v : valores) {
            suma += v;
        }
        return suma / valores.length;
    }

    private static double desvioEstandar(float[] valores) {
        double media = promedio(valores);
        double suma = 0;
        for (float v : valores) {
            suma += (v - media) * (v - media);
        }
        return Math.sqrt(suma / valores.length);
    }

    private static double productoPunto(float[] a, float[] b) {
        double suma = 0;
        for (int i = 0; i < a.length; i++) {
            suma += a[i] * b[i];
        }
        return suma;
    }
}

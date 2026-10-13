package py.librarium.servicio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import nu.pattern.OpenCV;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Reconoce la foto de un autor en la imagen que manda la cámara, con OpenCV.
 *
 * 1. ORB busca puntos clave (esquinas y detalles) en la foto de referencia y en la imagen.
 * 2. BFMatcher empareja los puntos por su descriptor (distancia de Hamming) y se descartan
 *    los dudosos con la prueba de Lowe (el mejor tiene que ser claramente mejor que el segundo).
 * 3. findHomography con RANSAC comprueba que los pares encajen como una misma foto plana vista
 *    desde la cámara (aunque esté girada o inclinada). Los que encajan son los "inliers".
 *
 * El autor con más inliers gana si tiene suficientes y le saca ventaja al segundo.
 */
public class ReconocedorImagen {

    /** Puntaje mínimo para reconocer (puntaje = inliers / INLIERS_PUNTAJE_COMPLETO, máximo 1). */
    public static final double UMBRAL = 0.50;
    /** Inliers que dan puntaje 1. Con UMBRAL 0.5 hacen falta 15 inliers. */
    static final int INLIERS_PUNTAJE_COMPLETO = 30;
    /** El ganador tiene que tener al menos el doble de inliers que el segundo autor. */
    static final double VENTAJA_SOBRE_SEGUNDO = 2.0;

    private static final int PUNTOS_ORB = 1000;
    private static final int LADO_MAXIMO = 480;
    private static final double PRUEBA_DE_LOWE = 0.75;
    private static final int PARES_MINIMOS = 8;
    private static final double ERROR_RANSAC = 5.0;
    private static final double AREA_MINIMA = 0.01;

    static {
        OpenCV.loadLocally();
    }

    /**
     * @param esquinas contorno de la foto encontrada, 4 puntos [x, y] entre 0 y 1 relativos a la imagen
     *                 (null si no coincide)
     */
    public record Resultado(String autorId, double puntaje, int inliers, int segundo, boolean coincide,
                            List<double[]> esquinas) {
    }

    private record Referencia(String autorId, int ancho, int alto, MatOfKeyPoint puntos, Mat descriptores) {
    }

    private record Comparacion(int inliers, List<double[]> esquinas) {
    }

    private final List<Referencia> referencias = new CopyOnWriteArrayList<>();

    public void agregarReferencia(String autorId, byte[] imagen) {
        Mat gris = decodificar(imagen);
        if (gris == null) {
            throw new IllegalArgumentException("La foto de " + autorId + " no es una imagen válida");
        }
        agregarReferencia(autorId, gris);
    }

    public void agregarReferencia(String autorId, BufferedImage foto) {
        agregarReferencia(autorId, aBytes(foto));
    }

    private void agregarReferencia(String autorId, Mat gris) {
        ORB orb = ORB.create(PUNTOS_ORB);
        MatOfKeyPoint puntos = new MatOfKeyPoint();
        Mat descriptores = new Mat();
        orb.detectAndCompute(gris, new Mat(), puntos, descriptores);
        if (descriptores.rows() < PARES_MINIMOS) {
            throw new IllegalArgumentException("La foto de " + autorId + " no tiene detalles suficientes");
        }
        referencias.add(new Referencia(autorId, gris.cols(), gris.rows(), puntos, descriptores));
        gris.release();
    }

    public int cantidadReferencias() {
        return referencias.size();
    }

    /** Analiza una imagen JPEG o PNG. Devuelve null si los bytes no son una imagen. */
    public Resultado analizar(byte[] imagen) {
        Mat gris = decodificar(imagen);
        if (gris == null) {
            return null;
        }
        try {
            return analizar(gris);
        } finally {
            gris.release();
        }
    }

    public Resultado analizar(BufferedImage cuadro) {
        return analizar(aBytes(cuadro));
    }

    private Resultado analizar(Mat gris) {
        ORB orb = ORB.create(PUNTOS_ORB);
        MatOfKeyPoint puntos = new MatOfKeyPoint();
        Mat descriptores = new Mat();
        try {
            orb.detectAndCompute(gris, new Mat(), puntos, descriptores);
            String mejorId = null;
            Comparacion mejor = new Comparacion(0, null);
            int segundo = 0;
            if (descriptores.rows() >= PARES_MINIMOS) {
                KeyPoint[] puntosCuadro = puntos.toArray();
                for (Referencia ref : referencias) {
                    Comparacion c = comparar(ref, puntosCuadro, descriptores, gris.cols(), gris.rows());
                    if (c.inliers() > mejor.inliers()) {
                        segundo = mejor.inliers();
                        mejor = c;
                        mejorId = ref.autorId();
                    } else if (c.inliers() > segundo) {
                        segundo = c.inliers();
                    }
                }
            }
            double puntaje = Math.min(1.0, (double) mejor.inliers() / INLIERS_PUNTAJE_COMPLETO);
            boolean coincide = mejorId != null && puntaje >= UMBRAL
                    && mejor.inliers() >= VENTAJA_SOBRE_SEGUNDO * segundo;
            return new Resultado(mejorId, puntaje, mejor.inliers(), segundo, coincide,
                    coincide ? mejor.esquinas() : null);
        } finally {
            puntos.release();
            descriptores.release();
        }
    }

    private Comparacion comparar(Referencia ref, KeyPoint[] puntosCuadro, Mat descriptoresCuadro, int ancho, int alto) {
        List<MatOfDMatch> vecinos = new ArrayList<>();
        BFMatcher.create(Core.NORM_HAMMING, false).knnMatch(descriptoresCuadro, ref.descriptores(), vecinos, 2);

        KeyPoint[] puntosRef = ref.puntos().toArray();
        List<Point> origen = new ArrayList<>();
        List<Point> destino = new ArrayList<>();
        for (MatOfDMatch par : vecinos) {
            DMatch[] m = par.toArray();
            if (m.length == 2 && m[0].distance < PRUEBA_DE_LOWE * m[1].distance) {
                origen.add(puntosRef[m[0].trainIdx].pt);
                destino.add(puntosCuadro[m[0].queryIdx].pt);
            }
            par.release();
        }
        if (origen.size() < PARES_MINIMOS) {
            return new Comparacion(0, null);
        }

        MatOfPoint2f src = new MatOfPoint2f(origen.toArray(new Point[0]));
        MatOfPoint2f dst = new MatOfPoint2f(destino.toArray(new Point[0]));
        Mat mascara = new Mat();
        Mat homografia = Calib3d.findHomography(src, dst, Calib3d.RANSAC, ERROR_RANSAC, mascara);
        try {
            if (homografia.empty()) {
                return new Comparacion(0, null);
            }
            List<double[]> esquinas = proyectarEsquinas(homografia, ref, ancho, alto);
            if (esquinas == null) {
                return new Comparacion(0, null);
            }
            return new Comparacion(Core.countNonZero(mascara), esquinas);
        } finally {
            src.release();
            dst.release();
            mascara.release();
            homografia.release();
        }
    }

    // Lleva las esquinas de la foto a la imagen de la cámara. Si el contorno sale retorcido
    // o diminuto, la homografía es casualidad y no se cuenta.
    private List<double[]> proyectarEsquinas(Mat homografia, Referencia ref, int ancho, int alto) {
        MatOfPoint2f esquinasRef = new MatOfPoint2f(
                new Point(0, 0), new Point(ref.ancho(), 0), new Point(ref.ancho(), ref.alto()), new Point(0, ref.alto()));
        MatOfPoint2f proyectadas = new MatOfPoint2f();
        Core.perspectiveTransform(esquinasRef, proyectadas, homografia);
        Point[] p = proyectadas.toArray();
        esquinasRef.release();
        proyectadas.release();

        MatOfPoint contorno = new MatOfPoint();
        contorno.fromArray(redondear(p));
        MatOfPoint2f poligono = new MatOfPoint2f(p);
        boolean convexo = Imgproc.isContourConvex(contorno);
        double area = Imgproc.contourArea(poligono);
        contorno.release();
        poligono.release();
        if (!convexo || area < AREA_MINIMA * ancho * alto) {
            return null;
        }
        List<double[]> esquinas = new ArrayList<>();
        for (Point punto : p) {
            esquinas.add(new double[]{redondear(punto.x / ancho), redondear(punto.y / alto)});
        }
        return esquinas;
    }

    private static Point[] redondear(Point[] puntos) {
        Point[] r = new Point[puntos.length];
        for (int i = 0; i < puntos.length; i++) {
            r[i] = new Point(Math.round(puntos[i].x), Math.round(puntos[i].y));
        }
        return r;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 1000) / 1000.0;
    }

    // Decodifica a escala de grises y achica las imágenes muy grandes (ORB no necesita más)
    private static Mat decodificar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        MatOfByte datos = new MatOfByte(bytes);
        Mat gris = Imgcodecs.imdecode(datos, Imgcodecs.IMREAD_GRAYSCALE);
        datos.release();
        if (gris.empty()) {
            gris.release();
            return null;
        }
        int lado = Math.max(gris.cols(), gris.rows());
        if (lado > LADO_MAXIMO) {
            double escala = (double) LADO_MAXIMO / lado;
            Mat chica = new Mat(0, 0, CvType.CV_8UC1);
            Imgproc.resize(gris, chica, new org.opencv.core.Size(), escala, escala, Imgproc.INTER_AREA);
            gris.release();
            return chica;
        }
        return gris;
    }

    private static byte[] aBytes(BufferedImage imagen) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

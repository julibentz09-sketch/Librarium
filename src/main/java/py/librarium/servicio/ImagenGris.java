package py.librarium.servicio;

import java.awt.image.BufferedImage;

/**
 * Imagen convertida a escala de grises (0 a 255) con dos canales de color aparte:
 * u (rojo - verde) y v (azul - amarillo).
 */
class ImagenGris {

    final int ancho;
    final int alto;
    final float[] gris;
    final float[] u;
    final float[] v;

    private ImagenGris(int ancho, int alto, float[] gris, float[] u, float[] v) {
        this.ancho = ancho;
        this.alto = alto;
        this.gris = gris;
        this.u = u;
        this.v = v;
    }

    static ImagenGris desde(BufferedImage imagen) {
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        int[] pixeles = imagen.getRGB(0, 0, w, h, null, 0, w);
        boolean tieneAlfa = imagen.getColorModel().hasAlpha();

        float[] gris = new float[w * h];
        float[] u = new float[w * h];
        float[] v = new float[w * h];
        for (int i = 0; i < pixeles.length; i++) {
            int p = pixeles[i];
            // las partes transparentes se toman como papel blanco
            double alfa = (tieneAlfa ? (p >>> 24) : 255) / 255.0;
            double r = ((p >> 16) & 0xFF) * alfa + 255 * (1 - alfa);
            double g = ((p >> 8) & 0xFF) * alfa + 255 * (1 - alfa);
            double b = (p & 0xFF) * alfa + 255 * (1 - alfa);
            gris[i] = (float) (0.299 * r + 0.587 * g + 0.114 * b);
            u[i] = (float) (r - g);
            v[i] = (float) (b - (r + g) / 2);
        }
        return new ImagenGris(w, h, gris, u, v);
    }

    /** Recorta el rectángulo (x, y, w, h) de un canal y lo achica a anchoSalida x altoSalida promediando. */
    float[] reducir(float[] canal, double x, double y, double w, double h, int anchoSalida, int altoSalida) {
        float[] salida = new float[anchoSalida * altoSalida];
        for (int fila = 0; fila < altoSalida; fila++) {
            int y0 = limitar((int) Math.floor(y + fila * h / altoSalida), 0, alto - 1);
            int y1 = limitar((int) Math.floor(y + (fila + 1) * h / altoSalida), y0 + 1, alto);
            for (int col = 0; col < anchoSalida; col++) {
                int x0 = limitar((int) Math.floor(x + col * w / anchoSalida), 0, ancho - 1);
                int x1 = limitar((int) Math.floor(x + (col + 1) * w / anchoSalida), x0 + 1, ancho);
                double suma = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        suma += canal[yy * ancho + xx];
                    }
                }
                salida[fila * anchoSalida + col] = (float) (suma / ((y1 - y0) * (x1 - x0)));
            }
        }
        return salida;
    }

    private static int limitar(int valor, int min, int max) {
        return Math.max(min, Math.min(max, valor));
    }
}

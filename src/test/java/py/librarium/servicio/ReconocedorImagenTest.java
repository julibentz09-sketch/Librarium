package py.librarium.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import py.librarium.util.Recursos;

class ReconocedorImagenTest {

    private static final String[] AUTORES = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"};
    private static final int LADO = 400;
    private static final ReconocedorImagen reconocedor = new ReconocedorImagen();

    @BeforeAll
    static void cargarFotos() throws IOException {
        for (String autor : AUTORES) {
            reconocedor.agregarReferencia(autor, Recursos.leerBytes("img/" + autor + ".png"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"})
    void reconoceLaFichaImpresa(String autor) throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto(autor), 0.6, 0, false));

        assertTrue(resultado.coincide());
        assertEquals(autor, resultado.autorId());
        assertEquals(4, resultado.esquinas().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"})
    void reconoceFotosImpresasEnBlancoYNegro(String autor) throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto(autor), 0.6, 0, true));

        assertTrue(resultado.coincide());
        assertEquals(autor, resultado.autorId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"})
    void reconoceLaFichaGirada(String autor) throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto(autor), 0.6, 25, false));

        assertTrue(resultado.coincide());
        assertEquals(autor, resultado.autorId());
    }

    @Test
    void reconoceLaFichaInclinada() throws IOException {
        BufferedImage recta = fichaEnPapel(foto("roa-bastos"), 0.7, 0, false);
        BufferedImage inclinada = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = inclinada.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(90, 90, 90));
        g.fillRect(0, 0, LADO, LADO);
        // achica de a franjas para simular que la ficha se ve desde abajo (más angosta arriba)
        for (int y = 0; y < LADO; y++) {
            double ancho = LADO * (0.7 + 0.3 * y / LADO);
            int x = (int) ((LADO - ancho) / 2);
            g.drawImage(recta, x, y, x + (int) ancho, y + 1, 0, y, LADO, y + 1, null);
        }
        g.dispose();

        ReconocedorImagen.Resultado resultado = reconocedor.analizar(inclinada);

        assertTrue(resultado.coincide());
        assertEquals("roa-bastos", resultado.autorId());
    }

    @Test
    void reconoceLaFotoMasGrandeQueElMarco() throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto("josefina-pla"), 0.95, 0, false));

        assertTrue(resultado.coincide());
        assertEquals("josefina-pla", resultado.autorId());
    }

    @Test
    void noReconoceUnaHojaEnBlanco() {
        BufferedImage hoja = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = hoja.createGraphics();
        g.setColor(new Color(240, 240, 236));
        g.fillRect(0, 0, LADO, LADO);
        g.dispose();

        ReconocedorImagen.Resultado resultado = reconocedor.analizar(hoja);

        assertFalse(resultado.coincide());
        assertNull(resultado.esquinas());
    }

    @Test
    void noReconoceUnaCaraCualquiera() {
        BufferedImage persona = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = persona.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(25, 25, 28));
        g.fillRect(0, 0, LADO, LADO);
        g.setColor(new Color(205, 160, 130));
        g.fillOval(125, 70, 150, 200);
        g.setColor(new Color(40, 70, 160));
        g.fillRoundRect(40, 280, 320, 140, 80, 80);
        g.dispose();

        assertFalse(reconocedor.analizar(persona).coincide());
    }

    @Test
    void devuelveNullSiNoEsUnaImagen() {
        assertNull(reconocedor.analizar(new byte[]{1, 2, 3}));
    }

    @Test
    void devuelveLasEsquinasDentroDeLaImagen() throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto("delfina-acosta"), 0.6, 0, false));

        assertNotNull(resultado.esquinas());
        for (double[] esquina : resultado.esquinas()) {
            assertTrue(esquina[0] > 0.05 && esquina[0] < 0.95, "x fuera de lugar: " + esquina[0]);
            assertTrue(esquina[1] > 0.05 && esquina[1] < 0.95, "y fuera de lugar: " + esquina[1]);
        }
    }

    private static BufferedImage foto(String autor) throws IOException {
        return Recursos.leerImagen("img/" + autor + ".png");
    }

    /** Simula lo que manda el navegador: la foto sobre un papel, ocupando cierta parte y girada. */
    private static BufferedImage fichaEnPapel(BufferedImage foto, double ocupa, double grados, boolean blancoYNegro) {
        if (blancoYNegro) {
            BufferedImage gris = new BufferedImage(foto.getWidth(), foto.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            gris.getGraphics().drawImage(foto, 0, 0, null);
            foto = gris;
        }
        BufferedImage cuadro = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cuadro.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(235, 232, 225));
        g.fillRect(0, 0, LADO, LADO);
        double proporcion = (double) foto.getWidth() / foto.getHeight();
        int w = (int) (proporcion >= 1 ? LADO * ocupa : LADO * ocupa * proporcion);
        int h = (int) (proporcion >= 1 ? LADO * ocupa / proporcion : LADO * ocupa);
        g.setTransform(AffineTransform.getRotateInstance(Math.toRadians(grados), LADO / 2.0, LADO / 2.0));
        g.drawImage(foto, (LADO - w) / 2, (LADO - h) / 2, w, h, null);
        g.dispose();
        return cuadro;
    }
}

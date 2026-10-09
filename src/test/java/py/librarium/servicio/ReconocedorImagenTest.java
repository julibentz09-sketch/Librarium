package py.librarium.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import py.librarium.util.Recursos;

class ReconocedorImagenTest {

    private static final String[] AUTORES = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"};
    private static final ReconocedorImagen reconocedor = new ReconocedorImagen();

    @BeforeAll
    static void cargarFotos() throws IOException {
        for (String autor : AUTORES) {
            reconocedor.agregarReferencia(autor, foto(autor));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"roa-bastos", "delfina-acosta", "josefina-pla", "mauricio-cardozo-ocampo"})
    void reconoceLaFichaImpresa(String autor) throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto(autor), 0.6, false));

        assertTrue(resultado.coincide());
        assertEquals(autor, resultado.autorId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"roa-bastos", "delfina-acosta"})
    void reconoceFotosImpresasEnBlancoYNegro(String autor) throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto(autor), 0.6, true));

        assertTrue(resultado.coincide());
        assertEquals(autor, resultado.autorId());
    }

    @Test
    void reconoceLaFotoMasGrandeQueElMarco() throws IOException {
        ReconocedorImagen.Resultado resultado = reconocedor.analizar(fichaEnPapel(foto("josefina-pla"), 0.92, false));

        assertTrue(resultado.coincide());
        assertEquals("josefina-pla", resultado.autorId());
    }

    @Test
    void noReconoceUnaHojaEnBlanco() {
        BufferedImage hoja = new BufferedImage(192, 192, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = hoja.createGraphics();
        g.setColor(new Color(240, 240, 236));
        g.fillRect(0, 0, 192, 192);
        g.dispose();

        assertFalse(reconocedor.analizar(hoja).coincide());
    }

    @Test
    void noConfundeUnaCaraRealConLaFotoEnBlancoYNegro() {
        BufferedImage persona = new BufferedImage(192, 192, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = persona.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(25, 25, 28));
        g.fillRect(0, 0, 192, 192);
        g.setColor(new Color(205, 160, 130));
        g.fillOval(60, 35, 76, 100);
        g.setColor(new Color(40, 70, 160));
        g.fillRoundRect(20, 135, 152, 70, 40, 40);
        g.dispose();

        assertFalse(reconocedor.analizar(persona).coincide());
    }

    private static BufferedImage foto(String autor) throws IOException {
        return Recursos.leerImagen("img/" + autor + ".png");
    }

    /** Simula lo que manda el navegador: la foto sobre un papel, ocupando cierta parte del recorte. */
    private static BufferedImage fichaEnPapel(BufferedImage foto, double ocupa, boolean blancoYNegro) {
        if (blancoYNegro) {
            BufferedImage gris = new BufferedImage(foto.getWidth(), foto.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            gris.getGraphics().drawImage(foto, 0, 0, null);
            foto = gris;
        }
        int lado = 192;
        BufferedImage cuadro = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cuadro.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(235, 232, 225));
        g.fillRect(0, 0, lado, lado);
        double proporcion = (double) foto.getWidth() / foto.getHeight();
        int w = (int) (proporcion >= 1 ? lado * ocupa : lado * ocupa * proporcion);
        int h = (int) (proporcion >= 1 ? lado * ocupa / proporcion : lado * ocupa);
        g.drawImage(foto, (lado - w) / 2, (lado - h) / 2, w, h, null);
        g.dispose();
        return cuadro;
    }
}

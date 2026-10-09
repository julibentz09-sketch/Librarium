package py.librarium.modelo;

import java.util.List;

public record Autor(
        String id,
        String nombre,
        String fechaNacimiento,
        String lugarNacimiento,
        String fallecimiento,
        String destacado,
        String biografia,
        String foto,
        List<Obra> obras,
        List<Multimedia> multimedia) {
}

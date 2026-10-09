package py.librarium.util;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;

/** Convierte mapas, listas, records, textos y números a JSON. */
public final class Json {

    private Json() {
    }

    public static String convertir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String texto) {
            escribirTexto(sb, texto);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            escribirMapa(sb, mapa);
        } else if (valor instanceof Collection<?> lista) {
            escribirLista(sb, lista);
        } else if (valor.getClass().isRecord()) {
            escribirRecord(sb, valor);
        } else {
            escribirTexto(sb, valor.toString());
        }
    }

    private static void escribirMapa(StringBuilder sb, Map<?, ?> mapa) {
        sb.append('{');
        boolean primero = true;
        for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribirTexto(sb, String.valueOf(entrada.getKey()));
            sb.append(':');
            escribir(sb, entrada.getValue());
        }
        sb.append('}');
    }

    private static void escribirLista(StringBuilder sb, Collection<?> lista) {
        sb.append('[');
        boolean primero = true;
        for (Object elemento : lista) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribir(sb, elemento);
        }
        sb.append(']');
    }

    private static void escribirRecord(StringBuilder sb, Object record) {
        sb.append('{');
        boolean primero = true;
        for (RecordComponent campo : record.getClass().getRecordComponents()) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            escribirTexto(sb, campo.getName());
            sb.append(':');
            try {
                escribir(sb, campo.getAccessor().invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No se pudo leer " + campo.getName(), e);
            }
        }
        sb.append('}');
    }

    private static void escribirTexto(StringBuilder sb, String texto) {
        sb.append('"');
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}

"""
Genera los modelos 3D (.glb) que se ven en realidad aumentada, en tamaño real (metros):
un pedestal de museo con el busto de bronce del autor (o su retrato enmarcado si todavía no
tiene busto), una placa con su nombre, una pila de libros y dos paneles a los costados con su
biografía y sus obras principales.

Uso: python herramientas/generar_modelos.py
Toma los datos de src/main/resources/db (schema.sql y datos.sql) y las fotos de
src/main/resources/web/img, y guarda los modelos en src/main/resources/web/modelos.
Necesita Pillow para escribir los textos: pip install pillow

Los bustos están en herramientas/bustos/<autor>.glb. Se hicieron con la IA Hunyuan3D-2.1
(https://huggingface.co/spaces/tencent/Hunyuan3D-2.1, "shape generation" a partir de la foto)
y se simplificaron a unos 50 mil triángulos con:
  npx @gltf-transform/cli weld crudo.glb soldado.glb
  npx @gltf-transform/cli simplify soldado.glb <autor>.glb --ratio 0.12 --error 0.0008
"""
import io
import json
import math
import sqlite3
import struct
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
CARPETA_DB = RAIZ / "src/main/resources/db"
CARPETA_WEB = RAIZ / "src/main/resources/web"
CARPETA_MODELOS = CARPETA_WEB / "modelos"
CARPETA_BUSTOS = Path(__file__).resolve().parent / "bustos"

ALTO_FOTO = 0.62
BORDE_MARCO = 0.05

AZUL = "#1E293B"
ESMERALDA = "#10B981"
ESMERALDA_OSCURO = "#047857"
ESMERALDA_SUAVE = "#D1FAE5"
TEXTO = "#334155"
APAGADO = "#64748B"
PAPEL = "#FBF8F1"


# ---------- datos ----------

def leer_autores():
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript((CARPETA_DB / "schema.sql").read_text(encoding="utf-8"))
    db.executescript((CARPETA_DB / "datos.sql").read_text(encoding="utf-8"))
    autores = [dict(fila) for fila in db.execute("SELECT * FROM autor ORDER BY orden")]
    for autor in autores:
        autor["obras"] = [dict(fila) for fila in db.execute(
            "SELECT * FROM obra WHERE autor_id = ? ORDER BY orden", (autor["id"],))]
    return autores


# ---------- texturas con texto ----------

FUENTES = {
    "serif": ["georgia.ttf", "DejaVuSerif.ttf"],
    "serif_negrita": ["georgiab.ttf", "DejaVuSerif-Bold.ttf"],
    "serif_cursiva": ["georgiaz.ttf", "DejaVuSerif-BoldItalic.ttf"],
    "sans": ["segoeui.ttf", "DejaVuSans.ttf"],
    "sans_negrita": ["segoeuib.ttf", "DejaVuSans-Bold.ttf"],
}
CARPETAS_FUENTES = [Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu"),
                    Path("/Library/Fonts"), Path.home() / "Library/Fonts"]


def fuente(tipo, tamano):
    for nombre in FUENTES[tipo]:
        for carpeta in CARPETAS_FUENTES:
            if (carpeta / nombre).exists():
                return ImageFont.truetype(str(carpeta / nombre), round(tamano))
    return ImageFont.load_default(round(tamano))


def envolver(dibujo, texto, letra, ancho):
    lineas, actual = [], ""
    for palabra in texto.split():
        prueba = f"{actual} {palabra}".strip()
        if actual and dibujo.textlength(prueba, font=letra) > ancho:
            lineas.append(actual)
            actual = palabra
        else:
            actual = prueba
    return lineas + [actual] if actual else lineas


def a_jpeg(imagen):
    salida = io.BytesIO()
    imagen.convert("RGB").save(salida, "JPEG", quality=88)
    return salida.getvalue()


ANCHO_PANEL, ALTO_PANEL = 1024, 1400
MARGEN = 70
ALTO_CABECERA = 190


def panel(seccion, nombre, bloques):
    """Dibuja un panel de museo con la letra lo más grande posible para que se lea de lejos."""
    escala = 1.4
    while True:
        imagen = Image.new("RGB", (ANCHO_PANEL, ALTO_PANEL), PAPEL)
        dibujo = ImageDraw.Draw(imagen)
        dibujo.rectangle((0, 0, ANCHO_PANEL, ALTO_CABECERA), fill=AZUL)
        dibujo.rectangle((0, ALTO_CABECERA, ANCHO_PANEL, ALTO_CABECERA + 10), fill=ESMERALDA)
        dibujo.text((MARGEN, 42), seccion.upper(), font=fuente("sans_negrita", 28), fill=ESMERALDA)
        letra_nombre = fuente("serif_negrita", 58)
        while dibujo.textlength(nombre, font=letra_nombre) > ANCHO_PANEL - 2 * MARGEN:
            letra_nombre = fuente("serif_negrita", letra_nombre.size - 2)
        dibujo.text((MARGEN, 88), nombre, font=letra_nombre, fill="white")

        y = ALTO_CABECERA + 50
        for bloque in bloques:
            y = bloque(dibujo, y, escala)
        if y <= ALTO_PANEL - 90 or escala < 0.6:
            break
        escala -= 0.04

    pie = "Librarium · Literatura Paraguaya Interactiva"
    letra_pie = fuente("sans", 22)
    dibujo.text(((ANCHO_PANEL - dibujo.textlength(pie, font=letra_pie)) / 2, ALTO_PANEL - 52),
                pie, font=letra_pie, fill=APAGADO)
    return imagen


def parrafo(texto, tipo, tamano, color, antes=0, interlineado=1.4):
    def dibujar(dibujo, y, escala):
        letra = fuente(tipo, tamano * escala)
        y += antes * escala
        for linea in envolver(dibujo, texto, letra, ANCHO_PANEL - 2 * MARGEN):
            dibujo.text((MARGEN, y), linea, font=letra, fill=color)
            y += letra.size * interlineado
        return y
    return dibujar


def insignia(texto):
    def dibujar(dibujo, y, escala):
        letra = fuente("sans_negrita", 28 * escala)
        ancho = dibujo.textlength(texto, font=letra)
        alto = letra.size * 1.7
        dibujo.rounded_rectangle((MARGEN, y, MARGEN + ancho + 44, y + alto), radius=alto / 2, fill=ESMERALDA_SUAVE)
        dibujo.text((MARGEN + 22, y + alto / 2), texto, font=letra, fill=ESMERALDA_OSCURO, anchor="lm")
        return y + alto + 10 * escala
    return dibujar


def separador(antes=26, despues=26):
    def dibujar(dibujo, y, escala):
        y += antes * escala
        dibujo.line((MARGEN, y, ANCHO_PANEL - MARGEN, y), fill="#E2E8F0", width=3)
        return y + despues * escala
    return dibujar


def panel_biografia(autor):
    bloques = []
    if autor["destacado"]:
        bloques.append(insignia(autor["destacado"]))
    bloques.append(parrafo("Nacimiento", "sans_negrita", 30, AZUL, antes=14))
    bloques.append(parrafo(f"{autor['fecha_nacimiento']}, {autor['lugar_nacimiento']}", "sans", 30, TEXTO))
    if autor["fallecimiento"]:
        bloques.append(parrafo("Fallecimiento", "sans_negrita", 30, AZUL, antes=10))
        bloques.append(parrafo(autor["fallecimiento"], "sans", 30, TEXTO))
    bloques.append(separador())
    bloques.append(parrafo(autor["biografia"], "sans", 35, TEXTO, interlineado=1.45))
    return panel("Biografía", autor["nombre"], bloques)


def panel_obras(autor):
    bloques = []
    for numero, obra in enumerate(autor["obras"]):
        if numero:
            bloques.append(separador(18, 24))
        bloques.append(parrafo(obra["titulo"], "serif_cursiva", 42, AZUL))
        datos = " · ".join(str(d) for d in (obra["genero"], obra["anio"]) if d)
        if datos:
            bloques.append(parrafo(datos, "sans_negrita", 27, ESMERALDA_OSCURO, antes=2))
        if obra["descripcion"]:
            bloques.append(parrafo(obra["descripcion"], "sans", 32, TEXTO, antes=8))
    return panel("Obras principales", autor["nombre"], bloques)


def textura_placa(nombre):
    ancho, alto = 768, 200
    imagen = Image.new("RGB", (ancho, alto))
    dibujo = ImageDraw.Draw(imagen)
    claro, oscuro = (0xE8, 0xCB, 0x6A), (0xB0, 0x87, 0x1C)
    for y in range(alto):
        t = y / (alto - 1)
        dibujo.line((0, y, ancho, y), fill=tuple(round(claro[i] + (oscuro[i] - claro[i]) * t) for i in range(3)))
    dibujo.rectangle((10, 10, ancho - 11, alto - 11), outline=(0x7A, 0x5A, 0x0E), width=4)
    letra = fuente("serif_negrita", 76)
    while dibujo.textlength(nombre, font=letra) > ancho - 80:
        letra = fuente("serif_negrita", letra.size - 2)
    dibujo.text((ancho / 2, alto / 2), nombre, font=letra, fill=(0x3B, 0x2A, 0x05), anchor="mm")
    return imagen


# ---------- glTF ----------

def color(hexa, rugosidad=0.8, metalico=0.0):
    """Material PBR a partir de un color sRGB (#RRGGBB)."""
    def lineal(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hexa[i:i + 2], 16) for i in (1, 3, 5))
    return {"pbrMetallicRoughness": {"baseColorFactor": [lineal(r), lineal(g), lineal(b), 1],
                                     "metallicFactor": metalico, "roughnessFactor": rugosidad}}


MATERIALES = {
    "pedestal": color(AZUL, 0.7),
    "zocalo": color("#0F172A", 0.7),
    "esmeralda": color(ESMERALDA, 0.5),
    "placa": color("#C9A227", 0.35, 0.8),
    "marco": color("#6B4423", 0.6),
    "hojas": color("#F5F0E1", 0.9),
    "tapa_roja": color("#9F1239", 0.6),
    "tapa_verde": color(ESMERALDA_OSCURO, 0.6),
    "tapa_azul": color("#1D4ED8", 0.6),
    "bronce": color("#A0703F", 0.38, 0.9),
}


def leer_busto(ruta):
    """Lee las posiciones y los triángulos del .glb de un busto (una sola malla, sin texturas)."""
    datos = ruta.read_bytes()
    largo_json = struct.unpack_from("<I", datos, 12)[0]
    gltf = json.loads(datos[20:20 + largo_json])
    binario = datos[28 + largo_json:]
    primitiva = gltf["meshes"][0]["primitives"][0]

    def leer(indice):
        accesor = gltf["accessors"][indice]
        vista = gltf["bufferViews"][accesor["bufferView"]]
        inicio = vista.get("byteOffset", 0) + accesor.get("byteOffset", 0)
        formato = {5126: "f", 5123: "H", 5125: "I"}[accesor["componentType"]]
        partes = 3 if accesor["type"] == "VEC3" else 1
        valores = struct.unpack_from(f"<{accesor['count'] * partes}{formato}", binario, inicio)
        return [valores[i:i + 3] for i in range(0, len(valores), 3)] if partes == 3 else list(valores)

    return leer(primitiva["attributes"]["POSITION"]), leer(primitiva["indices"])


def normales_suaves(posiciones, indices):
    normales = [[0.0, 0.0, 0.0] for _ in posiciones]
    for i in range(0, len(indices), 3):
        a, b, c = indices[i:i + 3]
        pa, pb, pc = posiciones[a], posiciones[b], posiciones[c]
        u = [pb[k] - pa[k] for k in range(3)]
        v = [pc[k] - pa[k] for k in range(3)]
        n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        for vertice in (a, b, c):
            for k in range(3):
                normales[vertice][k] += n[k]
    resultado = []
    for n in normales:
        largo = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2) or 1.0
        resultado.append((n[0] / largo, n[1] / largo, n[2] / largo))
    return resultado


def tamano_png(datos):
    ancho, alto = struct.unpack(">II", datos[16:24])
    return ancho, alto


class Modelo:
    def __init__(self):
        self.binario = bytearray()
        self.gltf = {"asset": {"version": "2.0", "generator": "Librarium"},
                     "scene": 0, "scenes": [{"nodes": [0]}],
                     "nodes": [{"name": "escultura", "children": []}],
                     "meshes": [], "materials": [], "accessors": [], "bufferViews": [],
                     "buffers": [], "textures": [], "images": [],
                     "samplers": [{"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071}]}
        self.indice_material = {}
        self.indice_malla = {}
        self.cubo = self._geometria_cubo()
        self.plano = self._geometria([(-0.5, -0.5, 0), (0.5, -0.5, 0), (0.5, 0.5, 0), (-0.5, 0.5, 0)],
                                     [(0, 0, 1)] * 4, [0, 1, 2, 0, 2, 3], [(0, 1), (1, 1), (1, 0), (0, 0)])

    def _vista(self, datos, destino=None):
        while len(self.binario) % 4:
            self.binario.append(0)
        vista = {"buffer": 0, "byteOffset": len(self.binario), "byteLength": len(datos)}
        if destino:
            vista["target"] = destino
        self.binario.extend(datos)
        self.gltf["bufferViews"].append(vista)
        return len(self.gltf["bufferViews"]) - 1

    def _accesor(self, valores, tipo, componentes, destino, con_limites=False):
        if tipo == "SCALAR":
            grande = max(valores) > 65535
            datos = struct.pack(f"<{len(valores)}{'I' if grande else 'H'}", *valores)
            accesor = {"componentType": 5125 if grande else 5123, "count": len(valores), "type": "SCALAR"}
        else:
            planos = [v for vector in valores for v in vector]
            datos = struct.pack(f"<{len(planos)}f", *planos)
            accesor = {"componentType": 5126, "count": len(valores), "type": tipo}
            if con_limites:
                accesor["min"] = [min(v[i] for v in valores) for i in range(componentes)]
                accesor["max"] = [max(v[i] for v in valores) for i in range(componentes)]
        accesor["bufferView"] = self._vista(datos, destino)
        self.gltf["accessors"].append(accesor)
        return len(self.gltf["accessors"]) - 1

    def _geometria(self, posiciones, normales, indices, uvs=None):
        atributos = {"POSITION": self._accesor(posiciones, "VEC3", 3, 34962, True),
                     "NORMAL": self._accesor(normales, "VEC3", 3, 34962)}
        if uvs:
            atributos["TEXCOORD_0"] = self._accesor(uvs, "VEC2", 2, 34962)
        return {"attributes": atributos, "indices": self._accesor(indices, "SCALAR", 1, 34963)}

    def _geometria_cubo(self):
        caras = [((1, 0, 0), (0, 0, -1), (0, 1, 0)), ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
                 ((0, 1, 0), (1, 0, 0), (0, 0, -1)), ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
                 ((0, 0, 1), (1, 0, 0), (0, 1, 0)), ((0, 0, -1), (-1, 0, 0), (0, 1, 0))]
        posiciones, normales, indices = [], [], []
        for normal, u, v in caras:
            base = len(posiciones)
            for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                posiciones.append(tuple(0.5 * normal[i] + 0.5 * su * u[i] + 0.5 * sv * v[i] for i in range(3)))
                normales.append(normal)
            indices += [base, base + 1, base + 2, base, base + 2, base + 3]
        return self._geometria(posiciones, normales, indices)

    def _material(self, nombre, material):
        if nombre not in self.indice_material:
            self.gltf["materials"].append({"name": nombre, **material})
            self.indice_material[nombre] = len(self.gltf["materials"]) - 1
        return self.indice_material[nombre]

    def _malla(self, nombre, geometria, material):
        if nombre not in self.indice_malla:
            self.gltf["meshes"].append({"name": nombre, "primitives": [{**geometria, "material": material}]})
            self.indice_malla[nombre] = len(self.gltf["meshes"]) - 1
        return self.indice_malla[nombre]

    def _nodo(self, nodo, padre=0):
        self.gltf["nodes"].append(nodo)
        indice = len(self.gltf["nodes"]) - 1
        self.gltf["nodes"][padre].setdefault("children", []).append(indice)
        return indice

    def caja(self, material, centro, tamano, padre=0):
        malla = self._malla(material, self.cubo, self._material(material, MATERIALES[material]))
        self._nodo({"mesh": malla, "translation": list(centro), "scale": list(tamano)}, padre)

    def grupo(self, centro, giro_y=0.0):
        mitad = math.radians(giro_y) / 2
        return self._nodo({"translation": list(centro), "rotation": [0, math.sin(mitad), 0, math.cos(mitad)]})

    def imagen(self, nombre, datos, tipo, centro, ancho, alto, padre=0, rugosidad=0.9, metalico=0.0, brillo=0.0):
        """Un plano con una imagen; con brillo > 0 se ve más claro aunque haya poca luz."""
        self.gltf["images"].append({"bufferView": self._vista(datos), "mimeType": tipo})
        self.gltf["textures"].append({"source": len(self.gltf["images"]) - 1, "sampler": 0})
        textura = len(self.gltf["textures"]) - 1
        material = {"pbrMetallicRoughness": {"baseColorTexture": {"index": textura},
                                             "metallicFactor": metalico, "roughnessFactor": rugosidad}}
        if brillo:
            material["emissiveTexture"] = {"index": textura}
            material["emissiveFactor"] = [brillo] * 3
        malla = self._malla(nombre, self.plano, self._material(nombre, material))
        self._nodo({"mesh": malla, "translation": list(centro), "scale": [ancho, alto, 1]}, padre)

    def figura(self, nombre, material, posiciones, indices):
        geometria = self._geometria(posiciones, normales_suaves(posiciones, indices), indices)
        malla = self._malla(nombre, geometria, self._material(material, MATERIALES[material]))
        self._nodo({"mesh": malla})

    def guardar(self, ruta):
        while len(self.binario) % 4:
            self.binario.append(0)
        self.gltf["buffers"].append({"byteLength": len(self.binario)})
        texto = json.dumps({k: v for k, v in self.gltf.items() if v}, separators=(",", ":")).encode()
        texto += b" " * (-len(texto) % 4)
        total = 12 + 8 + len(texto) + 8 + len(self.binario)
        with open(ruta, "wb") as archivo:
            archivo.write(struct.pack("<III", 0x46546C67, 2, total))
            archivo.write(struct.pack("<II", len(texto), 0x4E4F534A) + texto)
            archivo.write(struct.pack("<II", len(self.binario), 0x004E4942) + self.binario)


# ---------- escultura ----------

def libro(modelo, centro, giro, tapa):
    grupo = modelo.grupo(centro, giro)
    ancho, grosor, largo = 0.22, 0.045, 0.30
    modelo.caja(tapa, (0, grosor / 2 - 0.003, 0), (ancho, 0.006, largo), grupo)
    modelo.caja(tapa, (0, -grosor / 2 + 0.003, 0), (ancho, 0.006, largo), grupo)
    modelo.caja(tapa, (-ancho / 2 + 0.003, 0, 0), (0.006, grosor, largo), grupo)
    modelo.caja("hojas", (0.004, 0, 0), (ancho - 0.016, grosor - 0.012, largo - 0.012), grupo)


def atril(modelo, nombre, imagen, x, giro):
    """Panel de museo parado sobre dos patas, girado hacia el frente de la escultura."""
    grupo = modelo.grupo((x, 0, 0.12), giro)
    ancho = 0.72
    alto = ancho * ALTO_PANEL / ANCHO_PANEL
    centro_y = 0.55 + alto / 2 + 0.03
    for lado in (-1, 1):
        modelo.caja("pedestal", (lado * 0.26, 0.29, -0.01), (0.04, 0.58, 0.04), grupo)
        modelo.caja("zocalo", (lado * 0.26, 0.015, -0.01), (0.07, 0.03, 0.36), grupo)
    modelo.caja("pedestal", (0, centro_y, -0.01), (ancho + 0.06, alto + 0.06, 0.03), grupo)
    modelo.caja("esmeralda", (0, centro_y + alto / 2 + 0.04, -0.01), (ancho + 0.08, 0.025, 0.045), grupo)
    modelo.imagen(nombre, a_jpeg(imagen), "image/jpeg", (0, centro_y, 0.0065), ancho, alto, grupo, brillo=0.3)


def busto(modelo, ruta, x):
    """Coloca el busto parado sobre el pedestal, escalado para que entre en el espacio libre."""
    posiciones, indices = leer_busto(ruta)
    minimo = [min(p[k] for p in posiciones) for k in range(3)]
    maximo = [max(p[k] for p in posiciones) for k in range(3)]
    ancho, alto, fondo = (maximo[k] - minimo[k] for k in range(3))
    escala = min(0.78 / alto, 0.72 / ancho, 0.45 / fondo)
    centro_x, centro_z = (minimo[0] + maximo[0]) / 2, (minimo[2] + maximo[2]) / 2
    colocadas = [((p[0] - centro_x) * escala + x, (p[1] - minimo[1]) * escala + 0.9, (p[2] - centro_z) * escala)
                 for p in posiciones]
    modelo.figura("busto", "bronce", colocadas, indices)


def retrato(modelo, png, x):
    ancho_px, alto_px = tamano_png(png)
    ancho_foto = ALTO_FOTO * ancho_px / alto_px
    ancho_marco, alto_marco = ancho_foto + 2 * BORDE_MARCO, ALTO_FOTO + 2 * BORDE_MARCO
    centro_y = 0.9 + alto_marco / 2
    modelo.caja("marco", (x, centro_y, 0), (ancho_marco, alto_marco, 0.05))
    modelo.imagen("retrato", png, "image/png", (x, centro_y, 0.026), ancho_foto, ALTO_FOTO)


def crear_escultura(autor):
    modelo = Modelo()
    nombre_archivo = Path(autor["foto"]).stem

    # pedestal con la placa del nombre
    modelo.caja("zocalo", (0, 0.03, 0), (1.16, 0.06, 0.52))
    modelo.caja("pedestal", (0, 0.455, 0), (1.1, 0.79, 0.46))
    modelo.caja("esmeralda", (0, 0.875, 0), (1.16, 0.05, 0.52))
    modelo.caja("placa", (-0.15, 0.62, 0.236), (0.56, 0.155, 0.012))
    modelo.imagen("placa_nombre", a_jpeg(textura_placa(autor["nombre"])), "image/jpeg",
                  (-0.15, 0.62, 0.2425), 0.54, 0.54 * 200 / 768, rugosidad=0.4, metalico=0.5, brillo=0.15)

    # el autor: busto 3D si existe, si no su retrato enmarcado
    ruta_busto = CARPETA_BUSTOS / f"{nombre_archivo}.glb"
    if ruta_busto.exists():
        busto(modelo, ruta_busto, -0.15)
    else:
        retrato(modelo, (CARPETA_WEB / autor["foto"]).read_bytes(), -0.15)

    # pila de libros
    libro(modelo, (0.40, 0.9225, 0.02), 8, "tapa_roja")
    libro(modelo, (0.40, 0.9675, 0.02), -6, "tapa_verde")
    libro(modelo, (0.39, 1.0125, 0.03), 14, "tapa_azul")

    # paneles con la información
    atril(modelo, "biografia", panel_biografia(autor), -1.08, 22)
    atril(modelo, "obras", panel_obras(autor), 1.08, -22)
    return modelo


def main():
    CARPETA_MODELOS.mkdir(parents=True, exist_ok=True)
    for autor in leer_autores():
        destino = CARPETA_MODELOS / f"{Path(autor['foto']).stem}.glb"
        crear_escultura(autor).guardar(destino)
        tiene_busto = (CARPETA_BUSTOS / destino.name).exists()
        print(f"{destino.relative_to(RAIZ)} ({destino.stat().st_size // 1024} KB, {'busto' if tiene_busto else 'retrato'})")


if __name__ == "__main__":
    main()

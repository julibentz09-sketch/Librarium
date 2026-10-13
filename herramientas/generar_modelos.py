"""
Genera los modelos 3D (.glb) que se ven en realidad aumentada: un pedestal de museo
con el retrato enmarcado del autor y una pila de libros, en tamaño real (metros).

Uso: python herramientas/generar_modelos.py
Lee las fotos de src/main/resources/web/img y guarda los modelos en src/main/resources/web/modelos.
No necesita librerías externas.
"""
import json
import math
import struct
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CARPETA_FOTOS = RAIZ / "src/main/resources/web/img"
CARPETA_MODELOS = RAIZ / "src/main/resources/web/modelos"

ALTO_FOTO = 0.62
BORDE_MARCO = 0.05


def color(hexa, rugosidad=0.8, metalico=0.0):
    """Material PBR a partir de un color sRGB (#RRGGBB)."""
    def lineal(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hexa[i:i + 2], 16) for i in (1, 3, 5))
    return {"pbrMetallicRoughness": {"baseColorFactor": [lineal(r), lineal(g), lineal(b), 1],
                                     "metallicFactor": metalico, "roughnessFactor": rugosidad}}


MATERIALES = {
    "pedestal": color("#1E293B", 0.7),
    "zocalo": color("#0F172A", 0.7),
    "esmeralda": color("#10B981", 0.5),
    "placa": color("#C9A227", 0.35, 0.8),
    "marco": color("#6B4423", 0.6),
    "hojas": color("#F5F0E1", 0.9),
    "tapa_roja": color("#9F1239", 0.6),
    "tapa_verde": color("#047857", 0.6),
    "tapa_azul": color("#1D4ED8", 0.6),
}


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
                     "buffers": [], "textures": [], "images": [], "samplers": []}
        self.indice_material = {}
        self.indice_malla = {}
        self.cubo = self._geometria_cubo()

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
            datos = struct.pack(f"<{len(valores)}H", *valores)
            accesor = {"componentType": 5123, "count": len(valores), "type": "SCALAR"}
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

    def _malla_cubo(self, material):
        if material not in self.indice_malla:
            primitiva = {**self.cubo, "material": self._material(material, MATERIALES[material])}
            self.gltf["meshes"].append({"name": material, "primitives": [primitiva]})
            self.indice_malla[material] = len(self.gltf["meshes"]) - 1
        return self.indice_malla[material]

    def _nodo(self, nodo, padre=0):
        self.gltf["nodes"].append(nodo)
        indice = len(self.gltf["nodes"]) - 1
        self.gltf["nodes"][padre].setdefault("children", []).append(indice)
        return indice

    def caja(self, material, centro, tamano, padre=0):
        self._nodo({"mesh": self._malla_cubo(material), "translation": list(centro), "scale": list(tamano)}, padre)

    def grupo(self, centro, giro_y=0.0):
        mitad = math.radians(giro_y) / 2
        return self._nodo({"translation": list(centro), "rotation": [0, math.sin(mitad), 0, math.cos(mitad)]})

    def foto(self, png, centro, ancho, alto):
        imagen = self._vista(png)
        self.gltf["images"].append({"bufferView": imagen, "mimeType": "image/png"})
        self.gltf["samplers"].append({"magFilter": 9729, "minFilter": 9987, "wrapS": 33071, "wrapT": 33071})
        self.gltf["textures"].append({"source": 0, "sampler": 0})
        material = self._material("retrato", {"pbrMetallicRoughness": {
            "baseColorTexture": {"index": 0}, "metallicFactor": 0, "roughnessFactor": 0.9}})
        a, h = ancho / 2, alto / 2
        geometria = self._geometria([(-a, -h, 0), (a, -h, 0), (a, h, 0), (-a, h, 0)], [(0, 0, 1)] * 4,
                                    [0, 1, 2, 0, 2, 3], [(0, 1), (1, 1), (1, 0), (0, 0)])
        self.gltf["meshes"].append({"name": "retrato", "primitives": [{**geometria, "material": material}]})
        self._nodo({"mesh": len(self.gltf["meshes"]) - 1, "translation": list(centro)})

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


def libro(modelo, centro, giro, tapa):
    grupo = modelo.grupo(centro, giro)
    ancho, grosor, largo = 0.22, 0.045, 0.30
    modelo.caja(tapa, (0, grosor / 2 - 0.003, 0), (ancho, 0.006, largo), grupo)
    modelo.caja(tapa, (0, -grosor / 2 + 0.003, 0), (ancho, 0.006, largo), grupo)
    modelo.caja(tapa, (-ancho / 2 + 0.003, 0, 0), (0.006, grosor, largo), grupo)
    modelo.caja("hojas", (0.004, 0, 0), (ancho - 0.016, grosor - 0.012, largo - 0.012), grupo)


def crear_escultura(png):
    modelo = Modelo()
    ancho_px, alto_px = tamano_png(png)
    ancho_foto = ALTO_FOTO * ancho_px / alto_px

    # pedestal
    modelo.caja("zocalo", (0, 0.03, 0), (1.16, 0.06, 0.52))
    modelo.caja("pedestal", (0, 0.455, 0), (1.1, 0.79, 0.46))
    modelo.caja("esmeralda", (0, 0.875, 0), (1.16, 0.05, 0.52))
    modelo.caja("placa", (-0.15, 0.62, 0.235), (0.5, 0.13, 0.012))

    # retrato enmarcado
    ancho_marco, alto_marco = ancho_foto + 2 * BORDE_MARCO, ALTO_FOTO + 2 * BORDE_MARCO
    centro_y = 0.9 + alto_marco / 2
    modelo.caja("marco", (-0.15, centro_y, 0), (ancho_marco, alto_marco, 0.05))
    modelo.foto(png, (-0.15, centro_y, 0.026), ancho_foto, ALTO_FOTO)

    # pila de libros
    libro(modelo, (0.40, 0.9225, 0.02), 8, "tapa_roja")
    libro(modelo, (0.40, 0.9675, 0.02), -6, "tapa_verde")
    libro(modelo, (0.39, 1.0125, 0.03), 14, "tapa_azul")
    return modelo


def main():
    CARPETA_MODELOS.mkdir(parents=True, exist_ok=True)
    for foto in sorted(CARPETA_FOTOS.glob("*.png")):
        destino = CARPETA_MODELOS / f"{foto.stem}.glb"
        crear_escultura(foto.read_bytes()).guardar(destino)
        print(f"{destino.relative_to(RAIZ)} ({destino.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()

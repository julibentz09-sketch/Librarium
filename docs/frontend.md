# Frontend (página web)

Todo está en [`src/main/resources/web/`](../src/main/resources/web/). Es HTML, CSS y JavaScript con
**módulos ES nativos**: no hay npm, ni bundler, ni paso de build propio del frontend. Lo único es que el
servidor Java sirve la página desde el classpath, así que después de editar hay que recompilar
(`mvnw -DskipTests package`) y reiniciar; ver [Guía de desarrollo → Ciclo de trabajo](guia-desarrollo.md#ciclo-de-trabajo).

```
web/
├── index.html          toda la app: cada pantalla es una <section class="vista">
├── marcadores.html     página aparte para imprimir las fichas del escáner
├── css/styles.css      todos los estilos
├── js/
│   ├── app.js          punto de entrada: carga datos, rutas, modal de salir
│   ├── api.js          acceso a datos (servidor Java o JSON estáticos)
│   ├── plantillas.js   HTML de la tarjeta y de la ficha del autor + escapar()
│   ├── ra3d.js         vista de realidad aumentada 3D (<model-viewer>)
│   ├── escaner.js      vista de escanear ficha: cámara, recorte y reconocimiento
│   ├── camara.js       manejo de getUserMedia (trasera/frontal, cambiar cámara)
│   ├── reconocedor.js  reconocimiento con OpenCV.js en el navegador (solo en GitHub Pages)
│   └── voz.js          leer la biografía en voz alta (speechSynthesis)
├── img/                fotos de los autores (también son las referencias del escáner)
└── modelos/            esculturas 3D .glb (generadas, ver realidad-aumentada.md)
```

Librerías externas (por CDN, necesitan internet):

- [`@google/model-viewer@3.5.0`](https://modelviewer.dev): visor 3D y RA.
- [`qrcodejs@1.0.0`](https://github.com/davidshimjs/qrcodejs): QR del menú para abrir la app en el celular.
- [`@techstark/opencv-js@4.10.0-release.1`](https://www.npmjs.com/package/@techstark/opencv-js): OpenCV.js
  (~10 MB). No se carga con la página: `reconocedor.js` lo descarga recién al escanear y solo en GitHub Pages.

## Navegación (rutas con `#`)

`app.js` escucha `hashchange` y muestra una sola `<section>` a la vez (las demás llevan `hidden`).

| Ruta | Vista (`id`) | Qué muestra |
|---|---|---|
| `#/` | `vista-bienvenida` | Pantalla de bienvenida con el botón **Comenzar** |
| `#/menu` | `vista-menu` | Dos botones grandes: Galería Digital y Realidad Aumentada; QR para el celular; Salir |
| `#/galeria` | `vista-galeria` | Tarjetas de los autores |
| `#/autor/{id}` | `vista-autor` | Ficha completa: foto, datos, biografía (con audio), obras y botón de RA |
| `#/ra` y `#/ra/{id}` | `vista-ra` | Escultura 3D del autor con el botón **Ver en tu espacio** |
| `#/escanear` | `vista-escanear` | Cámara para reconocer fichas impresas |
| `#/salir` | `vista-salir` | Despedida |

Cualquier ruta desconocida muestra la bienvenida. El parámetro `{id}` es el id del autor
(`roa-bastos`, etc.).

`mostrarVista()` también:

- cambia el título de la pestaña;
- pone en `<body>` la clase `modo-escaner` (pantalla completa para la cámara) o `modo-bienvenida`
  (fondo azul, sin encabezado);
- detiene el audio y apaga la cámara del escáner al salir de esas vistas.

### Agregar una pantalla

1. En `index.html`, agregá `<section id="vista-nueva" class="vista" hidden>…</section>` dentro de `<main>`.
2. En `app.js`, sumala al objeto `vistas` y agregá la rama en `navegar()`.
3. Enlazala con `<a href="#/nueva">`.

## Módulos JavaScript

### `app.js`

Punto de entrada. Al iniciar: carga los autores (`api.obtenerAutores()`), dibuja la galería, pide
`api.obtenerInfo()` para el QR, conecta el modal de **Salir** y llama a `navegar()`. También tiene un único
listener global para los botones `[data-escuchar]` (audio de la biografía).

### `api.js`

Única puerta de entrada a los datos. Decide si hay servidor o no (ver
[Arquitectura → modos](arquitectura.md#los-dos-modos-de-ejecución)).

| Función | Con servidor | Sin servidor (Pages) |
|---|---|---|
| `obtenerAutores()` | `GET api/autores` | `datos/autores.json` |
| `obtenerInfo()` | `GET api/info` | Objeto armado en el navegador |
| `reconocer(lienzo)` | `POST api/reconocer` con el JPEG del lienzo | `reconocedor.js` con `datos/marcadores.json` |

### `plantillas.js`

Genera el HTML de `tarjetaAutor()` (galería) y `detalleAutor()` (ficha, usada en la vista del autor **y** en
la hoja que aparece al escanear). **Todo texto que venga de datos pasa por `escapar()`** para evitar inyección
de HTML; mantené esa regla si agregás plantillas.

### `ra3d.js`

Vista de realidad aumentada. Dibuja los botones de autores, carga `modelos/{archivo}.glb` en
`<model-viewer id="visor-ra">` y muestra un aviso si el dispositivo no soporta RA. El nombre del modelo sale de
la foto: `img/roa-bastos.png` → `modelos/roa-bastos.glb`. Al cambiar de autor usa `history.replaceState`
para actualizar la URL sin volver a navegar (así no salta al principio de la página). Detalle completo en
[Realidad aumentada](realidad-aumentada.md).

### `escaner.js` y `camara.js`

El escáner prende la cámara, recorta lo que está dentro del marco verde y pregunta varias veces por segundo
qué autor es. Explicado en [Realidad aumentada → Escáner](realidad-aumentada.md#escáner-de-fichas-impresas).

`camara.js` encapsula `getUserMedia`: en el celular arranca con la cámara trasera y el botón alterna con la
frontal; en la PC recorre las webcams disponibles. Usa un contador `turno` para descartar cámaras que
terminan de abrir cuando el usuario ya se fue de la pantalla.

### `voz.js`

Lee el nombre, la biografía y los títulos de las obras con `speechSynthesis`, eligiendo una voz en español
si hay (prioriza es-PY, es-AR, es-419…). El mismo botón sirve para detener.

## Estilos

Todo en [`css/styles.css`](../src/main/resources/web/css/styles.css), ordenado por pantalla
(Bienvenida, Menú, Autor, RA 3D, Escáner, Modales, Responsive).

**Colores** (variables en `:root`):

| Variable | Valor | Uso |
|---|---|---|
| `--azul-marino` | `#1E293B` | Encabezado, títulos, botón principal |
| `--esmeralda` | `#10B981` | Todo lo de realidad aumentada y cámara |
| `--gris-claro` | `#F8FAFC` | Fondo |
| `--texto` | `#334155` | Texto de lectura |

Los mismos colores se usan en los modelos 3D (`herramientas/generar_modelos.py`), así la escultura combina con
la app.

**Botones**: `.boton` + una variante (`.boton-primario`, `.boton-ra`, `.boton-contorno`, `.boton-sutil`) +
tamaño opcional (`.boton-grande`, `.boton-chico`).

**Responsive** (mobile first en la práctica):

| Condición | Cambio |
|---|---|
| ≥ 768 px | La hoja del autor reconocido aparece centrada (en el celular sube desde abajo) |
| ≥ 600 px | Galería en 4 columnas (abajo de eso, 2) |
| ≤ 760 px | Ficha del autor en una columna |
| ≤ 640 px | Botones del menú apilados (RA primero), escáner a pantalla completa, sin QR |
| ≤ 340 px | Galería en 1 columna |
| Celular acostado (alto ≤ 500 px) | Escáner con controles al costado |

También respeta `prefers-reduced-motion` (desactiva animaciones).

## `marcadores.html`

Página independiente para imprimir las fichas: pide los autores con `api.js` y arma una ficha por autor con su
foto. Al imprimir oculta todo menos las fichas.

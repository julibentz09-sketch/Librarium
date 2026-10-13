# Arquitectura

## Visión general

```mermaid
flowchart LR
    subgraph Navegador["Navegador (PC o celular)"]
        UI["index.html + js/*.js<br/>(SPA con rutas #/...)"]
        MV["model-viewer<br/>RA 3D"]
        RL["reconocedor.js<br/>(solo sin servidor)"]
    end

    subgraph Java["Servidor Java (Librarium.java)"]
        HTTP["HttpServer :8080"]
        HTTPS["HttpsServer :8443"]
        API["ApiControlador /api/*"]
        ARCH["ArchivoControlador /*"]
        REC["ReconocedorImagen"]
        DAO["AutorDAO / MarcadorDAO"]
    end

    DB[("librarium.db<br/>SQLite")]
    WEB["recursos web/<br/>(dentro del jar)"]
    GLB["modelos/*.glb"]

    UI -- "fetch api/autores, api/reconocer" --> API
    UI -- "GET html, css, js, img" --> ARCH
    MV -- "GET modelos/autor.glb" --> ARCH
    HTTP & HTTPS --> API & ARCH
    API --> DAO --> DB
    API --> REC
    ARCH --> WEB
    WEB --- GLB
```

La aplicación es una **página única** (SPA) que habla con una **API REST** chica. El servidor Java sirve las dos
cosas: los archivos de la página y la API.

## Los dos modos de ejecución

La misma página funciona de dos formas, y **se da cuenta sola** de en cuál está
(ver [`js/api.js`](../src/main/resources/web/js/api.js)):

| | Con servidor Java | Sitio estático (GitHub Pages) |
|---|---|---|
| Cómo se usa | `java -jar librarium.jar` en una PC; el celular entra por WiFi | https://julibentz09-sketch.github.io/Librarium/ |
| Datos de autores | `GET /api/autores` (desde SQLite) | `datos/autores.json` (generado por `ExportadorSitio`) |
| Reconocimiento de fichas | `POST /api/reconocer` → `ReconocedorImagen.java` (OpenCV para Java) | En el navegador con `js/reconocedor.js` (OpenCV.js) |
| HTTPS para la cámara | Puerto 8443 con certificado autofirmado | Lo da GitHub Pages |
| QR para el celular | Muestra la IP local (`/api/info`) | Muestra la misma URL publicada |

**Cómo detecta el modo:** `obtenerAutores()` intenta `api/autores`; si falla (no hay servidor), activa la
bandera `sinServidor` y usa los JSON estáticos. Todas las funciones siguientes de `api.js` miran esa bandera.

> El algoritmo de reconocimiento existe **dos veces**: en Java y en JavaScript. Tienen que dar el mismo
> resultado. Si cambiás uno, cambiá el otro (mismas constantes y mismos pasos).

## Arranque del servidor

```mermaid
sequenceDiagram
    participant M as Librarium.main
    participant C as ConexionBD
    participant R as ReconocedorImagen
    participant S as Servidor
    M->>C: inicializar(): crea las tablas y, si no hay autores, carga datos.sql
    M->>R: agregarReferencia(autorId, foto) por cada marcador
    M->>S: iniciar(autorDAO, reconocedor)
    S->>S: HttpServer :8080
    S->>S: CertificadoSSL (genera librarium-cert.p12 si no existe) + HttpsServer :8443
    M->>M: muestra direcciones en consola y abre el navegador
```

Archivos que crea el servidor en la **carpeta desde donde se ejecuta** (están en `.gitignore`):

- `librarium.db`: la base SQLite. Solo se cargan los datos iniciales si la tabla `autor` está vacía.
- `librarium-cert.p12`: el certificado HTTPS autofirmado, generado con `keytool` del JDK.

## Flujo de la interfaz

```mermaid
flowchart TD
    B["#/ Bienvenida"] -->|Comenzar| M["#/menu Menú"]
    M --> G["#/galeria Galería"]
    M --> RA["#/ra Realidad aumentada 3D"]
    G --> A["#/autor/{id} Ficha del autor"]
    A -->|Ver en Realidad Aumentada| RA2["#/ra/{id}"]
    RA --> E["#/escanear Escanear ficha impresa"]
    E -->|ficha reconocida| H["Hoja con la ficha del autor"]
    H -->|Ver en Realidad Aumentada| RA2
    M -->|Salir| S["#/salir"]
```

## Generación de contenido (fuera de la app)

Hay contenido que **no se genera en tiempo de ejecución** sino con herramientas, y se guarda en el repositorio:

```mermaid
flowchart LR
    SQL["db/schema.sql + datos.sql"] --> GEN["herramientas/generar_modelos.py"]
    FOTOS["web/img/*.png"] --> GEN
    BUSTOS["herramientas/bustos/*.glb<br/>(hechos con IA)"] --> GEN
    GEN --> MOD["web/modelos/*.glb"]
    SQL --> EXP["ExportadorSitio.java<br/>(en GitHub Actions)"]
    EXP --> JSON["sitio/datos/*.json"]
```

- `web/modelos/*.glb` **se versionan en git**: si cambiás datos o fotos hay que volver a correr el script.
- `sitio/datos/*.json` **no se versionan**: los genera el workflow de GitHub Actions en cada publicación.

## Decisiones de diseño

| Decisión | Por qué | Costo |
|---|---|---|
| Servidor HTTP del JDK, sin Spring | El proyecto es chico y educativo; arranca rápido y el jar es liviano | El ruteo de la API es manual (`if` por ruta) |
| SQLite | Un archivo, sin instalar nada | No sirve para muchos usuarios escribiendo a la vez (acá solo se lee) |
| JSON propio (`util/Json`) | Evitar dependencias | Solo serializa; no lee JSON |
| Frontend sin frameworks ni build | Se edita y se recarga; cualquiera lo entiende | Más código manual para las vistas |
| Rutas con `#` (hash) | Funciona igual con el servidor Java y en GitHub Pages sin configuración | URLs con `#` |
| `<model-viewer>` para RA | Es la tecnología de los "animales 3D" de Google: WebXR, Scene Viewer y Quick Look con un solo componente | Depende de un CDN y de que el celular soporte RA |
| Modelos 3D generados por script | Los textos de los paneles salen de la base de datos, no se dibujan a mano | Hay que regenerar al cambiar datos |
| Reconocimiento con OpenCV (ORB + homografía) | Técnica estándar para reconocer imágenes planas; soporta fichas giradas e inclinadas y da sus esquinas; existe para Java y para el navegador | El jar pesa ~100 MB; OpenCV.js son ~10 MB a descargar en Pages |
| HTTPS autofirmado en la red local | Los navegadores del celular solo dan la cámara en HTTPS | Aviso de seguridad la primera vez |

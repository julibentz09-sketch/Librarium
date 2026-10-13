# Guía de desarrollo

## Preparar el entorno

| Herramienta | ¿Obligatoria? | Para qué |
|---|---|---|
| **JDK 17 o superior** ([Temurin](https://adoptium.net)) | Sí | Compilar y correr el servidor. Trae `keytool`, que se usa para el HTTPS |
| Maven | No | Viene incluido con el wrapper `mvnw` / `mvnw.cmd` |
| Python 3 + Pillow (`pip install pillow`) | Solo para modelos 3D | Correr `herramientas/generar_modelos.py` |
| Node.js | Solo para bustos | Simplificar bustos con `npx @gltf-transform/cli` |
| Un celular | Recomendado | Probar la cámara y la realidad aumentada |

Cualquier IDE sirve (IntelliJ, VS Code, NetBeans, Eclipse): abrí la carpeta como **proyecto Maven**.

Sin instalar nada: **GitHub Codespaces** (ver [más abajo](#codespaces)).

## Correr el proyecto

```
mvnw package                    # compila, corre las pruebas y arma target/librarium.jar
java -jar target/librarium.jar  # abre http://localhost:8080
```

En Windows también funciona `iniciar.bat` con doble clic, y en Linux/macOS `./iniciar.sh`. Si el jar no
existe, lo compilan primero.

Atajos útiles:

```
mvnw -DskipTests package        # compilar sin correr las pruebas
mvnw compile exec:java          # correr sin armar el jar
mvnw test                       # solo pruebas
```

### Ciclo de trabajo

- **Cambios en HTML/CSS/JS o Java:** el servidor lee la página desde el classpath (`target/classes` o el
  jar), no desde `src/`. Después de cada cambio: detener (`Ctrl + C`), `mvnw -DskipTests package` (o
  *Build* en el IDE) y volver a iniciar. Tarda unos segundos.
- **Ojo con `iniciar.bat` / `iniciar.sh`:** solo compilan si **no existe** `target/librarium.jar`. Si cambiaste
  algo, recompilá a mano o borrá el jar; si no, vas a ver la versión vieja.
- **Cambios en `datos.sql`:** borrar `librarium.db` y reiniciar (si no, se siguen viendo los datos viejos).
  Si el cambio afecta los paneles 3D, regenerar los modelos.

### Probar en el celular

1. PC y celular en la **misma red WiFi**.
2. Escanear el QR del menú o entrar a la dirección `https://IP:8443` que muestra la consola.
3. Aceptar el aviso de seguridad (**Avanzado → Continuar**).
4. La primera vez, Windows pide permiso para que Java use la red: permitir en **redes privadas**.

## Tareas comunes

### Agregar un autor

1. **Foto:** guardala en `src/main/resources/web/img/<id>.png`, donde `<id>` es el slug del autor
   (minúsculas y guiones, por ejemplo `elvio-romero`). Que sea de frente y con buen contraste, porque también
   es la referencia del escáner.
2. **Datos:** en `src/main/resources/db/datos.sql` agregá:
   - el `INSERT INTO autor` (con `orden` para su lugar en la galería y `foto = 'img/<id>.png'`);
   - sus obras en `obra`;
   - sus filas en `multimedia` (como las de los demás);
   - su marcador: `('MK-XXX', '<id>', 'img/<id>.png')`.
3. **Busto (opcional):** ver [Bustos hechos con IA](realidad-aumentada.md#bustos-hechos-con-ia). Si no hay
   busto, la escultura muestra el retrato enmarcado.
4. **Modelo 3D:** `python herramientas/generar_modelos.py`.
5. **Pruebas:** actualizá los valores fijos:
   - `ConexionBDTest`: cantidad de autores (hoy `4`), de marcadores (hoy `4`) y la condición de 3 obras por autor;
   - `ReconocedorImagenTest`: el arreglo `AUTORES` y los `@ValueSource`.
6. **Probar:** borrar `librarium.db`, `mvnw package`, iniciar y revisar la galería, la ficha, la RA y el
   escáner con la ficha impresa (`marcadores.html`). Si el escáner confunde al autor nuevo con otro, la foto
   necesita más contraste o ser más distinta.

### Cambiar la biografía u obras de un autor

1. Editá `datos.sql`.
2. Borrá `librarium.db`.
3. Regenerá los modelos 3D (los paneles tienen el texto).
4. Corré las pruebas.

### Cambiar textos o diseño de una pantalla

Los textos fijos están en `index.html`; los que dependen del autor, en `js/plantillas.js`. Los estilos, en
`css/styles.css` (ver [Frontend → Estilos](frontend.md#estilos)). Probá en ancho de celular (≤ 640 px) y de PC.

### Agregar un endpoint o una pantalla

Ver [Backend → Agregar un endpoint](backend.md#agregar-un-endpoint) y
[Frontend → Agregar una pantalla](frontend.md#agregar-una-pantalla).

## Publicación

### GitHub Pages (automático)

Cada `push` a `main` dispara [`.github/workflows/pages.yml`](../.github/workflows/pages.yml):

1. `./mvnw -B package` → compila y **corre las pruebas** (si fallan, no se publica).
2. Copia `src/main/resources/web/` a `sitio/`.
3. `ExportadorSitio` genera `sitio/datos/autores.json` y `marcadores.json`.
4. Publica `sitio/` en https://julibentz09-sketch.github.io/Librarium/.

Tarda alrededor de un minuto. El estado se ve en la pestaña **Actions** del repositorio. GitHub Pages guarda
caché unos minutos: si no ves el cambio, recargá con `Ctrl + F5`.

Para activarlo en un repositorio nuevo: **Settings → Pages → Source: GitHub Actions**.

### Codespaces

1. **Code → Codespaces → Create codespace on main**.
2. Al crearse compila solo (`postCreateCommand`) y al conectarse arranca el servidor (`postAttachCommand`).
3. En **Puertos**, poné el 8080 como **Public** para abrirlo desde el celular. La URL ya es HTTPS, así que la
   cámara funciona sin el puerto 8443.
4. Al terminar, **Stop** para no gastar horas gratuitas.

## Git

- Mensajes de commit en español, cortos y en presente o como título:
  "Pantalla de bienvenida antes del menú", "Bustos 3D de Roa Bastos y Josefina Plá".
- `librarium.db`, `librarium-cert.p12` y `target/` no se suben (`.gitignore`).
- `web/modelos/*.glb` y `herramientas/bustos/*.glb` **sí** se suben; están marcados como binarios en
  `.gitattributes`.

## Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| "El puerto 8080 ya está en uso" | Librarium ya está abierto en otra consola, o usá `--puerto=8081` |
| Edité `datos.sql` y no cambia nada | Borrá `librarium.db` y reiniciá |
| El celular no abre la página | Misma WiFi; permiso de red para Java en Windows; usar `https://IP:8443`, no `http` |
| El celular abre la página pero no la cámara | Estás por `http`. La cámara solo funciona por HTTPS (puerto 8443, Pages o Codespaces) |
| Aviso "no se pudo iniciar HTTPS" en la consola | Falta `keytool` (estás usando un JRE en vez de un JDK) |
| No aparece "Ver en tu espacio" | El dispositivo no soporta RA (PC, o Android sin ARCore). En la PC se puede girar el modelo igual |
| La escultura no muestra los datos nuevos | Regenerá los modelos con `generar_modelos.py` y recargá con `Ctrl + F5` |
| El escáner no reconoce la ficha | Más luz, sin reflejos, la foto ocupando casi todo el marco verde, ficha quieta |
| GitHub Pages no se actualizó | Revisá **Actions**: probablemente falló una prueba o la compilación |
| Acentos raros en la consola de Windows | `iniciar.bat` ya pone `chcp 65001`; si corrés a mano, ejecutá `chcp 65001` antes |

## Pendientes y limitaciones conocidas

- **Bustos de Delfina Acosta y Mauricio Cardozo Ocampo:** todavía muestran el retrato enmarcado (ver
  [estado de los bustos](realidad-aumentada.md#bustos-hechos-con-ia)).
- **Reconocimiento duplicado:** `ReconocedorImagen.java` (OpenCV Java) y `reconocedor.js` (OpenCV.js) tienen
  que mantenerse iguales a mano; solo el de Java tiene pruebas automáticas.
- **Peso de OpenCV:** el jar pesa ~100 MB y en GitHub Pages el escáner descarga ~10 MB la primera vez.
- **Sin pruebas de frontend:** la interfaz se prueba a mano.
- **Depende de internet para la RA y el QR:** `<model-viewer>`, `qrcode.js` y (en Pages) OpenCV.js se cargan
  desde CDN. Con el servidor Java y sin internet, la galería y el escáner funcionan, pero la vista 3D y el QR no.
- **Peso de los modelos:** ~1,2 MB con busto; con datos móviles tarda unos segundos en cargar.
- **Tabla `multimedia`:** hoy solo se usa como registro. El audio de la biografía se genera con la voz del
  navegador (por eso la URL es `NULL`) y el tipo `video` todavía no se usa.
- **Sin autenticación ni edición:** la app es de solo lectura; los datos se cambian editando `datos.sql`.

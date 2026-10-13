# Documentación de Librarium

Documentación técnica para programadores que se suman al proyecto. Si solo querés usar la app, alcanza con el
[README principal](../README.md).

## Qué es Librarium en 30 segundos

Una aplicación web para que estudiantes de secundaria conozcan autores paraguayos. Tiene:

- una **galería digital** con la biografía y las obras de cada autor;
- **realidad aumentada**: con la cámara del celular se coloca en el espacio real una escultura 3D del autor
  (pedestal, busto o retrato, y paneles con su biografía y sus obras);
- un **escáner de fichas impresas**: se muestra a la cámara la foto impresa de un autor y la app lo reconoce
  con **OpenCV**.

El backend es **Java 17 sin frameworks** (servidor HTTP del JDK + SQLite). El frontend es **HTML, CSS y
JavaScript sin frameworks ni build** (módulos ES nativos). La misma página funciona con el servidor Java o como
sitio estático en GitHub Pages.

## Por dónde empezar

| Si querés… | Leé |
|---|---|
| Entender cómo está armado todo | [Arquitectura](arquitectura.md) |
| Levantar el proyecto y hacer tu primer cambio | [Guía de desarrollo](guia-desarrollo.md) |
| Tocar el servidor, la base de datos o la API | [Backend](backend.md) |
| Tocar pantallas, estilos o navegación | [Frontend](frontend.md) |
| Tocar la realidad aumentada, los modelos 3D o el escáner | [Realidad aumentada](realidad-aumentada.md) |
| Agregar un autor | [Guía de desarrollo → Agregar un autor](guia-desarrollo.md#agregar-un-autor) |
| Saber qué falta o qué está flojo | [Guía de desarrollo → Pendientes y limitaciones](guia-desarrollo.md#pendientes-y-limitaciones-conocidas) |

## Mapa rápido del repositorio

```
Librarium/
├── src/main/java/py/librarium/   servidor Java (API REST, base de datos, reconocimiento de imágenes)
├── src/main/resources/db/        schema.sql y datos.sql (toda la información de los autores)
├── src/main/resources/web/       la página web completa (se sirve tal cual, sin compilar)
├── src/test/java/                pruebas JUnit 5
├── herramientas/                 script que genera los modelos 3D + bustos hechos con IA
├── docs/                         esta documentación
├── .github/workflows/pages.yml   compila, prueba y publica en GitHub Pages
├── .devcontainer/                configuración para GitHub Codespaces
├── iniciar.bat / iniciar.sh      arranque con doble clic
└── pom.xml, mvnw                 build con Maven (no hace falta instalar Maven)
```

## Convenciones del proyecto

- **Todo en español**: nombres de clases, métodos, variables, archivos, commits y textos de la interfaz.
  Los textos para el usuario usan voseo ("Elegí", "Tocá").
- **Sin dependencias innecesarias**: el backend usa `sqlite-jdbc` y OpenCV; el frontend carga
  `<model-viewer>`, `qrcode.js` y OpenCV.js (solo en Pages) desde CDN. Antes de sumar una librería, conviene ver si se puede resolver
  con lo que ya hay.
- **Comentarios cortos que explican el porqué**, no el qué.

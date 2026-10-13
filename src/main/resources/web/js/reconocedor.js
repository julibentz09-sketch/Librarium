// Mismo algoritmo que ReconocedorImagen.java (OpenCV: ORB + BFMatcher + homografía con RANSAC),
// para la versión de GitHub Pages: ahí no hay servidor Java y el reconocimiento se hace en el navegador
// con OpenCV.js. Pesa unos 10 MB, por eso se descarga recién al entrar a escanear.

const URL_OPENCV = 'https://cdn.jsdelivr.net/npm/@techstark/opencv-js@4.10.0-release.1/dist/opencv.js';

export const UMBRAL = 0.50;
const INLIERS_PUNTAJE_COMPLETO = 30;
const VENTAJA_SOBRE_SEGUNDO = 2.0;
const PUNTOS_ORB = 1000;
const LADO_MAXIMO = 480;
const PRUEBA_DE_LOWE = 0.75;
const PARES_MINIMOS = 8;
const ERROR_RANSAC = 5.0;
const AREA_MINIMA = 0.01;

let cv = null;
let cargando = null;
const referencias = [];

export function cargarOpenCV() {
  if (!cargando) {
    cargando = new Promise((listo, fallo) => {
      const script = document.createElement('script');
      script.src = URL_OPENCV;
      script.async = true;
      script.onload = async () => {
        try {
          let modulo = window.cv;
          // según la versión, opencv.js deja una promesa o un objeto que avisa cuando termina de iniciar
          if (modulo instanceof Promise) {
            modulo = await modulo;
          } else if (!modulo.Mat) {
            await new Promise((iniciado) => { modulo.onRuntimeInitialized = iniciado; });
          }
          cv = modulo;
          // no se pasa el módulo a listo(): tiene un método then() y la promesa se quedaría
          // esperándolo para siempre (pasa con los módulos de Emscripten)
          listo();
        } catch (error) {
          fallo(error);
        }
      };
      script.onerror = () => {
        cargando = null;
        fallo(new Error('No se pudo descargar OpenCV'));
      };
      document.head.appendChild(script);
    });
  }
  return cargando;
}

// Lee un canvas o una imagen a escala de grises y achica lo muy grande (ORB no necesita más)
function leerGris(fuente) {
  const color = cv.imread(fuente);
  const gris = new cv.Mat();
  cv.cvtColor(color, gris, cv.COLOR_RGBA2GRAY);
  color.delete();
  const lado = Math.max(gris.cols, gris.rows);
  if (lado <= LADO_MAXIMO) return gris;
  const chica = new cv.Mat();
  const escala = LADO_MAXIMO / lado;
  cv.resize(gris, chica, new cv.Size(0, 0), escala, escala, cv.INTER_AREA);
  gris.delete();
  return chica;
}

function detectar(gris) {
  const orb = new cv.ORB(PUNTOS_ORB);
  const puntos = new cv.KeyPointVector();
  const descriptores = new cv.Mat();
  const sinMascara = new cv.Mat();
  orb.detectAndCompute(gris, sinMascara, puntos, descriptores);
  orb.delete();
  sinMascara.delete();
  return { puntos, descriptores };
}

// Lleva las esquinas de la foto a la imagen. Si el contorno sale retorcido o diminuto,
// la homografía es casualidad y no se cuenta.
function proyectarEsquinas(homografia, ref, ancho, alto) {
  const esquinasRef = cv.matFromArray(4, 1, cv.CV_32FC2, [0, 0, ref.ancho, 0, ref.ancho, ref.alto, 0, ref.alto]);
  const proyectadas = new cv.Mat();
  cv.perspectiveTransform(esquinasRef, proyectadas, homografia);
  const p = Array.from(proyectadas.data32F);
  esquinasRef.delete();
  proyectadas.delete();

  const contorno = cv.matFromArray(4, 1, cv.CV_32SC2, p.map(Math.round));
  const poligono = cv.matFromArray(4, 1, cv.CV_32FC2, p);
  const convexo = cv.isContourConvex(contorno);
  const area = cv.contourArea(poligono);
  contorno.delete();
  poligono.delete();
  if (!convexo || area < AREA_MINIMA * ancho * alto) return null;

  const redondear = (valor) => Math.round(valor * 1000) / 1000;
  const esquinas = [];
  for (let i = 0; i < 4; i++) {
    esquinas.push([redondear(p[i * 2] / ancho), redondear(p[i * 2 + 1] / alto)]);
  }
  return esquinas;
}

function comparar(ref, cuadro, ancho, alto) {
  const buscador = new cv.BFMatcher(cv.NORM_HAMMING, false);
  const vecinos = new cv.DMatchVectorVector();
  buscador.knnMatch(cuadro.descriptores, ref.descriptores, vecinos, 2);

  const origen = [];
  const destino = [];
  for (let i = 0; i < vecinos.size(); i++) {
    const par = vecinos.get(i);
    if (par.size() === 2 && par.get(0).distance < PRUEBA_DE_LOWE * par.get(1).distance) {
      const m = par.get(0);
      const pr = ref.puntos.get(m.trainIdx).pt;
      const pc = cuadro.puntos.get(m.queryIdx).pt;
      origen.push(pr.x, pr.y);
      destino.push(pc.x, pc.y);
    }
    par.delete();
  }
  vecinos.delete();
  buscador.delete();
  if (origen.length / 2 < PARES_MINIMOS) return { inliers: 0, esquinas: null };

  const src = cv.matFromArray(origen.length / 2, 1, cv.CV_32FC2, origen);
  const dst = cv.matFromArray(destino.length / 2, 1, cv.CV_32FC2, destino);
  const mascara = new cv.Mat();
  const homografia = cv.findHomography(src, dst, cv.RANSAC, ERROR_RANSAC, mascara);
  let resultado = { inliers: 0, esquinas: null };
  if (!homografia.empty()) {
    const esquinas = proyectarEsquinas(homografia, ref, ancho, alto);
    if (esquinas) resultado = { inliers: cv.countNonZero(mascara), esquinas };
  }
  src.delete();
  dst.delete();
  mascara.delete();
  homografia.delete();
  return resultado;
}

function cargarImagen(src) {
  return new Promise((listo, error) => {
    const img = new Image();
    img.onload = () => listo(img);
    img.onerror = () => error(new Error(`No se pudo cargar ${src}`));
    img.src = src;
  });
}

export async function cargarReferencias(marcadores) {
  await cargarOpenCV();
  for (const marcador of marcadores) {
    const foto = await cargarImagen(marcador.imagen);
    const lienzo = document.createElement('canvas');
    lienzo.width = foto.naturalWidth;
    lienzo.height = foto.naturalHeight;
    lienzo.getContext('2d').drawImage(foto, 0, 0);
    const gris = leerGris(lienzo);
    const { puntos, descriptores } = detectar(gris);
    if (descriptores.rows >= PARES_MINIMOS) {
      referencias.push({ autorId: marcador.autorId, ancho: gris.cols, alto: gris.rows, puntos, descriptores });
    } else {
      puntos.delete();
      descriptores.delete();
    }
    gris.delete();
  }
}

export function analizar(lienzo) {
  const gris = leerGris(lienzo);
  const cuadro = detectar(gris);
  let mejorId = null;
  let mejor = { inliers: 0, esquinas: null };
  let segundo = 0;
  if (cuadro.descriptores.rows >= PARES_MINIMOS) {
    for (const ref of referencias) {
      const c = comparar(ref, cuadro, gris.cols, gris.rows);
      if (c.inliers > mejor.inliers) {
        segundo = mejor.inliers;
        mejor = c;
        mejorId = ref.autorId;
      } else if (c.inliers > segundo) {
        segundo = c.inliers;
      }
    }
  }
  gris.delete();
  cuadro.puntos.delete();
  cuadro.descriptores.delete();

  const puntaje = Math.min(1, mejor.inliers / INLIERS_PUNTAJE_COMPLETO);
  const coincide = mejorId !== null && puntaje >= UMBRAL && mejor.inliers >= VENTAJA_SOBRE_SEGUNDO * segundo;
  return {
    coincide,
    autorId: coincide ? mejorId : null,
    puntaje: Math.round(puntaje * 1000) / 1000,
    umbral: UMBRAL,
    esquinas: coincide ? mejor.esquinas : null
  };
}

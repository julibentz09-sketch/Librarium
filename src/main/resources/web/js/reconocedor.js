// Mismo algoritmo que ReconocedorImagen.java, para la versión de GitHub Pages
// (ahí no hay servidor Java y el reconocimiento se hace en el navegador).

export const UMBRAL = 0.50;
const MARGEN = 0.08;
const LADO = 32;
const ZONAS = 4;
const ORIENTACIONES = 8;
const CONTRASTE_MINIMO = 6.0;
const ESCALAS = [1.0, 0.87, 0.76, 0.66, 0.57, 0.49, 0.42];
const CORRIMIENTOS = [-0.12, 0, 0.12];
const TOLERANCIA_COLOR = 6.0;
const ESCALA_COLOR = 40.0;

const referencias = [];

function imagenGris({ width, height, data }) {
  const total = width * height;
  const gris = new Float32Array(total);
  const u = new Float32Array(total);
  const v = new Float32Array(total);
  for (let i = 0; i < total; i++) {
    const alfa = data[i * 4 + 3] / 255;
    const r = data[i * 4] * alfa + 255 * (1 - alfa);
    const g = data[i * 4 + 1] * alfa + 255 * (1 - alfa);
    const b = data[i * 4 + 2] * alfa + 255 * (1 - alfa);
    gris[i] = 0.299 * r + 0.587 * g + 0.114 * b;
    u[i] = r - g;
    v[i] = b - (r + g) / 2;
  }
  return { ancho: width, alto: height, gris, u, v };
}

const limitar = (valor, min, max) => Math.max(min, Math.min(max, valor));

function reducir(imagen, canal, x, y, w, h, anchoSalida, altoSalida) {
  const salida = new Float32Array(anchoSalida * altoSalida);
  for (let fila = 0; fila < altoSalida; fila++) {
    const y0 = limitar(Math.floor(y + fila * h / altoSalida), 0, imagen.alto - 1);
    const y1 = limitar(Math.floor(y + (fila + 1) * h / altoSalida), y0 + 1, imagen.alto);
    for (let col = 0; col < anchoSalida; col++) {
      const x0 = limitar(Math.floor(x + col * w / anchoSalida), 0, imagen.ancho - 1);
      const x1 = limitar(Math.floor(x + (col + 1) * w / anchoSalida), x0 + 1, imagen.ancho);
      let suma = 0;
      for (let yy = y0; yy < y1; yy++) {
        for (let xx = x0; xx < x1; xx++) {
          suma += canal[yy * imagen.ancho + xx];
        }
      }
      salida[fila * anchoSalida + col] = suma / ((y1 - y0) * (x1 - x0));
    }
  }
  return salida;
}

function promedio(valores) {
  let suma = 0;
  for (const v of valores) suma += v;
  return suma / valores.length;
}

function desvioEstandar(valores) {
  const media = promedio(valores);
  let suma = 0;
  for (const v of valores) suma += (v - media) * (v - media);
  return Math.sqrt(suma / valores.length);
}

function normalizar(valores) {
  const media = promedio(valores);
  const r = new Float32Array(valores.length);
  let norma = 0;
  for (let i = 0; i < valores.length; i++) {
    r[i] = valores[i] - media;
    norma += r[i] * r[i];
  }
  norma = Math.sqrt(norma);
  if (norma > 0) {
    for (let i = 0; i < r.length; i++) r[i] /= norma;
  }
  return r;
}

function productoPunto(a, b) {
  let suma = 0;
  for (let i = 0; i < a.length; i++) suma += a[i] * b[i];
  return suma;
}

function histogramaBordes(p) {
  const hist = new Float32Array(ZONAS * ZONAS * ORIENTACIONES);
  const tamZona = LADO / ZONAS;
  for (let y = 1; y < LADO - 1; y++) {
    for (let x = 1; x < LADO - 1; x++) {
      const gx = p[y * LADO + x + 1] - p[y * LADO + x - 1];
      const gy = p[(y + 1) * LADO + x] - p[(y - 1) * LADO + x];
      const magnitud = Math.hypot(gx, gy);
      if (magnitud === 0) continue;
      let angulo = Math.atan2(gy, gx);
      if (angulo < 0) angulo += Math.PI;
      const orientacion = Math.min(ORIENTACIONES - 1, Math.floor(angulo / Math.PI * ORIENTACIONES));
      const zona = Math.floor(y / tamZona) * ZONAS + Math.floor(x / tamZona);
      hist[zona * ORIENTACIONES + orientacion] += magnitud;
    }
  }
  return hist;
}

function colorPorZona(imagen, x, y, w, h) {
  const u = reducir(imagen, imagen.u, x, y, w, h, ZONAS, ZONAS);
  const v = reducir(imagen, imagen.v, x, y, w, h, ZONAS, ZONAS);
  const promedioU = promedio(u);
  const promedioV = promedio(v);
  const color = new Float32Array(u.length);
  for (let i = 0; i < color.length; i++) {
    color[i] = Math.hypot(u[i] - promedioU, v[i] - promedioV);
  }
  return color;
}

function calcularFirma(imagen, x, y, w, h) {
  const p = reducir(imagen, imagen.gris, x, y, w, h, LADO, LADO);
  if (desvioEstandar(p) < CONTRASTE_MINIMO) return null;
  return { gris: normalizar(p), bordes: normalizar(histogramaBordes(p)), color: colorPorZona(imagen, x, y, w, h) };
}

function comparar(cuadro, referencia) {
  const forma = 0.5 * productoPunto(cuadro.gris, referencia.gris) + 0.5 * productoPunto(cuadro.bordes, referencia.bordes);
  let exceso = 0;
  for (let i = 0; i < cuadro.color.length; i++) {
    exceso += Math.max(0, cuadro.color[i] - referencia.color[i] - TOLERANCIA_COLOR);
  }
  return forma - Math.min(0.5, exceso / (cuadro.color.length * ESCALA_COLOR));
}

function buscar(imagen, ref) {
  let baseAncho = imagen.ancho;
  let baseAlto = imagen.ancho / ref.proporcion;
  if (baseAlto > imagen.alto) {
    baseAlto = imagen.alto;
    baseAncho = imagen.alto * ref.proporcion;
  }
  let mejor = 0;
  for (const escala of ESCALAS) {
    const w = baseAncho * escala;
    const h = baseAlto * escala;
    for (const dy of CORRIMIENTOS) {
      for (const dx of CORRIMIENTOS) {
        const x = Math.max(0, Math.min(imagen.ancho - w, (imagen.ancho - w) / 2 + dx * w));
        const y = Math.max(0, Math.min(imagen.alto - h, (imagen.alto - h) / 2 + dy * h));
        const firma = calcularFirma(imagen, x, y, w, h);
        if (firma) mejor = Math.max(mejor, comparar(firma, ref.firma));
      }
    }
  }
  return mejor;
}

function leerPixeles(fuente, ancho, alto) {
  const lienzo = document.createElement('canvas');
  lienzo.width = ancho;
  lienzo.height = alto;
  const ctx = lienzo.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(fuente, 0, 0);
  return ctx.getImageData(0, 0, ancho, alto);
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
  for (const marcador of marcadores) {
    const foto = await cargarImagen(marcador.imagen);
    const imagen = imagenGris(leerPixeles(foto, foto.naturalWidth, foto.naturalHeight));
    const firma = calcularFirma(imagen, 0, 0, imagen.ancho, imagen.alto);
    if (firma) {
      referencias.push({ autorId: marcador.autorId, proporcion: imagen.ancho / imagen.alto, firma });
    }
  }
}

export function analizar(lienzo) {
  const ctx = lienzo.getContext('2d', { willReadFrequently: true });
  const imagen = imagenGris(ctx.getImageData(0, 0, lienzo.width, lienzo.height));
  let mejorId = null;
  let mejor = 0;
  let segundo = 0;
  for (const ref of referencias) {
    const puntaje = buscar(imagen, ref);
    if (puntaje > mejor) {
      segundo = mejor;
      mejor = puntaje;
      mejorId = ref.autorId;
    } else if (puntaje > segundo) {
      segundo = puntaje;
    }
  }
  const coincide = mejorId !== null && mejor >= UMBRAL && mejor - segundo >= MARGEN;
  return { coincide, autorId: coincide ? mejorId : null, puntaje: Math.round(mejor * 1000) / 1000, umbral: UMBRAL };
}

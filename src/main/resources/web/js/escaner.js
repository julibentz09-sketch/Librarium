import * as api from './api.js';
import * as camara from './camara.js';
import * as voz from './voz.js';
import { detalleAutor, escapar } from './plantillas.js';

const INTERVALO_MS = 120;
const CUADROS_PARA_CONFIRMAR = 3;
const LADO_RECORTE = 192;
const MARGEN_RECORTE = 1.5;      // se manda el marco con margen por si la foto es más grande
const ESPERA_FICHA_MS = 1100;

const $ = (selector) => document.querySelector(selector);

export function crearEscaner({ buscarAutor, infoServidor }) {
  const video = $('#ra-video');
  const escenario = $('#ra-escenario');
  const marco = $('#ra-marco');
  const estado = $('#ra-estado');
  const cajaEstado = estado.parentElement;
  const barra = $('#ra-barra');
  const etiquetaCamara = $('#ra-camara');
  const btnCambiar = $('#btn-cambiar');
  const btnEscaneo = $('#btn-escaneo');
  const aviso = $('#ra-aviso');
  const avisoTexto = $('#ra-aviso-texto');
  const btnReintentar = $('#btn-reintentar');
  const ficha = $('#ra-hoja');
  const fichaCuerpo = $('#ra-hoja-cuerpo');

  const lienzo = document.createElement('canvas');
  lienzo.width = lienzo.height = LADO_RECORTE;
  const ctx = lienzo.getContext('2d');

  let activo = false;
  let escaneando = false;
  let detenidoPorUsuario = false;
  let espejado = false;
  let ciclo = 0;
  let temporizador = null;
  let temporizadorFicha = null;
  let racha = { id: null, cantidad: 0 };

  function mostrarEstado(texto, reconocido = false) {
    if (estado.textContent !== texto) estado.textContent = texto;
    cajaEstado.classList.toggle('ok', reconocido);
  }

  function mostrarAviso(html, conReintento = true) {
    if (!html) {
      aviso.hidden = true;
      return;
    }
    avisoTexto.innerHTML = html;
    btnReintentar.hidden = !conReintento;
    aviso.hidden = false;
    mostrarEstado('Cámara no disponible');
  }

  function actualizarBoton() {
    btnEscaneo.textContent = camara.activa() && !detenidoPorUsuario ? 'Detener Escaneo' : 'Reanudar Escaneo';
  }

  function mensajeDeError(error) {
    switch (error.name) {
      case 'NotAllowedError':
      case 'SecurityError':
        return 'No se dio permiso para usar la cámara. Tocá el candado al lado de la dirección, '
          + 'permití la <strong>Cámara</strong> y presioná Reintentar.';
      case 'NotFoundError':
      case 'OverconstrainedError':
        return 'No se encontró ninguna cámara en este dispositivo.';
      case 'NotReadableError':
      case 'AbortError':
        return 'La cámara está siendo usada por otra aplicación (Zoom, Meet, etc.). Cerrala y presioná Reintentar.';
      default:
        return `No se pudo encender la cámara (${escapar(error.message || error.name)}).`;
    }
  }

  function mensajeSinHttps() {
    const info = infoServidor();
    const url = `https://${location.hostname}:${info.puertoHttps}/#/ra`;
    if (!info.https) {
      return 'La cámara solo funciona en páginas seguras (HTTPS) o en <strong>localhost</strong>.';
    }
    return `Para usar la cámara del celular abrí la versión segura:<br><a href="${escapar(url)}">${escapar(url)}</a><br>`
      + '<small>Si aparece un aviso de seguridad, tocá "Avanzado" y luego "Continuar".</small>';
  }

  async function encenderCamara(cambiarCamara = false) {
    mostrarAviso(null);
    mostrarEstado('Iniciando cámara…');
    if (!window.isSecureContext) {
      mostrarAviso(mensajeSinHttps(), false);
      return;
    }
    if (!camara.soportada()) {
      mostrarAviso('Este navegador no permite usar la cámara. Probá con Chrome, Edge, Firefox o Safari actualizados.', false);
      return;
    }
    try {
      const info = cambiarCamara ? await camara.cambiar(video) : await camara.iniciar(video);
      if (!activo) {
        camara.detener();
        return;
      }
      espejado = info.espejado;
      escenario.classList.toggle('espejado', espejado);
      etiquetaCamara.textContent = info.nombre;
      etiquetaCamara.hidden = false;
      btnCambiar.hidden = !info.puedeCambiar;
      detenidoPorUsuario = false;
      if (ficha.hidden) empezarEscaneo();
      actualizarBoton();
    } catch (error) {
      if (error.name !== 'CanceladoError') mostrarAviso(mensajeDeError(error));
    }
  }

  function apagarCamara() {
    pararEscaneo();
    camara.detener();
    video.srcObject = null;
    etiquetaCamara.hidden = true;
  }

  function empezarEscaneo() {
    escaneando = true;
    racha = { id: null, cantidad: 0 };
    marco.classList.remove('reconocido');
    escenario.classList.add('escaneando');
    mostrarEstado('Colocá la foto del autor dentro del marco verde');
    actualizarBoton();
    analizar(++ciclo);
  }

  function pararEscaneo() {
    escaneando = false;
    ciclo++;
    clearTimeout(temporizador);
    escenario.classList.remove('escaneando');
    barra.style.width = '0%';
    actualizarBoton();
  }

  // Cuadrado del video, en píxeles de la cámara, centrado en el marco verde y con margen
  function regionDelMarco() {
    const esc = escenario.getBoundingClientRect();
    const mar = marco.getBoundingClientRect();
    const escala = Math.max(esc.width / video.videoWidth, esc.height / video.videoHeight);
    const desplX = (esc.width - video.videoWidth * escala) / 2;
    const desplY = (esc.height - video.videoHeight * escala) / 2;
    let centroX = mar.left - esc.left + mar.width / 2;
    if (espejado) centroX = esc.width - centroX;
    const centroY = mar.top - esc.top + mar.height / 2;
    const lado = (mar.width / escala) * MARGEN_RECORTE;
    return {
      x: (centroX - desplX) / escala - lado / 2,
      y: (centroY - desplY) / escala - lado / 2,
      lado
    };
  }

  // Lo que queda fuera del video se pinta de gris para que el marco siga centrado
  function copiarRegion({ x, y, lado }) {
    const k = lienzo.width / lado;
    const x0 = Math.max(0, x);
    const y0 = Math.max(0, y);
    const x1 = Math.min(video.videoWidth, x + lado);
    const y1 = Math.min(video.videoHeight, y + lado);
    ctx.fillStyle = '#808080';
    ctx.fillRect(0, 0, lienzo.width, lienzo.height);
    if (x1 > x0 && y1 > y0) {
      ctx.drawImage(video, x0, y0, x1 - x0, y1 - y0, (x0 - x) * k, (y0 - y) * k, (x1 - x0) * k, (y1 - y0) * k);
    }
  }

  async function analizar(miCiclo) {
    if (!escaneando || miCiclo !== ciclo) return;
    try {
      if (video.readyState >= 2 && video.videoWidth > 0) {
        copiarRegion(regionDelMarco());
        const imagen = await new Promise((listo) => lienzo.toBlob(listo, 'image/jpeg', 0.85));
        const resultado = await api.reconocer(imagen);
        if (!escaneando || miCiclo !== ciclo) return;
        procesarResultado(resultado);
        if (!escaneando) return;
      }
    } catch (error) {
      mostrarEstado('Sin conexión con el servidor. Reintentando…');
    }
    temporizador = setTimeout(() => analizar(miCiclo), INTERVALO_MS);
  }

  function procesarResultado(resultado) {
    const progreso = Math.max(0, Math.min(1, (resultado.puntaje - 0.15) / (resultado.umbral - 0.15)));
    barra.style.width = `${Math.round(progreso * 100)}%`;

    if (!resultado.coincide) {
      racha = { id: null, cantidad: 0 };
      mostrarEstado(progreso > 0.75 ? 'Casi… acercá un poco la foto' : 'Colocá la foto del autor dentro del marco verde');
      return;
    }

    racha = racha.id === resultado.autorId
      ? { id: resultado.autorId, cantidad: racha.cantidad + 1 }
      : { id: resultado.autorId, cantidad: 1 };
    const autor = buscarAutor(resultado.autorId);
    if (autor && racha.cantidad >= CUADROS_PARA_CONFIRMAR) {
      marcadorReconocido(autor);
    } else {
      mostrarEstado('Analizando… mantené la ficha quieta');
    }
  }

  function marcadorReconocido(autor) {
    pararEscaneo();
    marco.classList.add('reconocido');
    barra.style.width = '100%';
    mostrarEstado(`Marcador Reconocido: ${autor.nombre}`, true);
    if (navigator.vibrate) navigator.vibrate(120);
    clearTimeout(temporizadorFicha);
    temporizadorFicha = setTimeout(() => {
      if (activo) abrirFicha(autor);
    }, ESPERA_FICHA_MS);
  }

  function abrirFicha(autor) {
    fichaCuerpo.innerHTML = detalleAutor(autor);
    ficha.hidden = false;
    ficha.querySelector('.hoja').scrollTop = 0;
    $('#btn-seguir').focus({ preventScroll: true });
  }

  function cerrarFicha() {
    clearTimeout(temporizadorFicha);
    voz.detener();
    ficha.hidden = true;
  }

  function seguirEscaneando() {
    cerrarFicha();
    if (camara.activa()) {
      empezarEscaneo();
    } else {
      encenderCamara();
    }
  }

  btnEscaneo.addEventListener('click', () => {
    cerrarFicha();
    if (camara.activa() && !detenidoPorUsuario) {
      detenidoPorUsuario = true;
      apagarCamara();
      marco.classList.remove('reconocido');
      mostrarEstado('Escaneo detenido');
      actualizarBoton();
    } else {
      encenderCamara();
    }
  });
  btnCambiar.addEventListener('click', () => {
    pararEscaneo();
    encenderCamara(true);
  });
  btnReintentar.addEventListener('click', () => encenderCamara());
  $('#btn-seguir').addEventListener('click', seguirEscaneando);
  ficha.addEventListener('click', (e) => {
    if (e.target === ficha) seguirEscaneando();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !ficha.hidden) seguirEscaneando();
  });

  // apaga la cámara al cambiar de pestaña o bloquear el celular, y la vuelve a prender al volver
  document.addEventListener('visibilitychange', () => {
    if (!activo) return;
    if (document.hidden) {
      pararEscaneo();
      camara.detener();
    } else if (!detenidoPorUsuario) {
      encenderCamara();
    }
  });

  return {
    entrar() {
      if (activo) return;
      activo = true;
      detenidoPorUsuario = false;
      cerrarFicha();
      marco.classList.remove('reconocido');
      encenderCamara();
    },
    salir() {
      if (!activo) return;
      activo = false;
      cerrarFicha();
      apagarCamara();
      mostrarAviso(null);
    }
  };
}

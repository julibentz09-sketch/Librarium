// En el celular arranca con la cámara trasera y el botón alterna con la frontal.
// En la notebook usa la webcam y, si hay más de una, el botón las recorre.

export const esMovil = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) ||
  (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent));

let flujo = null;
let dispositivos = [];
let idActual = null;
let orientacion = esMovil ? 'environment' : 'user';
let turno = 0;

export function soportada() {
  return Boolean(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
}

export function activa() {
  return Boolean(flujo) && flujo.getVideoTracks().some((pista) => pista.readyState === 'live');
}

export function detener() {
  turno++;
  if (flujo) {
    flujo.getTracks().forEach((pista) => pista.stop());
    flujo = null;
  }
}

function adivinarOrientacion(nombre) {
  if (/back|rear|trasera|environment|posterior/i.test(nombre)) return 'environment';
  if (/front|frontal|user|facetime|integrated|integrada|webcam/i.test(nombre)) return 'user';
  return esMovil ? 'environment' : 'user';
}

async function pedirFlujo({ deviceId, facingMode }) {
  const resolucion = { width: { ideal: 1280 }, height: { ideal: 720 } };
  if (deviceId) {
    return navigator.mediaDevices.getUserMedia({ audio: false, video: { ...resolucion, deviceId: { exact: deviceId } } });
  }
  try {
    const modo = esMovil ? { exact: facingMode } : { ideal: facingMode };
    return await navigator.mediaDevices.getUserMedia({ audio: false, video: { ...resolucion, facingMode: modo } });
  } catch (error) {
    if (error.name !== 'OverconstrainedError' && error.name !== 'NotFoundError') throw error;
    return navigator.mediaDevices.getUserMedia({ audio: false, video: resolucion });
  }
}

function nombreCamara(etiqueta) {
  if (esMovil) return orientacion === 'user' ? 'Cámara frontal' : 'Cámara trasera';
  const nombre = (etiqueta || '').replace(/\s*\([0-9a-f]{4}:[0-9a-f]{4}\)\s*$/i, '').trim();
  return nombre || 'Cámara de la computadora';
}

export async function iniciar(video, opciones = {}) {
  detener();
  const miTurno = turno;
  const nuevo = await pedirFlujo({ deviceId: opciones.deviceId, facingMode: opciones.facingMode || orientacion });

  // si mientras tanto se salió de la pantalla o se pidió otra cámara, se descarta
  if (miTurno !== turno) {
    nuevo.getTracks().forEach((pista) => pista.stop());
    const cancelado = new Error('Cámara cancelada');
    cancelado.name = 'CanceladoError';
    throw cancelado;
  }

  flujo = nuevo;
  video.srcObject = flujo;
  try {
    await video.play();
  } catch (e) {
    // el video está silenciado, arranca solo
  }

  const pista = flujo.getVideoTracks()[0];
  const ajustes = pista.getSettings ? pista.getSettings() : {};
  idActual = ajustes.deviceId || null;
  orientacion = ajustes.facingMode || adivinarOrientacion(pista.label);
  try {
    dispositivos = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === 'videoinput');
  } catch (e) {
    dispositivos = [];
  }

  return {
    nombre: nombreCamara(pista.label),
    espejado: orientacion === 'user',
    puedeCambiar: dispositivos.length > 1
  };
}

export function cambiar(video) {
  if (esMovil || dispositivos.length < 2) {
    return iniciar(video, { facingMode: orientacion === 'user' ? 'environment' : 'user' });
  }
  const indice = dispositivos.findIndex((d) => d.deviceId === idActual);
  const siguiente = dispositivos[(indice + 1) % dispositivos.length];
  return iniciar(video, { deviceId: siguiente.deviceId });
}

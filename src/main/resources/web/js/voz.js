// Lee la biografía en voz alta con la voz del navegador

export const disponible = 'speechSynthesis' in window;

const TEXTO_ESCUCHAR = 'Escuchar biografía';
const TEXTO_DETENER = 'Detener audio';
let botonActivo = null;

function vozEnEspanol() {
  const voces = speechSynthesis.getVoices();
  return voces.find((v) => /^es[-_](PY|AR|419|MX|US)/i.test(v.lang)) ||
    voces.find((v) => /^es/i.test(v.lang)) ||
    null;
}

function cambiarTexto(boton, texto) {
  boton.querySelector('span').textContent = texto;
}

function liberarBoton() {
  if (botonActivo) cambiarTexto(botonActivo, TEXTO_ESCUCHAR);
  botonActivo = null;
}

export function detener() {
  if (!disponible) return;
  speechSynthesis.cancel();
  liberarBoton();
}

export function alternar(autor, boton) {
  if (botonActivo === boton) {
    detener();
    return;
  }
  detener();

  const obras = autor.obras.map((o) => o.titulo).join(', ');
  const lectura = new SpeechSynthesisUtterance(`${autor.nombre}. ${autor.biografia} Sus obras principales son: ${obras}.`);
  const voz = vozEnEspanol();
  lectura.lang = voz ? voz.lang : 'es-ES';
  if (voz) lectura.voice = voz;
  lectura.rate = 0.95;
  lectura.onend = lectura.onerror = () => {
    if (botonActivo === boton) liberarBoton();
  };

  botonActivo = boton;
  cambiarTexto(boton, TEXTO_DETENER);
  speechSynthesis.speak(lectura);
}

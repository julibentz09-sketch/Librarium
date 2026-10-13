import { escapar } from './plantillas.js';

const $ = (selector) => document.querySelector(selector);

// Muestra la escultura 3D de cada autor con <model-viewer>. En el celular el botón
// "Ver en tu espacio" abre la cámara y la coloca sobre el piso (WebXR, Scene Viewer o Quick Look).
export function crearVisorRA(obtenerAutores) {
  const visor = $('#visor-ra');
  const lista = $('#ra3d-autores');
  const etiqueta = $('#ra3d-etiqueta');
  const avisoSinRA = $('#ra3d-sin-ra');
  let actual = null;

  const modeloDe = (autor) => autor.foto.replace(/^img\//, 'modelos/').replace(/\.\w+$/, '.glb');

  function dibujarLista(autores) {
    lista.innerHTML = autores.map((autor) => `
      <button type="button" class="ra3d-autor" data-autor="${escapar(autor.id)}" aria-pressed="false">
        <img src="${escapar(autor.foto)}" alt="">
        <span>${escapar(autor.nombre)}</span>
      </button>`).join('');
  }

  function elegir(autor) {
    actual = autor;
    visor.src = modeloDe(autor);
    visor.alt = `Escultura 3D de ${autor.nombre}`;
    etiqueta.textContent = autor.nombre;
    for (const boton of lista.querySelectorAll('[data-autor]')) {
      boton.setAttribute('aria-pressed', String(boton.dataset.autor === autor.id));
    }
  }

  lista.addEventListener('click', (e) => {
    const boton = e.target.closest('[data-autor]');
    const autor = boton && obtenerAutores().find((a) => a.id === boton.dataset.autor);
    if (!autor || autor === actual) return;
    elegir(autor);
    // actualiza la dirección sin volver a navegar (así no salta al principio de la página)
    history.replaceState(null, '', `#/ra/${encodeURIComponent(autor.id)}`);
  });

  visor.addEventListener('load', () => {
    avisoSinRA.hidden = visor.canActivateAR;
  });

  return {
    mostrar(id) {
      const autores = obtenerAutores();
      if (!autores.length) return;
      if (!lista.children.length) dibujarLista(autores);
      elegir(autores.find((a) => a.id === id) || actual || autores[0]);
    }
  };
}

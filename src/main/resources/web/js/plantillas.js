import * as voz from './voz.js';

const ICONO_PARLANTE = `
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/>
  </svg>`;

export function escapar(texto) {
  const reemplazos = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  return String(texto ?? '').replace(/[&<>"']/g, (c) => reemplazos[c]);
}

export function tarjetaAutor(autor) {
  return `
    <article class="tarjeta">
      <img src="${escapar(autor.foto)}" alt="Foto de ${escapar(autor.nombre)}" loading="lazy">
      <div class="tarjeta-cuerpo">
        <h3>${escapar(autor.nombre)}</h3>
        <p class="etiqueta">${escapar(autor.destacado)}</p>
        <a href="#/autor/${encodeURIComponent(autor.id)}" class="boton boton-primario boton-chico">Ver Información</a>
      </div>
    </article>`;
}

function itemObra(obra) {
  const datos = [obra.genero, obra.anio].filter(Boolean).join(', ');
  return `
    <li>
      <span class="obra-titulo">${escapar(obra.titulo)}</span>
      ${datos ? `<span class="obra-meta">(${escapar(datos)})</span>` : ''}
      ${obra.descripcion ? `<p class="obra-desc">${escapar(obra.descripcion)}</p>` : ''}
    </li>`;
}

export function detalleAutor(autor) {
  const botonAudio = voz.disponible
    ? `<button type="button" class="boton boton-sutil boton-chico" data-escuchar="${escapar(autor.id)}">${ICONO_PARLANTE}<span>Escuchar biografía</span></button>`
    : '';

  return `
    <article class="detalle">
      <div class="detalle-lado">
        <img class="detalle-foto" src="${escapar(autor.foto)}" alt="Foto de ${escapar(autor.nombre)}">
        <h2>${escapar(autor.nombre)}</h2>
        <dl class="detalle-datos">
          <dt>Nacimiento</dt>
          <dd>${escapar(autor.fechaNacimiento)}<br>${escapar(autor.lugarNacimiento)}</dd>
          ${autor.fallecimiento ? `<dt>Fallecimiento</dt><dd>${escapar(autor.fallecimiento)}</dd>` : ''}
        </dl>
        ${autor.destacado ? `<span class="insignia">${escapar(autor.destacado)}</span>` : ''}
      </div>
      <div class="detalle-info">
        <section>
          <h3>Biografía</h3>
          <p class="biografia">${escapar(autor.biografia)}</p>
          ${botonAudio}
        </section>
        <section>
          <h3>Obras principales</h3>
          <ul class="obras">${autor.obras.map(itemObra).join('')}</ul>
        </section>
      </div>
    </article>`;
}

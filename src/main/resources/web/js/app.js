import * as api from './api.js';
import { esMovil } from './camara.js';
import * as voz from './voz.js';
import { detalleAutor, tarjetaAutor } from './plantillas.js';
import { crearEscaner } from './escaner.js';

const $ = (selector) => document.querySelector(selector);

let autores = [];
let infoServidor = { urlsCelular: [], https: false, puertoHttps: 8443 };

const buscarAutor = (id) => autores.find((a) => a.id === id);
const escaner = crearEscaner({ buscarAutor, infoServidor: () => infoServidor });

const vistas = {
  inicio: $('#vista-inicio'),
  autor: $('#vista-autor'),
  ra: $('#vista-ra'),
  salir: $('#vista-salir')
};

function mostrarVista(nombre, titulo = 'Librarium') {
  for (const [clave, vista] of Object.entries(vistas)) {
    vista.hidden = clave !== nombre;
  }
  document.body.classList.toggle('modo-ra', nombre === 'ra');
  document.title = titulo;
  window.scrollTo(0, 0);
}

function navegar() {
  const [, seccion = '', parametro = ''] = (location.hash || '#/').split('/');
  voz.detener();
  if (seccion !== 'ra') escaner.salir();

  const autor = seccion === 'autor' ? buscarAutor(decodeURIComponent(parametro)) : null;
  if (autor) {
    $('#detalle-autor').innerHTML = detalleAutor(autor);
    mostrarVista('autor', `${autor.nombre} · Librarium`);
  } else if (seccion === 'ra') {
    mostrarVista('ra', 'Cámara RA · Librarium');
    escaner.entrar();
  } else if (seccion === 'salir') {
    mostrarVista('salir');
  } else {
    mostrarVista('inicio');
  }
}

function direccionParaCelular() {
  // publicada con HTTPS (por ejemplo en Codespaces) sirve la misma dirección
  if (location.protocol === 'https:') return `${location.origin}/`;
  return infoServidor.urlsCelular[0];
}

function mostrarInfoCelular() {
  const url = direccionParaCelular();
  if (!url || esMovil) return;
  const enlace = $('#url-celular');
  enlace.href = url;
  enlace.textContent = url;
  if (window.QRCode) {
    new QRCode($('#qr'), { text: url, width: 132, height: 132, correctLevel: QRCode.CorrectLevel.M });
  }
  $('#info-celular').hidden = false;
}

function prepararSalida() {
  const modal = $('#modal-salir');
  $('#btn-salir').addEventListener('click', () => {
    modal.hidden = false;
    $('#btn-cancelar-salir').focus();
  });
  $('#btn-cancelar-salir').addEventListener('click', () => {
    modal.hidden = true;
  });
  modal.addEventListener('click', (e) => {
    if (e.target === modal) modal.hidden = true;
  });
  $('#btn-confirmar-salir').addEventListener('click', () => {
    modal.hidden = true;
    window.close(); // solo funciona si la pestaña la abrió un script
    setTimeout(() => {
      location.hash = '#/salir';
    }, 150);
  });
}

document.addEventListener('click', (e) => {
  const boton = e.target.closest('[data-escuchar]');
  if (boton) voz.alternar(buscarAutor(boton.dataset.escuchar), boton);
});

async function iniciar() {
  prepararSalida();
  try {
    autores = await api.obtenerAutores();
    $('#tarjetas').innerHTML = autores.map(tarjetaAutor).join('');
  } catch (error) {
    $('#tarjetas').innerHTML = '<p class="error-carga">No se pudieron cargar los autores. Revisá que el servidor esté encendido.</p>';
  }
  try {
    infoServidor = await api.obtenerInfo();
  } catch (error) {
    // se usan los valores por defecto
  }

  window.addEventListener('hashchange', navegar);
  navegar();
  if (document.readyState === 'complete') {
    mostrarInfoCelular();
  } else {
    window.addEventListener('load', mostrarInfoCelular);
  }
}

iniciar();

import * as reconocedorLocal from './reconocedor.js';

// Con el servidor Java los datos vienen de /api. En GitHub Pages no hay servidor:
// se usan los JSON que genera ExportadorSitio y el reconocimiento se hace en el navegador.
let sinServidor = false;
let referenciasListas = null;

async function pedirJson(url, opciones) {
  const respuesta = await fetch(url, opciones);
  if (!respuesta.ok) {
    throw new Error(`HTTP ${respuesta.status}`);
  }
  return respuesta.json();
}

export async function obtenerAutores() {
  try {
    return await pedirJson('api/autores');
  } catch (error) {
    sinServidor = true;
    return pedirJson('datos/autores.json');
  }
}

export async function obtenerInfo() {
  if (sinServidor) {
    return { urlsCelular: [], https: location.protocol === 'https:', puertoHttps: 443 };
  }
  return pedirJson('api/info');
}

export async function reconocer(lienzo) {
  if (sinServidor) {
    if (!referenciasListas) {
      referenciasListas = pedirJson('datos/marcadores.json')
        .then(reconocedorLocal.cargarReferencias)
        .catch((error) => {
          referenciasListas = null;
          throw error;
        });
    }
    await referenciasListas;
    return reconocedorLocal.analizar(lienzo);
  }
  const imagen = await new Promise((listo) => lienzo.toBlob(listo, 'image/jpeg', 0.85));
  return pedirJson('api/reconocer', {
    method: 'POST',
    headers: { 'Content-Type': 'image/jpeg' },
    body: imagen
  });
}

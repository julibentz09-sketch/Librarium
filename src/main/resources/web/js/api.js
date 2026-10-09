async function pedirJson(url, opciones) {
  const respuesta = await fetch(url, opciones);
  if (!respuesta.ok) {
    throw new Error(`HTTP ${respuesta.status}`);
  }
  return respuesta.json();
}

export function obtenerAutores() {
  return pedirJson('api/autores');
}

export function obtenerInfo() {
  return pedirJson('api/info');
}

export function reconocer(imagen) {
  return pedirJson('api/reconocer', {
    method: 'POST',
    headers: { 'Content-Type': 'image/jpeg' },
    body: imagen
  });
}

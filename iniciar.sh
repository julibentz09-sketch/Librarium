#!/bin/sh
# Inicia Librarium en macOS o Linux:  ./iniciar.sh
cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
  echo "No se encontró Java. Instala Java 17 o superior desde https://adoptium.net"
  exit 1
fi

if [ ! -f target/librarium.jar ]; then
  echo "Compilando Librarium por primera vez, espera un momento..."
  sh ./mvnw -q -B package || { echo "Hubo un error al compilar."; exit 1; }
fi

exec java -jar target/librarium.jar "$@"

@echo off
rem Inicia Librarium en Windows (doble clic sobre este archivo).
chcp 65001 >nul
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo No se encontro Java. Instala Java 17 o superior desde https://adoptium.net
  pause
  exit /b 1
)

if not exist "target\librarium.jar" (
  echo Compilando Librarium por primera vez, espera un momento...
  call mvnw.cmd -q -B package
  if errorlevel 1 (
    echo Hubo un error al compilar.
    pause
    exit /b 1
  )
)

java -jar "target\librarium.jar" %*
pause

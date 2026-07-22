# RutaPro

App Android que **lee la carrera que aparece en pantalla** (Uber) y te dice al instante
si **conviene o no**, con una burbuja flotante de color:

- 🟢 **CONVIENE** — buen pago por km y por minuto
- 🟡 **REGULAR** — justo en el límite
- 🔴 **NO CONVIENE** — paga poco o la recogida es muy lejos

Calcula: ganancia neta (descontando gasolina), pesos por km, pesos por minuto y penaliza
recogidas lejanas.

## Cómo funciona (por dentro)

1. Un **servicio de accesibilidad** lee el texto de la tarjeta de oferta de Uber.
2. `UberParser` extrae tarifa, distancia/tiempo de recogida y de viaje.
3. `RideAnalyzer` aplica tu fórmula de rentabilidad.
4. El **overlay flotante** muestra el resultado encima de Uber.

> Todo el procesamiento es **local en el teléfono**. Nada se envía a internet.

## Compilar el APK en la nube (sin instalar nada)

1. Sube este proyecto a un repositorio de GitHub.
2. Cada `push` a la rama `main` dispara el workflow `.github/workflows/build.yml`.
3. Cuando termine, entra a la pestaña **Actions** → el último run → sección **Artifacts**
   → descarga **RutaPro-debug-apk**.
4. Descomprime y pasa el `app-debug.apk` al teléfono para instalarlo
   (activa "instalar apps de origen desconocido").

También puedes lanzar la compilación manualmente en **Actions → Build APK → Run workflow**.

## Uso en el teléfono

1. Abre RutaPro y ajusta tus parámetros (pago mínimo por km/minuto, precio de gasolina,
   rendimiento km/galón, recogida máxima). Toca **Guardar**.
2. Toca **1. Permitir burbuja** y concede el permiso de superposición.
3. Toca **2. Activar accesibilidad** y activa "RutaPro" en la lista.
4. Abre Uber. Cuando entre una carrera, la burbuja se pinta sola.

## Ajustar el parser a tu ciudad

El texto exacto que muestra Uber cambia según país/idioma/versión. Si la burbuja no
detecta bien los datos, hay que afinar las expresiones de `parser/UberParser.kt`.
Para eso conviene capturar el texto crudo real: los datos llegan en `RideOffer.rawText`.

## Aviso

Leer la pantalla de otra app con accesibilidad puede ir contra los Términos de Servicio
de Uber. Úsalo bajo tu propia responsabilidad, para uso personal.

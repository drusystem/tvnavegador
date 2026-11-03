# Launcher

Inicio del stick. Módulo Gradle propio en `launcher/` (su `settings.gradle` no es el de la raíz). Paquete `com.tvdonghua.launcher`. APK de depuración: `launcher\build\outputs\apk\debug\Inicio-debug.apk`. Versión actual de referencia: 2.3 (`versionCode` 14).

No lee el catálogo de ViewAnime ni abre sus actividades por dentro. Las apps se lanzan como actividades `MAIN` del sistema, ViewAnime incluida.

## Qué tiene que cumplir

1. Ser la actividad HOME. Al encender o pulsar Inicio se ve el fondo animado, la hora del stick, favoritos y el resto de aplicaciones.
2. El foco del mando se nota en la tarjeta. El nombre de la app solo se muestra en la tarjeta enfocada.
3. Un OK corto abre la app. Dejar OK unos 500 ms abre el menú de esa tarjeta. El foco del menú no se mueve hasta soltar, y la primera selección se ignora unos 250 ms para no activar una opción con el mismo pulsado.
4. El menú ofrece mover a la izquierda o a la derecha cuando la posición lo permite, Quitar, Detener y Cancelar. Detener no se ofrece para el propio launcher. Detener llama a `killBackgroundProcesses` y, en otro hilo y sin esperar, a `su 0 am force-stop` del paquete.
5. Cada fila tiene un `+` que lista las apps que todavía no están en esa fila. Elegir una la coloca al final.
6. El engranaje y la hora abren ajustes en Hora y fecha. El carril izquierdo cambia de página al recibir foco: Hora y fecha, Red, Bluetooth, Pantalla, Sonido, Aplicaciones, Sistema, Apariencia.
7. Apariencia persiste color y tamaño de letras, fuente, tamaño de tarjetas e iconos, color y opacidad de tarjetas, opacidad al enfocar, color y opacidad de los modales, **fondo animado** (Brisa / Danza / Levitar) y **visibilidad del reproductor del inicio**. OK edita la fila enfocada, izquierda y derecha cambian el valor, Atrás suelta. Hay vuelta a los valores por defecto.
8. El salvapantallas `ForestDream` muestra el mismo fondo animado elegido y la hora. No es interactivo.
9. `SettingsActivity` no está exportada. No se abre con `am start` desde el shell.

## Fondo animado

El JPG estático `bg_home` sigue siendo la imagen base. Encima, `LivingBg` reproduce en bucle silencioso uno de tres mp4 en `launcher/src/main/res/raw/`:

| Clave | Nombre en Apariencia | Archivo | Idea |
|---|---|---|---|
| `breeze` | Brisa | `bg_home_breeze.mp4` | Pétalos, pelo, río y brillo del báculo; Frieren en su sitio |
| `dance` | Danza | `bg_home_dance.mp4` | Baile con el báculo a la misma distancia de cámara |
| `levitate` | Levitar | `bg_home_levitate.mp4` | Flota en su sitio (sin acercarse), baile armonioso con pétalos y haz hacia la esquina superior derecha |

La cámara no hace zoom en Levitar: se aprecia desde la distancia del fondo original. El loop es infinito (`isLooping` y reinicio manual por si el H313 lo ignora). Al pausar la actividad se pausa el video; al volver se reanuda.

Con **Levitar**, `ChromeSparks` hace pulsar la tuerca y la hora y lanza destellos cuando el haz del corto apunta a esa esquina (cada ~15 s, a mitad del loop).

## Reproductor del inicio (clip)

Abajo a la derecha hay un panel glass (`ClipPane`) con el video recortado a esquinas redondeadas (`RoundHost`).

- Por defecto usa el OP de Frieren empaquetado en `launcher/src/main/assets/clip/default.mp4`, copiado a `filesDir/clip/current.mp4` la primera vez.
- Se reproduce en bucle local (MediaPlayer; ExoPlayer solo si el reemplazo trae video y audio por separado).
- OK en foco: pausa / play. Si el clip está oculto en Apariencia, no recibe foco ni se dibuja.
- En Apariencia → **Video del inicio** se pega un enlace de YouTube. Al guardar, `ClipStore` descarga el nuevo archivo, borra el anterior y lo deja listo. NewPipe Extractor resuelve el stream; hace falta `INTERNET`.
- Dependencias del módulo: Media3 ExoPlayer y NewPipe Extractor (JitPack). `compileSdk` 35.

## Qué no se hace

- No se cambian las preferencias de Projectivy.
- No se mueve lógica de reproducción, catálogo o Gecko a este módulo.
- No se trata el cursor del mando como un toque que reemplace al foco: el OK corto y el OK largo siguen siendo teclas.
- No se embeben videos de YouTube en vivo en el WebView del sistema para el clip del inicio: en este stick falla; el camino es archivo local.

## Verificación

En el stick, a 1280×720: inicio con cada fondo (Brisa, Danza, Levitar), destellos en tuerca/hora solo con Levitar, clip visible/oculto, play/pause del clip, menú de una app que no sea el launcher (tiene que verse Detener), los dos selectores `+`, y cada página de ajustes. Referencia en `docs/capturas/launcher/` y los mp4 de `launcher/src/main/res/raw/` en el README.

## Pendiente

No hay una captura del salvapantallas: solo aparece cuando el sistema lanza el Dream.

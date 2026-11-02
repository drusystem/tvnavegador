# Launcher

Inicio del stick. Módulo Gradle propio en `launcher/` (su `settings.gradle` no es el de la raíz). Paquete `com.tvdonghua.launcher`. APK de depuración: `launcher\build\outputs\apk\debug\Inicio-debug.apk`.

No lee el catálogo de ViewAnime ni abre sus actividades por dentro. Las apps se lanzan como actividades `MAIN` del sistema, ViewAnime incluida.

## Qué tiene que cumplir

1. Ser la actividad HOME. Al encender o pulsar Inicio se ve el fondo, la hora del stick, favoritos y el resto de aplicaciones.
2. El foco del mando se nota en la tarjeta. El nombre de la app solo se muestra en la tarjeta enfocada.
3. Un OK corto abre la app. Dejar OK unos 500 ms abre el menú de esa tarjeta. El foco del menú no se mueve hasta soltar, y la primera selección se ignora unos 250 ms para no activar una opción con el mismo pulsado.
4. El menú ofrece mover a la izquierda o a la derecha cuando la posición lo permite, Quitar, Detener y Cancelar. Detener no se ofrece para el propio launcher. Detener llama a `killBackgroundProcesses` y, en otro hilo y sin esperar, a `su 0 am force-stop` del paquete.
5. Cada fila tiene un `+` que lista las apps que todavía no están en esa fila. Elegir una la coloca al final.
6. El engranaje y la hora abren ajustes en Hora y fecha. El carril izquierdo cambia de página al recibir foco: Hora y fecha, Red, Bluetooth, Pantalla, Sonido, Aplicaciones, Sistema, Apariencia.
7. Apariencia persiste color y tamaño de letras, fuente, tamaño de tarjetas e iconos, color y opacidad de tarjetas, opacidad al enfocar, y color y opacidad de los modales. OK edita la fila enfocada, izquierda y derecha cambian el valor, Atrás suelta. Hay vuelta a los valores por defecto.
8. El salvapantallas `ForestDream` muestra el fondo y la hora. No es interactivo.
9. `SettingsActivity` no está exportada. No se abre con `am start` desde el shell.

## Qué no se hace

- No se cambian las preferencias de Projectivy.
- No se mueve lógica de reproducción, catálogo o Gecko a este módulo.
- No se trata el cursor del mando como un toque que reemplace al foco: el OK corto y el OK largo siguen siendo teclas.

## Verificación

En el stick, a 1280×720: inicio, menú de una app que no sea el launcher (tiene que verse Detener), los dos selectores `+`, y cada página de ajustes. Referencia en `docs/capturas/launcher/`.

## Pendiente

No hay una captura del salvapantallas: solo aparece cuando el sistema lanza el Dream.

# TV Navegador

Repositorio del stick H96 Max (Allwinner H313, solo `armeabi-v7a`, 1280×720, unos 2 GB de RAM). El WebView y Chrome de fábrica no se pueden actualizar, y el control es un mando Bluetooth con cursor y OK.

Hay tres módulos. No se pisan entre sí:

| Módulo | Paquete | Qué es |
|---|---|---|
| `launcher` | `com.tvdonghua.launcher` | Inicio del stick: favoritos, aplicaciones y ajustes |
| `viewanime` | `com.tvdonghua.viewanime` | Catálogo y reproductor de SeriesDonghua y JKAnime |
| `app` | `com.tvdonghua.browser` | NavAnime, el navegador anterior con GeckoView. Se dejó como estaba |

El contexto para seguir mejorando cada uno, sin mezclarlos, está en [`sdd/`](sdd/README.md).

## Launcher

Inicio con el fondo del bosque, la hora del stick y dos filas: favoritos arriba y el resto de aplicaciones abajo. El engranaje y la hora abren los ajustes.

![Inicio del launcher](docs/capturas/launcher/inicio.png)

Dejar pulsado OK sobre una app abre su menú: moverla, quitarla, detenerla o cancelar. Detener cierra el proceso de esa app (también con `su`, si el stick lo tiene) y no aparece sobre el propio launcher.

![Menú al dejar pulsado OK](docs/capturas/launcher/menu-app.png)

El botón `+` de cada fila abre el selector para sumar una app. Lo que ya está en esa fila no vuelve a ofrecerse.

![Agregar a favoritos](docs/capturas/launcher/agregar-favoritos.png)

![Agregar a aplicaciones](docs/capturas/launcher/agregar-aplicaciones.png)

Los ajustes comparten el mismo fondo y un carril a la izquierda. La página de la derecha cambia al mover el foco.

![Hora y fecha](docs/capturas/launcher/ajustes-hora.png)

![Red](docs/capturas/launcher/ajustes-red.png)

![Bluetooth](docs/capturas/launcher/ajustes-bluetooth.png)

![Pantalla](docs/capturas/launcher/ajustes-pantalla.png)

![Sonido](docs/capturas/launcher/ajustes-sonido.png)

![Aplicaciones](docs/capturas/launcher/ajustes-aplicaciones.png)

![Sistema](docs/capturas/launcher/ajustes-sistema.png)

Apariencia cambia letras, fuente, tamaño de tarjetas e iconos, y el color y la opacidad de tarjetas y modales. OK entra a editar una fila, izquierda y derecha cambian el valor, Atrás suelta.

![Apariencia](docs/capturas/launcher/ajustes-apariencia.png)

También hay un salvapantallas (`ForestDream`) con el mismo fondo y la hora. No tiene captura aquí porque solo aparece cuando el sistema lo lanza.

## ViewAnime

Catálogo oscuro, pensado para verse a dos o tres metros en una tele de 50 pulgadas o más. El stick sigue dibujando 1280×720, así que el foco es un borde cian grueso y el nombre de la ficha enfocada se lee grande encima de las carátulas.

SeriesDonghua abre en Últimos. JKAnime abre en Programación, con las pestañas Animes, Donghuas y Ovas. La pestaña Hentai de JKAnime no se muestra.

![Últimos de SeriesDonghua](docs/capturas/viewanime/ultimos.png)

![Programación de JKAnime](docs/capturas/viewanime/jkanime.png)

Cada sitio tiene su directorio, con un campo para buscar por nombre.

![Directorio de SeriesDonghua](docs/capturas/viewanime/directorio.png)

![Búsqueda en SeriesDonghua](docs/capturas/viewanime/buscar.png)

![Directorio de JKAnime](docs/capturas/viewanime/jkanime-directorio.png)

![Búsqueda en JKAnime](docs/capturas/viewanime/jkanime-buscar.png)

Al abrir un capítulo, SeriesDonghua elige OK.ru y el video arranca en pantalla completa. Atrás vuelve a la barra de servidores (Dailymotion, OK.ru, Rumble y VOE siguen disponibles). Un OK pausa o sigue. Dos veces OK sale o entra de la pantalla completa.

![Capítulo con los servidores](docs/capturas/viewanime/capitulo.png)

![Reproducción a pantalla completa](docs/capturas/viewanime/reproduccion.png)

Navegador abre cualquier dirección con el mismo arreglo de anuncios del primer clic. Agregar lista guarda una fuente propia: la página de capítulos y un capítulo de ejemplo. Si el sitio es SeriesDonghua o JKAnime, se usa su lectura.

![Navegador](docs/capturas/viewanime/navegador.png)

![Agregar lista](docs/capturas/viewanime/agregar-lista.png)

El icono es un kitsune dentro de una ventana de navegador, con un mundo al lado. Está en `viewanime/src/main/res/drawable-nodpi/ic_launcher.png`.

## Cómo se llegó a este alcance

El navegador del stick no se puede actualizar, así que NavAnime metió GeckoView dentro de la APK. En este Allwinner el decodificador de hardware de Gecko deja el cuadro en negro (`media.hardware-video-decoding.enabled` se apaga al arrancar, y el proceso RDD también). El video se pinta por software y, al componer la página, se nota en micropausas. Una app como Xuper se ve fluida porque dibuja con MediaCodec sobre la capa de hardware del chip (`omx_vdec` / `omx_vdec_aw`), un camino que Gecko no puede usar aquí.

ViewAnime no sustituye a NavAnime. Lee el catálogo en nativo y, al reproducir, busca un archivo directo (`.m3u8` o `.mp4`). Si lo encuentra, lo pasa a ExoPlayer con tope 1280×720 y preferencia por H.264. Si no hay archivo, o el reproductor falla, abre la página en Gecko con el mismo perfil de software.

En JKAnime ese archivo directo existe y el chip lo decodifica con audio. En SeriesDonghua el servidor por defecto de la web es Dailymotion. Su playlist responde 200 desde un PC y 403 vacío desde el `HttpURLConnection` del stick, con y sin cookies. Reenviar la misma URL desde Gecko no cambia el 403: el CDN rechaza al cliente HTTP del aparato. Por eso Dailymotion no se intenta en ExoPlayer y se abre su reproductor de página.

De los otros servidores del mismo capítulo, probados en el stick:

- **OK.ru** entrega un HLS normal con H.264 y AAC. ExoPlayer lo reproduce con `omx_vdec` y el decodificador AAC. Es el servidor que queda elegido al abrir un capítulo, y la reproducción entra sola en pantalla completa.
- **Rumble** escribe la dirección con barras escapadas (`https:\/\/…`) y además parte el video en un `.tar` por rangos, con el audio en un AAC aparte. La app abre el reproductor de la página.
- **VOE** solo hace un salto por JavaScript y esconde el archivo. También abre el reproductor de la página, con su publicidad.

Al ir al inicio del stick, ViewAnime pausa el audio y suelta al reproductor. En el launcher, Detener fuerza el cierre de la app que tenga el foco del menú.

Para la tele grande se subió el texto, el borde del foco y el título de la ficha seleccionada. Esos cambios solo pintan la interfaz: no piden más datos ni tocan el decodificador.

## Compilar e instalar

Hace falta el SDK de Android y el Gradle del proyecto. En este equipo: `ANDROID_HOME` en `%LOCALAPPDATA%\Android\Sdk` y Gradle en `%USERPROFILE%\.gradle\gradle-9.4.1\bin\gradle.bat`.

La propiedad `-PgeckoviewVersion=…` va entre comillas en PowerShell. No se fija en `gradle.properties`: ahí queda `LATEST` para el workflow. GeckoView arrastra Media3 1.11.1 aunque el módulo pida otra versión; el reproductor que corre en el stick es el 1.11.1.

```
gradle.bat "-PgeckoviewVersion=159.0.20261022150525" ":viewanime:assembleArmDebug"
```

La APK queda en `viewanime\build\outputs\apk\arm\debug\viewanime-arm-debug.apk`.

El launcher es un proyecto Gradle aparte (`launcher\`, no entra en el `settings.gradle` de la raíz):

```
gradle.bat ":assembleDebug"
```

Su APK queda en `launcher\build\outputs\apk\debug\Inicio-debug.apk`.

Instalación en el stick, con la dirección ADB que corresponda:

```
adb -s 192.168.18.229:5555 install -r viewanime-arm-debug.apk
```

`EpisodeActivity` y los ajustes del launcher no se pueden abrir con `am start` desde el shell: no están exportados. Las pruebas se hacen desde la actividad de inicio, con el mando o con `input`.

NavAnime sigue compilándose con el workflow de GitHub Actions del módulo `app`. Si una versión nueva de GeckoView cambia la API, el workflow se puede relanzar indicando una versión anterior.

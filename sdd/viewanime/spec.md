# ViewAnime

Catálogo y reproductor para el stick. Módulo `:viewanime` del Gradle de la raíz. Paquete `com.tvdonghua.viewanime`. APK: `viewanime\build\outputs\apk\arm\debug\viewanime-arm-debug.apk`, sabor `arm`, solo `armeabi-v7a`.

No sustituye a NavAnime (`app/`, `com.tvdonghua.browser`). No se copia su código para “unificarlos”.

## Qué tiene que cumplir

1. Inicio oscuro, en horizontal, usable a dos o tres metros en 1280×720. El foco de una ficha lleva borde cian de unos 5 dp y crece un poco. Encima de la fila se lee el título de la ficha enfocada, en blanco y más grande que el de la carátula. Las fichas y los chips aceptan foco también en modo táctil, porque el mando entra como puntero y si no el primer OK solo enfoca.
2. SeriesDonghua lista `https://seriesdonghua.com/episodios` (tarjetas `donghua-card`, paginación `rel=next`). JKAnime lista la Programación de animes, donghuas y ovas. La pestaña Hentai no se muestra. Los mirrors de descarga en base64 y `remote = c1.jkplayers.com` no son botones de reproducción.
3. Cada sitio tiene directorio: `https://seriesdonghua.com/todos-los-donghuas` y `https://jkanime.net/directorio`, con búsqueda. En SeriesDonghua el título de la ficha sale del `title` del enlace (`Ver {nombre} Sub Español`). En JKAnime el directorio es el JSON `var animes = …` hasta `var mode` (no se corta en el primer `;`, porque las sinopsis lo traen). La búsqueda de JKAnime es `https://jkanime.net/buscar/{texto}` y no usa ese JSON.
4. Agregar lista guarda dos URL en `viewanime_sources`. Si el host es seriesdonghua.com o jkanime.net, se usa el lector de ese sitio. Si no, se listan los enlaces de la página y el capítulo abre el iframe que encuentre.
5. Al abrir un capítulo de SeriesDonghua el servidor inicial es el que se llama OK.ru. Si ese capítulo no lo trae, queda el primero de la lista. La reproducción de ese servidor arranca sola y entra en pantalla completa en cuanto hay imagen. Atrás sale de la pantalla completa y deja la barra de servidores. Un segundo Atrás cierra el capítulo.
6. Un OK pausa o sigue (en Gecko, un toque al centro). Dos OK en menos de 420 ms alternan la pantalla completa de layout. Esa pantalla completa solo oculta la cromía de la app. No se llama a `Engine.fullscreen()` para agrandar: en este stick eso dejaba el video trabado y sin mando. El cursor del mando pasa a la página.
7. Si el embed tiene un `.m3u8` o `.mp4` usable, se reproduce con ExoPlayer (en el APK queda Media3 1.11.1, el que arrastra GeckoView). Tope 1280×720, preferencia H.264, Referer y cookies del hallazgo. Si no hay archivo o el reproductor falla, se abre la página en Gecko con decodificador por software. Dailymotion se salta y va directo a la página: su CDN responde 403 al HTTP del stick.
8. Al salir al inicio del stick (`onUserLeaveHint` y `onStop`) se pausa ExoPlayer, Gecko pasa a inactivo y se quita `KEEP_SCREEN_ON`. Al volver, Gecko se reactiva.
9. El navegador interno bloquea el primer clic de publicidad igual que NavAnime: en fase de captura, `location.assign` del mismo sitio, y se bloquean los iframes de AdAngle, aclib y agl003. `window.open` no se parchea para devolver null.
10. El icono de la app es el kitsune en una ventana de navegador con un mundo, sin cursor, en `res/drawable-nodpi/ic_launcher.png`.

## Reproducción, por servidor

Verificado en el stick con un capítulo de SeriesDonghua:

| Servidor | Qué hace ViewAnime |
|---|---|
| OK.ru | HLS H.264 + AAC. Lo decodifica el chip (`omx_vdec` y AAC). Es el camino fluido, el mismo tipo que JKAnime |
| Dailymotion | 403 en el HTTP del stick. Se abre el reproductor de la página |
| Rumble | La página trae el `.m3u8` con barras escapadas y el video va en un `.tar` por rangos, con el audio aparte. Se abre el reproductor de la página |
| VOE | La primera respuesta solo redirige por JavaScript y el archivo va ofuscado. Se abre el reproductor de la página |

JKAnime sigue por el archivo directo que publica su player (`video[i]` / iframe `jkplayer`). El foco inicial del player de JKAnime es `video[1]` cuando existe.

## Qué no se hace

- No se reactiva el decodificador de hardware de Gecko ni `gfx.webrender.software`.
- No se vuelve a crear un `DataSource` propio para Dailymotion: el 403 se reprodujo también con `HttpURLConnection` pelado. El fallo no son las cabeceras de ExoPlayer.
- No se adivina una tecla del mando para la pantalla completa nativa. `KEYCODE_F3` no la activó. La pantalla completa es de layout.
- `EpisodeActivity`, `BrowserActivity` y `SourceActivity` no están exportadas.

## Verificación

En el stick: Últimos con el título grande y el borde cian, directorio y una búsqueda, un capítulo que pase a «Reproduciendo» y llene la tele, Atrás para ver los servidores con OK.ru marcado, y el audio que se corta al pulsar Inicio. Referencia en `docs/capturas/viewanime/`.

## Pendiente

Abrir una ficha del directorio de SeriesDonghua (probado con Renegade Immortal) mostró «Esta lista no trajo capítulos». El lector exige enlaces con `episodio` dentro de `donghua-card`. Últimos sí abre el capítulo. No se documenta el listado de episodios de una serie como hecho hasta que una ficha real lo muestre en el stick.

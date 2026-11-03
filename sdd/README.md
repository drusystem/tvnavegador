# SDD de este repositorio

Esta carpeta es el contrato para cambiar el software con especificación primero. Antes de tocar código, se lee la especificación del proyecto que se va a cambiar y se contrasta el cambio con ella. Si el cambio no cabe en la especificación, se actualiza la especificación en el mismo trabajo.

Hay dos productos vivos en el mismo repo. Sus contextos no se mezclan:

| Si el cambio es de… | Se lee | No se usa como guía de UI ni de reproducción |
|---|---|---|
| Inicio del stick, favoritos, ajustes, fondos animados, clip local, salvapantallas | [`launcher/spec.md`](launcher/spec.md) | `viewanime/` y `app/` |
| Catálogo, capítulo, reproductor, navegador de ViewAnime | [`viewanime/spec.md`](viewanime/spec.md) | `launcher/` y `app/` |
| NavAnime (`app/`, `com.tvdonghua.browser`) | No hay spec aquí | No se modifica salvo pedido explícito |

Los dos productos corren en el mismo aparato. Las restricciones del hardware están una sola vez en [`dispositivo.md`](dispositivo.md). Cada spec las da por conocidas y no las redefine.

Reglas de trabajo:

- Un cambio de launcher no altera ViewAnime, y al revés. No comparten actividades, temas ni preferencias.
- No se editan las preferencias de Projectivy.
- No se hace commit ni push si el pedido no lo dice.
- La prueba de una pantalla es en el stick (1280×720), no solo la compilación. `adb shell "su 0 …"` es la forma de root que funciona; `su -c` se rompe por las comillas de Windows. Las capturas salen con `cmd /c "adb exec-out screencap -p > archivo.png"` porque PowerShell estropea el binario.
- Las capturas de referencia viven en `docs/capturas/launcher` y `docs/capturas/viewanime`.

# TV Navegador (GeckoView)

Navegador para TV box Android con **motor Firefox (GeckoView) incluido en la APK**, asi no depende del WebView del sistema. Pensado para seriesdonghua.com y jkanime.net.

## Compilar sin instalar nada (GitHub Actions)

1. Crea un repositorio nuevo en GitHub y sube **todo el contenido de esta carpeta** (incluida `.github`).
2. Ve a la pestana **Actions** -> **Compilar APK** -> **Run workflow** (tambien corre solo al hacer push).
3. Al terminar, baja el artefacto **TVNavegador-APK**. Trae `app-arm-debug.apk` (32 bits, armeabi-v7a), que es la del stick.
4. Copia la APK a la TV box (USB o app tipo "Send Files to TV") e instalala.

## Uso con el control remoto

| Tecla | Accion |
|---|---|
| Cursor del control + OK | Mover el puntero y hacer clic en la pagina |
| Flechas y OK en el inicio | Elegir el cuadro SeriesDonghua, JKAnime o Navegador |
| Atras | Pagina anterior (o inicio si no hay mas) |
| Atras mantenido ~1 s | Pantalla de inicio |
| Menu / Info / Guia | Abrir o cerrar el inicio |
| Canal +/- o Page Up/Down | Desplazar pagina |

## Incluye

- Pantalla de inicio con el fondo a pantalla completa y tres tarjetas abajo: SeriesDonghua, JKAnime y navegador libre. La hora es la del stick. El foco solo aclara la tarjeta.
- User-agent y viewport de escritorio, para que el reproductor de la pagina cargue.
- El video no usa el decodificador de hardware: en este chip deja el cuadro en negro. El ajuste se aplica al arrancar Gecko.
- Bloqueo de pop-ups y de esquemas raros (intent://, market://).
- El primer clic en una tarjeta de capitulo abre el capitulo. El enlace de publicidad que esas paginas ponen encima no se sigue.
- En el stick, las portadas se decodifican de una en una. No se cargan las fuentes web, la analítica ni los avisos que frenan la página.
- Sin uBlock Origin: en este stick su filtro congelaba la pagina mas de un minuto.

## Notas

- La version de GeckoView se toma automaticamente de la ultima estable. Si una version nueva cambiara su API y fallara la compilacion, ejecuta el workflow a mano indicando una version anterior.
- Es APK "debug" (firmada automaticamente), valida para instalar directamente.
- Si un video no se reproduce aun con este navegador, el problema probablemente sea el codec/decodificador de hardware de la TV box.

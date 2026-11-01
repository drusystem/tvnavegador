# TV Navegador (GeckoView)

Navegador para TV box Android con **motor Firefox (GeckoView) incluido en la APK**, asi no depende del WebView del sistema. Pensado para seriesdonghua.com y jkanime.net.

## Compilar sin instalar nada (GitHub Actions)

1. Crea un repositorio nuevo en GitHub y sube **todo el contenido de esta carpeta** (incluida `.github`).
2. Ve a la pestana **Actions** -> **Compilar APK** -> **Run workflow** (tambien corre solo al hacer push).
3. Al terminar (~5-10 min), baja el artefacto **TVNavegador-APK**. Trae dos APK:
   - `app-arm-debug.apk` -> TV box de **32 bits** (la mas comun, armeabi-v7a)
   - `app-aarch64-debug.apk` -> TV box de **64 bits** (arm64-v8a)
4. Copia la APK a la TV box (USB o app tipo "Send Files to TV") e instalala.

Si dudas cual es: prueba primero la de 32 bits; funciona tambien en muchas de 64 bits.

## Uso con el control remoto

| Tecla | Accion |
|---|---|
| Flechas | Mover el cursor (acelera si mantienes) |
| Flecha contra el borde de arriba/abajo | Desplaza la pagina |
| OK / Enter | Clic en el cursor |
| Atras | Pagina anterior (o inicio si no hay mas) |
| Atras mantenido ~1 s | Pantalla de inicio |
| Menu / Info / Guia | Abrir o cerrar el inicio |
| Canal +/- o Page Up/Down | Desplazar pagina |

## Incluye

- Pantalla de inicio con botones a SeriesDonghua, JKAnime y direccion libre.
- Modo escritorio (reproductores mas grandes).
- Bloqueo de pop-ups y de esquemas raros (intent://, market://).
- uBlock Origin se instala solo la primera vez (requiere internet).

## Notas

- La version de GeckoView se toma automaticamente de la ultima estable. Si una version nueva cambiara su API y fallara la compilacion, ejecuta el workflow a mano indicando una version anterior.
- Es APK "debug" (firmada automaticamente), valida para instalar directamente.
- Si un video no se reproduce aun con este navegador, el problema probablemente sea el codec/decodificador de hardware de la TV box.

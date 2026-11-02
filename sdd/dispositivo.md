# Stick

Datos del aparato donde corren el launcher y ViewAnime. Si un hecho de aquí cambia, se actualiza este archivo y no se copia una versión distinta dentro de cada spec.

- Equipo: H96 Max H313TS. SoC Allwinner H313, cuatro Cortex-A53, unos 2 GB de RAM.
- ABI: solo `armeabi-v7a`. Las APK de 64 bits no sirven.
- Pantalla que reporta el sistema: 1280×720, densidad 213. Se ve en una tele de 50 pulgadas o más, a unos dos o tres metros. Un borde de 2 px o un texto de 13 sp no se leen desde el sofá.
- Android mezcla reportes 10 y 14. `minSdk` 26, `targetSdk` 34.
- ADB usado en el desarrollo: `192.168.18.229:5555`. `adb` está en `C:\Users\Andres\Desktop\platform-tools\adb.exe`.
- Root: `adb shell "su 0 …"`.
- Mando: Bluetooth HID, en este stick `/dev/input/event3`, vendor `0x2b54`, producto `0x1600`. El cursor es un puntero de verdad. Teclas útiles: OK `23`, atrás `4`, inicio `3`, arriba `19`, derecha `22`.
- El WebView y Chrome de fábrica no se actualizan.
- El decodificador de video de hardware del chip es el OMX de Allwinner (`omx_vdec` / `omx_vdec_aw`). ExoPlayer puede usarlo. Gecko, en este chip, no: con `media.hardware-video-decoding.enabled` el cuadro sale negro. Esa preferencia es `AtStartup`. El YAML se aplica con `GeckoRuntimeSettings.Builder.configFilePath` antes de `GeckoRuntime.create`, y `media.rdd-process.enabled` queda en false. No se vuelve a activar el decodificador de hardware de Gecko ni `gfx.webrender.software`.
- El cliente HTTP de Java del stick (`HttpURLConnection`) no es el de un navegador de escritorio. Una URL que responde 200 en el PC puede responder 403 aquí. Hay que probar la URL en el stick antes de darla por reproducible.
- En el stick no hay `curl`. Las pruebas HTTP salen por la propia app o por otro cliente del aparato.
- Gradle local: `C:\Users\Andres\.gradle\gradle-9.4.1\bin\gradle.bat`. `ANDROID_HOME`: `C:\Users\Andres\AppData\Local\Android\Sdk`. En PowerShell, `-PgeckoviewVersion=159.0.20261022150525` va entre comillas. No se deja clavada esa versión en `gradle.properties`.

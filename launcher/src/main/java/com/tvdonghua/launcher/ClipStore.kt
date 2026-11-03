package com.tvdonghua.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Clip local del launcher: un solo archivo en bucle; al cambiar el link se reemplaza. */
internal object ClipStore {
    const val PREFS = "clip"
    const val KEY_URL = "url"
    const val DEFAULT_ID = "QoGM9hCxr4k"
    const val DEFAULT_URL = "https://www.youtube.com/watch?v=$DEFAULT_ID"

    private const val ASSET = "clip/default.mp4"
    private val busy = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var extractorReady = false

    data class ClipFiles(val video: File, val audio: File?)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(context: Context): String =
        prefs(context).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun wantedId(context: Context): String =
        idOf(url(context)) ?: DEFAULT_ID

    fun saveUrl(context: Context, raw: String): Boolean {
        val id = idOf(raw) ?: return false
        prefs(context).edit().putString(KEY_URL, "https://www.youtube.com/watch?v=$id").apply()
        return true
    }

    fun idOf(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return text
        return Regex("""(?:v=|youtu\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})""")
            .find(text)?.groupValues?.getOrNull(1)
    }

    fun dir(context: Context): File = File(context.filesDir, "clip").also { it.mkdirs() }

    fun videoFile(context: Context): File = File(dir(context), "current.mp4")
    fun audioFile(context: Context): File = File(dir(context), "current.m4a")
    fun idFile(context: Context): File = File(dir(context), "current.id")

    fun readyFiles(context: Context): ClipFiles? {
        val id = wantedId(context)
        val video = videoFile(context)
        if (!video.exists() || video.length() < 64_000L) return null
        if (idFile(context).takeIf { it.exists() }?.readText() != id) return null
        val audio = audioFile(context).takeIf { it.exists() && it.length() > 1024L }
        return ClipFiles(video, audio)
    }

    fun ensure(context: Context, onReady: (ClipFiles) -> Unit, onStatus: (String) -> Unit = {}) {
        val app = context.applicationContext
        val id = wantedId(app)
        readyFiles(app)?.let {
            onReady(it)
            return
        }
        worker.execute {
            try {
                if (id == DEFAULT_ID) {
                    onStatus("Preparando video…")
                    seedDefault(app)
                    if (readyFiles(app) != null) {
                        main.post { onReady(readyFiles(app)!!) }
                        return@execute
                    }
                }
                if (!busy.compareAndSet(false, true)) {
                    main.post { onStatus("Descarga en curso…") }
                    return@execute
                }
                try {
                    main.post { onStatus("Descargando video…") }
                    download(app, id)
                    val files = readyFiles(app)
                    main.post {
                        if (files != null) onReady(files)
                        else onStatus("No se pudo descargar el video")
                    }
                } finally {
                    busy.set(false)
                }
            } catch (error: Exception) {
                busy.set(false)
                main.post {
                    onStatus(error.message?.take(80) ?: "Error al descargar")
                }
            }
        }
    }

    fun replaceAsync(context: Context, onReady: (ClipFiles) -> Unit, onStatus: (String) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            if (!busy.compareAndSet(false, true)) {
                main.post { onStatus("Descarga en curso…") }
                return@execute
            }
            try {
                val id = wantedId(app)
                main.post { onStatus("Descargando video…") }
                if (id == DEFAULT_ID) {
                    clearCurrent(app)
                    seedDefault(app)
                } else {
                    download(app, id)
                }
                val files = readyFiles(app)
                main.post {
                    if (files != null) {
                        onStatus("Video listo")
                        onReady(files)
                    } else onStatus("No se pudo descargar el video")
                }
            } catch (error: Exception) {
                main.post {
                    onStatus(error.message?.take(80) ?: "Error al descargar")
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private fun seedDefault(context: Context) {
        val dest = videoFile(context)
        val tmp = File(dir(context), "seed.tmp")
        context.assets.open(ASSET).use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        clearCurrent(context)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        idFile(context).writeText(DEFAULT_ID)
        audioFile(context).delete()
    }

    private fun clearCurrent(context: Context) {
        videoFile(context).delete()
        audioFile(context).delete()
        idFile(context).delete()
    }

    private fun download(context: Context, id: String) {
        initExtractor()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val muxed = pickMuxed(info.videoStreams)
        val tmpVideo = File(dir(context), "dl-video.tmp")
        val tmpAudio = File(dir(context), "dl-audio.tmp")
        tmpVideo.delete()
        tmpAudio.delete()
        if (muxed != null) {
            pull(needUrl(muxed.url), tmpVideo)
            clearCurrent(context)
            tmpVideo.renameTo(videoFile(context)) || run {
                tmpVideo.copyTo(videoFile(context), overwrite = true)
                tmpVideo.delete()
            }
            idFile(context).writeText(id)
            return
        }
        val videoOnly = pickVideoOnly(info.videoStreams)
            ?: throw IllegalStateException("Sin video compatible")
        val audio = info.audioStreams
            .filter { it.format != null && (it.format!!.name.contains("M4A") || it.format!!.name.contains("MPEG")) }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("Sin audio")
        pull(needUrl(videoOnly.url), tmpVideo)
        pull(needUrl(audio.url), tmpAudio)
        clearCurrent(context)
        tmpVideo.renameTo(videoFile(context)) || run {
            tmpVideo.copyTo(videoFile(context), overwrite = true)
            tmpVideo.delete()
        }
        tmpAudio.renameTo(audioFile(context)) || run {
            tmpAudio.copyTo(audioFile(context), overwrite = true)
            tmpAudio.delete()
        }
        idFile(context).writeText(id)
    }

    private fun pickMuxed(streams: List<VideoStream>): VideoStream? {
        return streams
            .filter { !it.isVideoOnly && it.format?.name?.contains("MPEG") == true }
            .sortedBy { resolutionRank(it) }
            .firstOrNull { resolutionRank(it) in 360..480 }
            ?: streams.filter { !it.isVideoOnly }.minByOrNull { kotlin.math.abs(resolutionRank(it) - 480) }
    }

    private fun pickVideoOnly(streams: List<VideoStream>): VideoStream? {
        val avc = streams.filter {
            it.isVideoOnly &&
                (it.codec?.contains("avc", true) == true || it.format?.name?.contains("MPEG") == true)
        }
        return avc.sortedBy { resolutionRank(it) }.firstOrNull { resolutionRank(it) in 360..480 }
            ?: avc.minByOrNull { kotlin.math.abs(resolutionRank(it) - 480) }
            ?: streams.filter { it.isVideoOnly }.minByOrNull { kotlin.math.abs(resolutionRank(it) - 480) }
    }

    private fun resolutionRank(stream: VideoStream): Int {
        val height = stream.height
        if (height > 0) return height
        val text = stream.resolution ?: return 0
        return Regex("""(\d{3,4})""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun needUrl(url: String?): String {
        if (url.isNullOrBlank()) throw IllegalStateException("URL de stream vacía")
        return url
    }

    private fun pull(url: String, dest: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (dest.length() < 32_000L) {
            dest.delete()
            throw IllegalStateException("Archivo incompleto")
        }
    }

    @Synchronized
    private fun initExtractor() {
        if (extractorReady) return
        NewPipe.init(HttpDownloader)
        extractorReady = true
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private object HttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                requestMethod = request.httpMethod()
                setRequestProperty("User-Agent", request.headers()["User-Agent"]?.firstOrNull() ?: USER_AGENT)
                request.headers().forEach { (key, values) ->
                    if (!key.equals("User-Agent", true)) {
                        values.forEach { setRequestProperty(key, it) }
                    }
                }
                if (this is HttpsURLConnection) {
                    // default trust
                }
                val body = request.dataToSend()
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            val code = connection.responseCode
            if (code == 429) throw ReCaptchaException("reCAPTCHA", request.url())
            val stream = try {
                if (code >= 400) connection.errorStream else connection.inputStream
            } catch (_: Exception) {
                null
            }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val headers = connection.headerFields
                ?.filterKeys { it != null }
                ?.mapKeys { it.key!! }
                ?.mapValues { it.value ?: emptyList() }
                ?: emptyMap()
            connection.disconnect()
            return Response(code, connection.responseMessage, headers, text, request.url())
        }
    }
}

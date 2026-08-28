package com.neontides.nativeapp.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

enum class ModelDownloadStatus { IDLE, DOWNLOADING, PAUSED, COMPLETED, ERROR }

data class ModelDownloadState(
    val status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val message: String = ""
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
    val percent: Int get() = (progress * 100f).toInt().coerceIn(0, 100)
}

/**
 * Download riprendibile del solo modello di riferimento di Neon Tides.
 * Il file parziale resta con estensione .part; viene rinominato .gguf soltanto
 * dopo controllo dimensione, intestazione GGUF e lunghezza HTTP completa.
 */
class ModelDownloadManager(context: Context) {
    companion object {
        const val MODEL_NAME = "Qwen2.5-3B-Instruct-Uncensored.Q4_K_M.gguf"
        const val MODEL_URL = "https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-Uncensored-GGUF/resolve/main/Qwen2.5-3B-Instruct-Uncensored.Q4_K_M.gguf"
        private const val MIN_VALID_BYTES = 1_500_000_000L
        private const val SAFETY_FREE_BYTES = 200L * 1024L * 1024L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutableState = MutableStateFlow(ModelDownloadState())
        private var job: Job? = null
        @Volatile private var deletePartialOnCancel = false
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    private val modelsDir = File(appContext.getExternalFilesDir(null), "models").apply { mkdirs() }
    private val finalFile = File(modelsDir, MODEL_NAME)
    private val partialFile = File(modelsDir, "$MODEL_NAME.part")

    val state: StateFlow<ModelDownloadState> = mutableState.asStateFlow()

    init {
        if (job?.isActive != true) {
            mutableState.value = when {
                finalFile.exists() && isValidGguf(finalFile, requireFullSize = true) -> ModelDownloadState(
                    status = ModelDownloadStatus.COMPLETED,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length(),
                    message = "Modello già disponibile"
                )
                partialFile.exists() -> ModelDownloadState(
                    status = ModelDownloadStatus.PAUSED,
                    downloadedBytes = partialFile.length(),
                    totalBytes = prefs.getLong("totalBytes", 0L),
                    message = "Download parziale pronto per essere ripreso"
                )
                else -> ModelDownloadState()
            }
        }
    }

    fun startOrResume(onCompleted: (File) -> Unit) {
        if (job?.isActive == true) return
        if (finalFile.exists() && isValidGguf(finalFile, requireFullSize = true)) {
            mutableState.value = ModelDownloadState(
                ModelDownloadStatus.COMPLETED, finalFile.length(), finalFile.length(), message = "Modello già disponibile"
            )
            onCompleted(finalFile)
            return
        }
        deletePartialOnCancel = false
        job = scope.launch {
            try {
                download(onCompleted)
            } catch (_: CancellationException) {
                if (deletePartialOnCancel) {
                    partialFile.delete()
                    prefs.edit().remove("totalBytes").apply()
                    mutableState.value = ModelDownloadState(message = "Download annullato")
                } else {
                    val previous = mutableState.value
                    mutableState.value = previous.copy(
                        status = ModelDownloadStatus.PAUSED,
                        downloadedBytes = partialFile.length(),
                        bytesPerSecond = 0L,
                        message = "Download in pausa"
                    )
                }
                throw CancellationException()
            } catch (t: Throwable) {
                mutableState.value = mutableState.value.copy(
                    status = ModelDownloadStatus.ERROR,
                    downloadedBytes = partialFile.length(),
                    bytesPerSecond = 0L,
                    message = t.message ?: "Errore durante il download"
                )
            }
        }
    }

    fun pause() {
        if (job?.isActive != true) return
        deletePartialOnCancel = false
        job?.cancel()
    }

    fun cancel() {
        deletePartialOnCancel = true
        job?.cancel()
        if (job?.isActive != true) {
            partialFile.delete()
            prefs.edit().remove("totalBytes").apply()
            mutableState.value = ModelDownloadState(message = "Download annullato")
        }
    }

    fun removePartialDownload() {
        cancel()
        partialFile.delete()
    }

    fun markModelDeleted() {
        if (job?.isActive != true && !finalFile.exists()) {
            mutableState.value = if (partialFile.exists()) {
                ModelDownloadState(
                    ModelDownloadStatus.PAUSED,
                    partialFile.length(),
                    prefs.getLong("totalBytes", 0L),
                    message = "Download parziale pronto per essere ripreso"
                )
            } else ModelDownloadState()
        }
    }

    private fun download(onCompleted: (File) -> Unit) {
        var existing = partialFile.length()
        var connection = openConnection(existing)
        if (existing > 0L && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            partialFile.delete()
            existing = 0L
            connection = openConnection(0L)
        }
        val response = connection.responseCode
        require(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) {
            "Il server ha risposto con codice $response"
        }
        val remaining = connection.contentLengthLong.coerceAtLeast(0L)
        val total = when {
            response == HttpURLConnection.HTTP_PARTIAL && remaining > 0L -> existing + remaining
            remaining > 0L -> remaining
            else -> prefs.getLong("totalBytes", 0L)
        }
        require(total <= 0L || modelsDir.usableSpace >= (total - existing).coerceAtLeast(0L) + SAFETY_FREE_BYTES) {
            "Spazio insufficiente: libera almeno 2,2 GB e riprova"
        }
        if (total > 0L) prefs.edit().putLong("totalBytes", total).apply()

        mutableState.value = ModelDownloadState(
            status = ModelDownloadStatus.DOWNLOADING,
            downloadedBytes = existing,
            totalBytes = total,
            message = if (existing > 0L) "Ripresa del download" else "Download avviato"
        )
        val startedAt = System.nanoTime()
        var lastUpdateAt = startedAt
        var lastUpdateBytes = existing
        var downloaded = existing
        try {
            BufferedInputStream(connection.inputStream, 1024 * 1024).use { input ->
                FileOutputStream(partialFile, existing > 0L).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val now = System.nanoTime()
                        if (now - lastUpdateAt >= 250_000_000L) {
                            val seconds = (now - lastUpdateAt) / 1_000_000_000.0
                            val speed = if (seconds > 0.0) ((downloaded - lastUpdateBytes) / seconds).toLong() else 0L
                            mutableState.value = ModelDownloadState(
                                ModelDownloadStatus.DOWNLOADING, downloaded, total, speed, "Download in corso"
                            )
                            lastUpdateAt = now
                            lastUpdateBytes = downloaded
                        }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        require(total <= 0L || partialFile.length() == total) { "Download incompleto: puoi riprenderlo" }
        require(isValidGguf(partialFile, requireFullSize = true)) { "Il file scaricato non è un GGUF valido" }
        if (finalFile.exists()) finalFile.delete()
        require(partialFile.renameTo(finalFile)) { "Impossibile finalizzare il modello" }
        prefs.edit().remove("totalBytes").apply()
        mutableState.value = ModelDownloadState(
            ModelDownloadStatus.COMPLETED,
            finalFile.length(),
            finalFile.length(),
            message = "Download completato e verificato"
        )
        onCompleted(finalFile)
    }

    private fun openConnection(offset: Long): HttpURLConnection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20_000
        readTimeout = 30_000
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("User-Agent", "NeonTides-Android/8.8")
        if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        connect()
    }

    private fun isValidGguf(file: File, requireFullSize: Boolean): Boolean {
        if (!file.exists() || (requireFullSize && file.length() < MIN_VALID_BYTES)) return false
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                magic.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            }
        }.getOrDefault(false)
    }
}

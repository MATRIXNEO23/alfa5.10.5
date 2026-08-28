package com.neontides.nativeapp.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

enum class LocalAiBackend(val label: String) {
    LLAMA_CPP("llama.cpp · GGUF"),
    MLC_LLM("MLC LLM · GPU")
}

data class LocalModel(
    val id: String,
    val displayName: String,
    val backend: LocalAiBackend,
    val path: File,
    val sizeBytes: Long
)

data class MlcRuntimeConfig(
    val modelId: String,
    val modelLib: String
)

class ModelManager(private val context: Context) {
    companion object {
        private const val PREF_ACTIVE = "active"
        private const val PREF_BACKEND = "active_backend"
        private const val MLC_CONFIG = "mlc-app-config.json"
        private const val MIN_MLC_BYTES = 500L * 1024L * 1024L
    }

    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)

    fun modelsDir(): File = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun mlcModelsDir(): File = File(modelsDir(), "mlc").apply { mkdirs() }

    /** Compatibilità con la UI e i salvataggi delle build precedenti. */
    fun listModels(): List<File> = listLocalModels()
        .filter { it.backend == LocalAiBackend.LLAMA_CPP }
        .map { it.path }

    fun listLocalModels(): List<LocalModel> {
        val gguf = modelsDir().listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("gguf", true) }
            ?.map {
                LocalModel(
                    id = it.name,
                    displayName = it.name,
                    backend = LocalAiBackend.LLAMA_CPP,
                    path = it,
                    sizeBytes = it.length()
                )
            }
            .orEmpty()
        val mlc = mlcModelsDir().listFiles()
            ?.asSequence()
            ?.filter { isValidMlcDirectory(it, requireFullSize = false) }
            ?.map {
                LocalModel(
                    id = it.name,
                    displayName = it.name.removeSuffix(".mlc"),
                    backend = LocalAiBackend.MLC_LLM,
                    path = it,
                    sizeBytes = directorySize(it)
                )
            }
            .orEmpty()
        return (gguf + mlc).sortedWith(compareBy({ it.backend.ordinal }, { it.displayName.lowercase() })).toList()
    }

    fun activeBackend(): LocalAiBackend = runCatching {
        LocalAiBackend.valueOf(prefs.getString(PREF_BACKEND, null).orEmpty())
    }.getOrDefault(LocalAiBackend.LLAMA_CPP)

    fun activeModelName(): String? = activeModel()?.id

    fun activeModel(): LocalModel? {
        val id = prefs.getString(PREF_ACTIVE, null) ?: return null
        val backend = activeBackend()
        val model = listLocalModels().firstOrNull { it.id == id && it.backend == backend }
        if (model == null) prefs.edit().remove(PREF_ACTIVE).remove(PREF_BACKEND).apply()
        return model
    }

    fun activeModelFile(): File? = activeModel()?.path

    fun setActive(model: LocalModel) {
        // Il cambio effettivo viene eseguito da AiEngine.restart() su Dispatchers.IO.
        prefs.edit()
            .putString(PREF_ACTIVE, model.id)
            .putString(PREF_BACKEND, model.backend.name)
            .apply()
    }

    fun setActive(file: File) {
        val backend = if (file.isDirectory) LocalAiBackend.MLC_LLM else LocalAiBackend.LLAMA_CPP
        val model = listLocalModels().firstOrNull { it.path.absolutePath == file.absolutePath && it.backend == backend }
            ?: LocalModel(file.name, file.name.removeSuffix(".mlc"), backend, file, modelSize(file))
        setActive(model)
    }

    fun deleteModel(file: File): Boolean {
        val active = activeModel()
        val wasActive = active?.path?.absolutePath == file.absolutePath
        if (wasActive) prefs.edit().remove(PREF_ACTIVE).remove(PREF_BACKEND).apply()
        val removed = when {
            !file.exists() -> true
            file.isDirectory -> file.deleteRecursively()
            else -> file.delete()
        }
        if (removed && file.name == ModelDownloadManager.MODEL_NAME) {
            ModelDownloadManager(context).markModelDeleted()
        }
        return removed
    }

    fun importModel(uri: Uri): File {
        val originalName = displayName(uri)
        return when {
            originalName.endsWith(".gguf", true) -> importGguf(uri, originalName)
            originalName.endsWith(".tar", true) -> importMlcArchive(uri, originalName)
            else -> error("Formato non riconosciuto: scegli un file .gguf oppure il pacchetto MLC .tar")
        }
    }

    fun mlcRuntimeConfig(): MlcRuntimeConfig? = runCatching {
        val text = context.assets.open(MLC_CONFIG).bufferedReader().use { it.readText() }
        val first = JSONObject(text).getJSONArray("model_list").getJSONObject(0)
        MlcRuntimeConfig(
            modelId = first.getString("model_id"),
            modelLib = first.getString("model_lib")
        )
    }.getOrNull()

    fun recommendedDownloader(): ModelDownloadManager = ModelDownloadManager(context)

    private fun importGguf(uri: Uri, originalName: String): File {
        val baseName = originalName.takeIf { it.endsWith(".gguf", true) }
            ?: "model_${System.currentTimeMillis()}.gguf"
        val dest = uniqueFile(modelsDir(), baseName, ".gguf")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Impossibile aprire il file" }
                FileOutputStream(dest).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            require(dest.length() > 1024 * 1024) { "Il file GGUF è vuoto o incompleto" }
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        setActive(dest)
        return dest
    }

    private fun importMlcArchive(uri: Uri, originalName: String): File {
        val safeBase = originalName.removeSuffix(".tar").removeSuffix(".TAR")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "Qwen2.5-3B-Uncensored-MLC" }
        val finalDir = uniqueDirectory(mlcModelsDir(), "$safeBase.mlc")
        val tempDir = File(mlcModelsDir(), ".import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri).use { raw ->
                requireNotNull(raw) { "Impossibile aprire il pacchetto MLC" }
                TarArchiveInputStream(BufferedInputStream(raw, 1024 * 1024)).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        val normalized = entry.name.replace('\\', '/').removePrefix("./")
                        if (!normalized.startsWith("model/")) continue
                        val relative = normalized.removePrefix("model/")
                        if (relative.isBlank()) continue
                        val destination = File(tempDir, relative)
                        require(destination.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                            "Percorso non valido nel pacchetto MLC"
                        }
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output -> tar.copyTo(output, 1024 * 1024) }
                    }
                }
            }
            require(isValidMlcDirectory(tempDir, requireFullSize = true)) {
                "Pacchetto MLC incompleto: mancano configurazione, tokenizer o shard dei pesi"
            }
            require(tempDir.renameTo(finalDir)) { "Impossibile finalizzare il modello MLC" }
        } catch (t: Throwable) {
            tempDir.deleteRecursively()
            finalDir.deleteRecursively()
            throw t
        }
        setActive(finalDir)
        return finalDir
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?: "model_${System.currentTimeMillis()}"

    private fun isValidMlcDirectory(directory: File, requireFullSize: Boolean): Boolean {
        if (!directory.isDirectory) return false
        val required = listOf("mlc-chat-config.json", "tensor-cache.json", "tokenizer.json")
        if (required.any { !File(directory, it).isFile }) return false
        if (directory.listFiles()?.none { it.isFile && it.name.startsWith("params_shard_") && it.extension == "bin" } != false) {
            return false
        }
        return !requireFullSize || directorySize(directory) >= MIN_MLC_BYTES
    }

    private fun directorySize(directory: File): Long = directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun modelSize(file: File): Long = if (file.isDirectory) directorySize(file) else file.length()

    private fun uniqueFile(parent: File, name: String, suffix: String): File {
        val first = File(parent, name)
        if (!first.exists()) return first
        return File(parent, name.removeSuffix(suffix) + "_${System.currentTimeMillis()}" + suffix)
    }

    private fun uniqueDirectory(parent: File, name: String): File {
        val first = File(parent, name)
        if (!first.exists()) return first
        return File(parent, name.removeSuffix(".mlc") + "_${System.currentTimeMillis()}.mlc")
    }
}

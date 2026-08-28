#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <csignal>
#include <mutex>
#include <string>
#include <unistd.h>
#include <vector>

#define TAG "NeonTidesLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex gMutex;
static std::mutex gDiagnosticsMutex;
static llama_model *gModel = nullptr;
static llama_context *gConversationCtx = nullptr;
static std::atomic_bool gModelLoaded{false};
static std::atomic_bool gConversationPrepared{false};
static int32_t gConversationTokens = 0;
static int32_t gConversationBaseTokens = 0;
static uint32_t gCtx = 2048;
static int32_t gThreads = 4;
static bool gBackend = false;
static std::string gDiagnostics = "NeonTidesLLM diagnostics initialized\n";

// Viene installato soltanto nel processo :mlc_runtime. Una terminazione
// controllata evita che un abort del driver GPU venga attribuito al processo
// principale del gioco; il client Binder rileva la morte e applica il rollback.
static void mlcProcessCrashGuard(int signalNumber) {
    _exit(128 + signalNumber);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_installProcessCrashGuard(
        JNIEnv *, jobject) {
    struct sigaction action {};
    action.sa_handler = mlcProcessCrashGuard;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_RESETHAND;
    // Non sostituire SIGSEGV/SIGBUS: ART li usa internamente tramite sigchain.
    // Gli assert fatali di TVM/OpenCL arrivano invece come SIGABRT.
    const int guardedSignals[] = {SIGABRT};
    for (const int guardedSignal : guardedSignals) {
        sigaction(guardedSignal, &action, nullptr);
    }
}

static void diag(const std::string &message) {
    std::lock_guard<std::mutex> lock(gDiagnosticsMutex);
    gDiagnostics += message + "\n";
    if (gDiagnostics.size() > 16000) gDiagnostics.erase(0, gDiagnostics.size() - 12000);
}

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// NewStringUTF accetta il Modified UTF-8 di JNI, non tutti i frammenti UTF-8
// prodotti dal tokenizer (per esempio emoji e caratteri a quattro byte).
// La conversione esplicita in UTF-16 evita testo corrotto o eccezioni durante
// lo streaming di un token che contiene Unicode standard.
static jstring utf8JString(JNIEnv *env, const std::string &text) {
    std::vector<jchar> utf16;
    utf16.reserve(text.size());
    for (size_t i = 0; i < text.size();) {
        const auto first = (unsigned char)text[i];
        uint32_t cp = 0;
        size_t length = 0;
        if (first < 0x80) {
            cp = first;
            length = 1;
        } else if ((first & 0xE0) == 0xC0) {
            cp = first & 0x1F;
            length = 2;
        } else if ((first & 0xF0) == 0xE0) {
            cp = first & 0x0F;
            length = 3;
        } else if ((first & 0xF8) == 0xF0) {
            cp = first & 0x07;
            length = 4;
        } else {
            utf16.push_back(0xFFFD);
            ++i;
            continue;
        }

        bool valid = i + length <= text.size();
        for (size_t j = 1; valid && j < length; ++j) {
            const auto next = (unsigned char)text[i + j];
            valid = (next & 0xC0) == 0x80;
            if (valid) cp = (cp << 6) | (next & 0x3F);
        }
        const bool overlong = (length == 2 && cp < 0x80) ||
                              (length == 3 && cp < 0x800) ||
                              (length == 4 && cp < 0x10000);
        if (!valid || overlong || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            utf16.push_back(0xFFFD);
            ++i;
            continue;
        }
        if (cp <= 0xFFFF) {
            utf16.push_back((jchar)cp);
        } else {
            cp -= 0x10000;
            utf16.push_back((jchar)(0xD800 + (cp >> 10)));
            utf16.push_back((jchar)(0xDC00 + (cp & 0x3FF)));
        }
        i += length;
    }
    return env->NewString(utf16.data(), (jsize)utf16.size());
}

static bool hasCompleteUtf8Tail(const std::string &text) {
    for (size_t i = 0; i < text.size();) {
        const auto first = (unsigned char)text[i];
        size_t length = 1;
        if ((first & 0xE0) == 0xC0) length = 2;
        else if ((first & 0xF0) == 0xE0) length = 3;
        else if ((first & 0xF8) == 0xF0) length = 4;
        if (i + length > text.size()) return false;
        i += length;
    }
    return true;
}

static jstring jsonMessage(JNIEnv *env, const char *message) {
    std::string out =
        std::string("{\"reply\":\"") + message +
        "\",\"emotion\":\"thoughtful\",\"affection\":0,\"attraction\":0,\"trust\":0,\"memory\":\"\"}";
    return env->NewStringUTF(out.c_str());
}

static void ensureBackend() {
    if (!gBackend) {
        llama_backend_init();
        gBackend = true;
    }
}

static std::string piece(const llama_vocab *vocab, llama_token tok) {
    char buf[256];
    int32_t n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
    if (n >= 0) return std::string(buf, (size_t)n);
    std::vector<char> big((size_t)-n);
    n = llama_token_to_piece(vocab, tok, big.data(), (int32_t)big.size(), 0, true);
    return n > 0 ? std::string(big.data(), (size_t)n) : std::string();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_loadModel(
        JNIEnv *env, jobject, jstring path, jint contextSize, jint threads) {
    std::lock_guard<std::mutex> lock(gMutex);
    const auto loadStarted = std::chrono::steady_clock::now();
    ensureBackend();

    if (gModel) {
        if (gConversationCtx) {
            llama_free(gConversationCtx);
            gConversationCtx = nullptr;
            gConversationTokens = 0;
            gConversationBaseTokens = 0;
            gConversationPrepared.store(false);
        }
        llama_model_free(gModel);
        gModel = nullptr;
        gModelLoaded.store(false);
    }

    auto p = jstr(env, path);
    LOGI("Loading model: %s", p.c_str());
    diag("LOAD start: " + p);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.use_mmap = true;
    mp.use_mlock = false;

    gModel = llama_model_load_from_file(p.c_str(), mp);
    if (!gModel) {
        LOGE("Model load failed");
        diag("LOAD ERROR: llama_model_load_from_file returned null");
        return JNI_FALSE;
    }

    gCtx = (uint32_t)std::clamp((int)contextSize, 1024, 4096);
    gThreads = std::clamp((int)threads, 1, 6);
    LOGI("Model loaded. ctx=%u threads=%d", gCtx, gThreads);
    const auto loadMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - loadStarted
    ).count();
    diag("LOAD OK: context=" + std::to_string(gCtx) +
         " threads=" + std::to_string(gThreads) +
         " load_ms=" + std::to_string(loadMs));
    gModelLoaded.store(true);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_isModelLoaded(
        JNIEnv *, jobject) {
    return gModelLoaded.load() ? JNI_TRUE : JNI_FALSE;
}

static llama_context *newContext(int32_t promptTokens) {
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = gCtx;
    cp.n_batch = std::min<uint32_t>(gCtx, std::max<uint32_t>(256, (uint32_t)promptTokens));
    cp.n_ubatch = std::min<uint32_t>(cp.n_batch, 256);
    cp.n_threads = gThreads;
    cp.n_threads_batch = gThreads;
    return llama_init_from_model(gModel, cp);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_prepareConversation(
        JNIEnv *env, jobject, jstring contextJ) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gModel) return JNI_FALSE;
    const auto cacheStarted = std::chrono::steady_clock::now();
    gConversationPrepared.store(false);
    if (gConversationCtx) {
        llama_free(gConversationCtx);
        gConversationCtx = nullptr;
        gConversationTokens = 0;
        gConversationBaseTokens = 0;
    }

    std::string text = jstr(env, contextJ);
    const llama_vocab *vocab = llama_model_get_vocab(gModel);
    int32_t count = -llama_tokenize(vocab, text.c_str(), (int32_t)text.size(), nullptr, 0, true, true);
    if (count <= 0 || count > (int32_t)gCtx - 160) {
        diag("CACHE ERROR: invalid_tokens=" + std::to_string(count) +
             " context_limit=" + std::to_string(gCtx));
        return JNI_FALSE;
    }
    diag("CACHE prepare: prompt_chars=" + std::to_string(text.size()) +
         " prompt_tokens=" + std::to_string(count));

    std::vector<llama_token> tokens((size_t)count);
    if (llama_tokenize(vocab, text.c_str(), (int32_t)text.size(), tokens.data(), count, true, true) < 0) {
        diag("CACHE ERROR: tokenization failed");
        return JNI_FALSE;
    }

    gConversationCtx = newContext(count);
    gConversationTokens = 0;
    if (!gConversationCtx) {
        diag("CACHE ERROR: context allocation failed");
        return JNI_FALSE;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), count);
    if (llama_decode(gConversationCtx, batch) != 0) {
        llama_free(gConversationCtx);
        gConversationCtx = nullptr;
        diag("CACHE ERROR: llama_decode failed");
        return JNI_FALSE;
    }
    gConversationTokens = count;
    gConversationBaseTokens = count;
    gConversationPrepared.store(true);
    LOGI("Conversation context cached: %d tokens", count);
    const auto cacheMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - cacheStarted
    ).count();
    diag("CACHE OK: tokens=" + std::to_string(count) +
         " prepare_ms=" + std::to_string(cacheMs));
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_isConversationPrepared(
        JNIEnv *, jobject) {
    return gConversationPrepared.load() ? JNI_TRUE : JNI_FALSE;
}

// Ripristina il solo prefisso permanente senza rivalutarlo. Il laboratorio usa
// questa operazione fra prove indipendenti, così le domande precedenti non
// contaminano la semantica e la cache di Luna non viene ricostruita.
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_rewindConversation(
        JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gConversationCtx || !gConversationPrepared.load() || gConversationBaseTokens <= 0) {
        diag("CACHE rewind: unavailable");
        return JNI_FALSE;
    }
    llama_memory_t memory = llama_get_memory(gConversationCtx);
    if (!memory || !llama_memory_seq_rm(memory, -1, gConversationBaseTokens, -1)) {
        diag("CACHE rewind: failed");
        return JNI_FALSE;
    }
    gConversationTokens = gConversationBaseTokens;
    diag("CACHE rewind: base_tokens=" + std::to_string(gConversationBaseTokens));
    return JNI_TRUE;
}

static jstring generateInternal(
        JNIEnv *env,
        jstring promptJ,
        jint maxTokens,
        jfloat temperature,
        jobject streamCallback) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gModel) return jsonMessage(env, "Il modello IA non è caricato.");

    const auto promptStarted = std::chrono::steady_clock::now();
    const auto elapsedMs = [](const auto &from) {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - from
        ).count();
    };

    std::string prompt = jstr(env, promptJ);
    diag("GENERATE start: prompt_chars=" + std::to_string(prompt.size()) +
         " max_tokens=" + std::to_string((int)maxTokens));
    const llama_vocab *vocab = llama_model_get_vocab(gModel);

    const bool cached = gConversationCtx != nullptr;
    int32_t nPrompt = -llama_tokenize(
        vocab, prompt.c_str(), (int32_t)prompt.size(),
        nullptr, 0, !cached, true
    );

    if (nPrompt <= 0) {
        diag("TOKENIZE ERROR: invalid prompt token count");
        return jsonMessage(env, "Non riesco a leggere il prompt.");
    }
    diag("TOKENIZE OK: tokens=" + std::to_string(nPrompt));
    if (nPrompt + (cached ? gConversationTokens : 0) > (int32_t)gCtx - 192) {
        LOGE("Prompt too long: %d tokens for ctx %u", nPrompt, gCtx);
        diag("CONTEXT ERROR: prompt too long");
        return jsonMessage(env, "La conversazione è diventata troppo lunga. Chiudi e riapri la chat.");
    }

    std::vector<llama_token> tokens((size_t)nPrompt);
    int32_t tokenized = llama_tokenize(
        vocab, prompt.c_str(), (int32_t)prompt.size(),
        tokens.data(), (int32_t)tokens.size(), !cached, true
    );
    if (tokenized < 0) return jsonMessage(env, "Errore durante la preparazione del testo.");

    llama_context *ctx = cached ? gConversationCtx : newContext(nPrompt);
    if (!ctx) {
        diag("CONTEXT ERROR: llama_init_from_model returned null");
        return jsonMessage(env, "Memoria insufficiente per avviare il modello IA.");
    }

    const int32_t promptStart = cached ? gConversationTokens : 0;
    const auto rollbackCachedTurn = [&]() {
        if (!cached || !gConversationCtx) return;
        llama_memory_t memory = llama_get_memory(gConversationCtx);
        if (memory && llama_memory_seq_rm(memory, -1, promptStart, -1)) {
            gConversationTokens = promptStart;
            gConversationPrepared.store(true);
            diag("CACHE rollback: tokens=" + std::to_string(gConversationTokens));
            return;
        }
        llama_free(gConversationCtx);
        gConversationCtx = nullptr;
        gConversationTokens = 0;
        gConversationBaseTokens = 0;
        gConversationPrepared.store(false);
        diag("CACHE rollback failed: cache cleared");
    };

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        if (cached) rollbackCachedTurn();
        else llama_free(ctx);
        diag("DECODE ERROR: initial prompt decode failed");
        return jsonMessage(env, "Il modello non è riuscito a elaborare la conversazione.");
    }
    if (cached) gConversationTokens += nPrompt;

    const auto promptMs = elapsedMs(promptStarted);
    diag("PROMPT OK: evaluation_ms=" + std::to_string(promptMs));
    if (promptMs >= 26000) {
        if (cached) rollbackCachedTurn();
        else llama_free(ctx);
        diag("TIMEOUT: prompt evaluation_ms=" + std::to_string(promptMs));
        return jsonMessage(env, "Il modello è troppo lento su questo dispositivo. Prova un GGUF più piccolo.");
    }

    auto sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.85f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(
        std::clamp((float)temperature, 0.05f, 1.5f)
    ));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    std::string pendingChunk;
    int limit = std::clamp((int)maxTokens, 1, 160);
    int generatedTokens = 0;
    long firstTokenMs = -1;
    const auto generationStarted = std::chrono::steady_clock::now();
    auto lastStreamFlush = generationStarted;

    jmethodID onTokenMethod = nullptr;
    if (streamCallback) {
        jclass callbackClass = env->GetObjectClass(streamCallback);
        if (callbackClass) {
            onTokenMethod = env->GetMethodID(
                callbackClass, "onToken", "(Ljava/lang/String;)V"
            );
            env->DeleteLocalRef(callbackClass);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            onTokenMethod = nullptr;
            diag("STREAM WARNING: callback method unavailable");
        }
    }

    const auto emitChunk = [&]() {
        if (!streamCallback || !onTokenMethod || pendingChunk.empty()) return;
        if (!hasCompleteUtf8Tail(pendingChunk)) return;
        jstring text = utf8JString(env, pendingChunk);
        env->CallVoidMethod(streamCallback, onTokenMethod, text);
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            onTokenMethod = nullptr;
            diag("STREAM WARNING: callback failed");
        }
        pendingChunk.clear();
        lastStreamFlush = std::chrono::steady_clock::now();
    };

    for (int i = 0; i < limit; ++i) {
        if (elapsedMs(generationStarted) >= 18000) {
            llama_sampler_free(sampler);
            if (cached) rollbackCachedTurn();
            else llama_free(ctx);
            LOGE("Generation timeout after 18 seconds");
            diag("TIMEOUT: generation_ms=" + std::to_string(elapsedMs(generationStarted)) +
                 " generated_tokens=" + std::to_string(i));
            return jsonMessage(env, "Il modello è troppo lento su questo dispositivo. Prova un GGUF più piccolo.");
        }

        llama_token tok = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        llama_sampler_accept(sampler, tok);
        const std::string tokenText = piece(vocab, tok);
        out += tokenText;
        pendingChunk += tokenText;
        generatedTokens = i + 1;
        if (firstTokenMs < 0 && !tokenText.empty()) {
            firstTokenMs = elapsedMs(generationStarted);
            diag("FIRST TOKEN: after_prompt_ms=" + std::to_string(firstTokenMs) +
                 " from_generate_start_ms=" + std::to_string(promptMs + firstTokenMs));
        }

        // Non troncare a un numero fisso se il modello ha appena concluso una
        // risposta sensata. Dopo una lunghezza minima, termina alla fine della
        // prima o seconda frase completa e prima di eventuali nuovi ruoli.
        const bool roleLeak = out.find("<|im_end|>") != std::string::npos ||
                              out.find("<|eot_id|>") != std::string::npos ||
                              out.find("<|start_header_id|>user") != std::string::npos ||
                              out.find("\n### Giocatore") != std::string::npos ||
                              out.find("\nUser:") != std::string::npos ||
                              out.find("\nUtente:") != std::string::npos ||
                              out.find("\nGIOCATORE:") != std::string::npos;
        const auto lastVisible = out.find_last_not_of(" \t\r\n");
        const bool sentenceEnd = lastVisible != std::string::npos &&
            (out[lastVisible] == '.' || out[lastVisible] == '!' || out[lastVisible] == '?');

        llama_token next = tok;
        batch = llama_batch_get_one(&next, 1);
        if (llama_decode(ctx, batch) != 0) {
            diag("DECODE ERROR: generated token " + std::to_string(i));
            break;
        }
        if (cached) gConversationTokens++;
        const auto sinceFlush = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - lastStreamFlush
        ).count();
        if (i == 0 || pendingChunk.size() >= 14 || sinceFlush >= 80 || sentenceEnd || roleLeak) {
            emitChunk();
        }
        if (roleLeak || (i >= 31 && sentenceEnd)) break;
    }

    emitChunk();

    llama_sampler_free(sampler);
    if (!cached) llama_free(ctx);

    if (out.empty()) {
        if (cached) rollbackCachedTurn();
        diag("GENERATE ERROR: empty output");
        return jsonMessage(env, "Il modello non ha prodotto una risposta.");
    }
    const auto generationMs = std::max<long>(1, elapsedMs(generationStarted));
    const int tokensPerSecond10 = (int)((generatedTokens * 10000L) / generationMs);
    diag("GENERATE OK: output_chars=" + std::to_string(out.size()) +
         " prompt_ms=" + std::to_string(promptMs) +
         " generation_ms=" + std::to_string(generationMs) +
         " first_token_ms=" + std::to_string(firstTokenMs) +
         " total_ms=" + std::to_string(promptMs + generationMs) +
         " tokens=" + std::to_string(generatedTokens) +
         " tokens_per_second=" + std::to_string(tokensPerSecond10 / 10) + "." +
         std::to_string(tokensPerSecond10 % 10));
    return utf8JString(env, out);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_generate(
        JNIEnv *env, jobject, jstring promptJ, jint maxTokens, jfloat temperature) {
    return generateInternal(env, promptJ, maxTokens, temperature, nullptr);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_generateStreaming(
        JNIEnv *env,
        jobject,
        jstring promptJ,
        jint maxTokens,
        jfloat temperature,
        jobject streamCallback) {
    return generateInternal(env, promptJ, maxTokens, temperature, streamCallback);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_getDiagnostics(
        JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(gDiagnosticsMutex);
    return env->NewStringUTF(gDiagnostics.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_clearDiagnostics(
        JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gDiagnosticsMutex);
    gDiagnostics = "Diagnostics cleared\n";
}

extern "C"
JNIEXPORT void JNICALL
Java_com_neontides_nativeapp_ai_NativeLlama_unloadModel(
        JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    gConversationPrepared.store(false);
    gModelLoaded.store(false);
    if (gModel) {
        if (gConversationCtx) {
            llama_free(gConversationCtx);
            gConversationCtx = nullptr;
            gConversationTokens = 0;
            gConversationBaseTokens = 0;
        }
        llama_model_free(gModel);
        gModel = nullptr;
    }
    if (gBackend) {
        llama_backend_free();
        gBackend = false;
    }
}

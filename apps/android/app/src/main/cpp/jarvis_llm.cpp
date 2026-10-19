#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

// CPU-only, bounded, synchronous JNI. Kotlin calls ONLY on a single worker.
// No network, shell, Android intents, model downloads, microphone or tools.
namespace {
std::mutex inference_mutex;
std::once_flag backend_once;

// Maximum ONE retained weight set per process; always guarded by inference_mutex.
// Session context/sampler is fresh every request, preventing conversational
// state leakage. The Kotlin side supplies a *verified* SHA-256 revision.
llama_model * cached_model = nullptr;
std::string cached_path;
std::string cached_revision;
uint64_t native_model_loads = 0;
uint64_t native_cache_hits = 0;
uint64_t native_cache_evictions = 0;

void release_cached_locked() {
    if (cached_model) {
        llama_model_free(cached_model);
        cached_model = nullptr;
        native_cache_evictions++;
    }
    cached_path.clear();
    cached_revision.clear();
}

bool valid_sha256(const std::string & sha) {
    if (sha.size() != 64) return false;
    for (char ch : sha) {
        if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) return false;
    }
    return true;
}

jstring make_java_string(JNIEnv * env, const std::string & text) {
    const auto length = static_cast<jsize>(std::min<size_t>(text.size(), 8192));
    jbyteArray bytes = env->NewByteArray(length);
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, length,
        reinterpret_cast<const jbyte *>(text.data()));
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) { env->DeleteLocalRef(bytes); return nullptr; }
    jmethodID ctor = env->GetMethodID(stringClass, "<init>",
        "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto result = reinterpret_cast<jstring>(
        env->NewObject(stringClass, ctor, bytes, encoding));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(stringClass);
    return result;
}

bool was_cancelled(JNIEnv * env, jobject signal, jmethodID getMethod) {
    return env->CallBooleanMethod(signal, getMethod) == JNI_TRUE;
}

std::string run_inference(JNIEnv * env, const std::string & path,
    const std::string & revision, const std::string & prompt, jint requested,
    jobject signal, bool keep_loaded) {
    if (path.empty() || path.size() > 1024 || prompt.size() > 4096 ||
        requested < 1 || requested > 96 || !signal ||
        (keep_loaded && !valid_sha256(revision))) return "Solicitud local inválida.";

    jclass atomicClass = env->FindClass("java/util/concurrent/atomic/AtomicBoolean");
    if (!atomicClass) return "No se pudo iniciar la cancelación.";
    jmethodID getMethod = env->GetMethodID(atomicClass, "get", "()Z");
    if (!getMethod) { env->DeleteLocalRef(atomicClass); return "Cancelación no disponible."; }

    std::lock_guard<std::mutex> lock(inference_mutex);
    std::call_once(backend_once, [] { llama_backend_init(); });
    if (was_cancelled(env, signal, getMethod)) {
        env->DeleteLocalRef(atomicClass);
        return "";
    }

    llama_model * model = nullptr;
    if (keep_loaded && cached_model && cached_path == path &&
        cached_revision == revision) {
        model = cached_model;
        native_cache_hits++;
    } else {
        if (keep_loaded) release_cached_locked();
        llama_model_params modelOpts = llama_model_default_params();
        modelOpts.n_gpu_layers = 0; // portable CPU, no privileged GPU access
        model = llama_model_load_from_file(path.c_str(), modelOpts);
        if (model && keep_loaded) {
            cached_model = model;
            cached_path = path;
            cached_revision = revision;
            native_model_loads++;
        }
    }
    if (!model) {
        env->DeleteLocalRef(atomicClass);
        return "No se pudo cargar el modelo GGUF.";
    }

    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    std::string output;
    do {
        const llama_vocab * vocab = llama_model_get_vocab(model);
        if (!vocab) { output = "Vocabulario GGUF no disponible."; break; }
        const int count = -llama_tokenize(vocab,
            prompt.c_str(), static_cast<int>(prompt.size()),
            nullptr, 0, true, true);
        if (count < 1 || count + requested > 1024) {
            output = "La pregunta supera el contexto local máximo."; break;
        }
        std::vector<llama_token> promptTokens(static_cast<size_t>(count));
        if (llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                promptTokens.data(), count, true, true) != count) {
            output = "Error de tokenización del modelo."; break;
        }
        llama_context_params ctxOpts = llama_context_default_params();
        ctxOpts.n_ctx = 1024;
        ctxOpts.n_batch = 256;
        ctxOpts.n_ubatch = 256;
        ctxOpts.n_threads = 2;
        ctxOpts.n_threads_batch = 2;
        context = llama_init_from_model(model, ctxOpts);
        if (!context) { output = "Memoria insuficiente para el contexto GGUF."; break; }
        sampler = llama_sampler_init_greedy();
        if (!sampler) { output = "No se pudo iniciar el generador local."; break; }
        bool bad = false;
        for (int start = 0; start < count; start += 256) {
            if (was_cancelled(env, signal, getMethod)) { bad = true; break; }
            const int n = std::min(256, count - start);
            llama_batch batch = llama_batch_get_one(promptTokens.data() + start, n);
            if (llama_decode(context, batch) != 0) { bad = true; break; }
        }
        if (bad) { output = "Inferencia local cancelada o fallida."; break; }

        for (int i = 0; i < requested; i++) {
            if (was_cancelled(env, signal, getMethod)) { output.clear(); break; }
            const llama_token next = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, next)) break;
            std::vector<char> piece(256);
            int n = llama_token_to_piece(vocab, next, piece.data(),
                static_cast<int>(piece.size()), 0, true);
            if (n < 0 && n >= -4096) {
                piece.resize(static_cast<size_t>(-n));
                n = llama_token_to_piece(vocab, next, piece.data(),
                    static_cast<int>(piece.size()), 0, true);
            }
            if (n < 0) { output = "Fallo al decodificar una palabra."; break; }
            if (output.size() + static_cast<size_t>(n) > 4096) break;
            output.append(piece.data(), static_cast<size_t>(n));
            llama_token current = next;
            const llama_batch batch = llama_batch_get_one(&current, 1);
            if (llama_decode(context, batch) != 0) {
                output = "La generación local se interrumpió."; break;
            }
        }
    } while (false);

    if (sampler) llama_sampler_free(sampler);
    if (context) llama_free(context);
    if (!keep_loaded) llama_model_free(model);
    env->DeleteLocalRef(atomicClass);
    return output;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_cardenaspiero255_jarvis_LocalGgufInference_generate(
    JNIEnv * env, jobject /*self*/, jstring jpath,
    jstring jprompt, jint maxTokens, jobject cancellation) {
    if (!jpath || !jprompt || !cancellation)
        return make_java_string(env, "Parámetros faltantes.");
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!path || !prompt) {
        if (path) env->ReleaseStringUTFChars(jpath, path);
        if (prompt) env->ReleaseStringUTFChars(jprompt, prompt);
        return make_java_string(env, "No se pudo preparar el mensaje.");
    }
    std::string result = run_inference(
        env, path, "", prompt, maxTokens, cancellation, false);
    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return make_java_string(env, result);
}


// Cache-aware path: this entrypoint accepts the SHA-256 already authenticated
// by LocalGgufModelStore.withVerifiedModelRevision. Never trust path alone.
// The same mutex guards decode, eviction and all model-cache counters.
extern "C" JNIEXPORT jstring JNICALL
Java_com_cardenaspiero255_jarvis_LocalGgufInference_generateCached(
    JNIEnv * env, jobject /*self*/, jstring jpath, jstring jrevision,
    jstring jprompt, jint maxTokens, jobject cancellation) {
    if (!jpath || !jrevision || !jprompt || !cancellation)
        return make_java_string(env, "Parámetros faltantes.");
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    const char * revision = env->GetStringUTFChars(jrevision, nullptr);
    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!path || !revision || !prompt) {
        if (path) env->ReleaseStringUTFChars(jpath, path);
        if (revision) env->ReleaseStringUTFChars(jrevision, revision);
        if (prompt) env->ReleaseStringUTFChars(jprompt, prompt);
        return make_java_string(env, "No se pudo preparar el mensaje.");
    }
    const std::string result = run_inference(
        env, path, revision, prompt, maxTokens, cancellation, true);
    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jrevision, revision);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return make_java_string(env, result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_cardenaspiero255_jarvis_LocalGgufInference_releaseCachedModel(
    JNIEnv * /*env*/, jobject /*self*/) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    release_cached_locked();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_cardenaspiero255_jarvis_LocalGgufInference_nativeCacheCounters(
    JNIEnv * env, jobject /*self*/) {
    std::lock_guard<std::mutex> lock(inference_mutex);
    jlong counters[] = {
        static_cast<jlong>(native_model_loads),
        static_cast<jlong>(native_cache_hits),
        static_cast<jlong>(native_cache_evictions)
    };
    jlongArray result = env->NewLongArray(3);
    if (result) env->SetLongArrayRegion(result, 0, 3, counters);
    return result;
}

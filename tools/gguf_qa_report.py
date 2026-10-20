#!/usr/bin/env python3
"""Evidence-preserving analysis of real Android GGUF instrumentation metrics.

This report is a performance *measurement*, not model-quality or physical-
device certification. No fabricated results, no raw user prompts/audio stored.
"""
import argparse
import json
import math
import statistics
from pathlib import Path

PINNED_SHA256 = "e3131339bf4e8065265593d4fd8f7bb7ff2d3abff1edb5618aa1197b89cad9f5"
# Legacy marker in the raw GGUF-only fixture; it never exercised ASR/TTS.
KNOWN_VOICE_STATUS = "NOT_IMPLEMENTED"
# PR #91 introduced a one-turn local voice path, not evaluated by this suite.
REPORT_VOICE_STATUS = "NOT_MEASURED_IN_GGUF_E2E"


def nearest_rank(data, quantile):
    if not data:
        raise ValueError("Cannot estimate a percentile without observations")
    if not 0 < quantile <= 1:
        raise ValueError("Invalid quantile")
    values = sorted(data)
    return values[math.ceil(len(values) * quantile) - 1]


def _positive_int(value, name):
    if type(value) is not int or value <= 0:
        raise ValueError(f"{name} must be a strictly positive measured integer")
    return value


def evaluate(raw):
    if raw.get("schema_version") != 1:
        raise ValueError("Unknown benchmark schema")
    device = raw["device"]
    source = device.get("source")
    if source not in {"emulator", "physical_reported"}:
        raise ValueError("Explicit device provenance is mandatory")
    if source == "physical_reported":
        proof = device.get("provenance", {})
        if (proof.get("method") != "usb_adb_instrumentation" or
                not proof.get("session_id") or
                not proof.get("adb_serial_sha256")):
            raise ValueError("Physical claim lacks a device run provenance")
    if type(device.get("sdk_int")) is not int or device["sdk_int"] < 26:
        raise ValueError("Unknown or unsupported Android SDK")
    if not device.get("model") or not device.get("manufacturer"):
        raise ValueError("Missing device identifiers")

    model = raw["model"]
    if model.get("sha256") != PINNED_SHA256:
        raise ValueError("Measurements must use the pinned, authentic GGUF")
    if _positive_int(model.get("bytes"), "model.bytes") != 101016128:
        raise ValueError("Unexpected model size")

    metrics = raw["inference"]
    first = _positive_int(metrics.get("cold_request_ms"), "cold_request_ms")
    others = metrics.get("subsequent_request_ms")
    if not isinstance(others, list) or len(others) < 3:
        raise ValueError("At least 3 subsequent real observations required")
    others = [_positive_int(x, "subsequent_request_ms") for x in others]
    lengths = metrics.get("generated_characters")
    if not isinstance(lengths, list) or len(lengths) != len(others) + 1:
        raise ValueError("Output sizes must correspond to every inference")
    lengths = [_positive_int(x, "generated_characters") for x in lengths]
    base = _positive_int(metrics.get("baseline_pss_kib"), "baseline_pss_kib")
    peak = _positive_int(metrics.get("peak_pss_kib"), "peak_pss_kib")
    java = _positive_int(metrics.get("peak_java_heap_kib"), "peak_java_heap_kib")
    if peak < base:
        raise ValueError("Peak PSS cannot be below baseline PSS")

    cache = metrics.get("warm_cache")
    if not isinstance(cache, dict):
        raise ValueError("Genuine native cache counters are mandatory")
    loads = _positive_int(cache.get("native_model_loads"), "native_model_loads")
    hits = cache.get("native_cache_hits")
    evictions = cache.get("native_cache_evictions")
    if type(hits) is not int or hits < len(others):
        raise ValueError("All subsequent requests must hit the verified native cache")
    if type(evictions) is not int or evictions < 0:
        raise ValueError("Native cache eviction count must be nonnegative")
    if cache.get("verified_sha_revision") is not True or loads != 1:
        raise ValueError("Must use exactly one SHA-verified native model load")

    quality = raw["quality"]
    cases = quality.get("cases")
    if quality.get("method") != "keyword_proxy_v1" or not isinstance(cases, list) or len(cases) < 4:
        raise ValueError("Insufficient Spanish proxy evaluation cases")
    ids = set()
    successes = 0
    for case in cases:
        cid = case.get("id")
        matched = case.get("keyword_matched")
        if not isinstance(cid, str) or not cid or cid in ids or type(matched) is not bool:
            raise ValueError("Duplicate or invalid quality evaluation case")
        ids.add(cid)
        successes += int(matched)

    voice = raw["voice"]
    if (voice.get("gguf_voice_pipeline") != KNOWN_VOICE_STATUS or
            voice.get("asr_audio_validated") is not False or
            voice.get("tts_audio_validated") is not False or
            voice.get("llm_tool_authority") is not False):
        raise ValueError("The current voice integration must not claim certification")

    return {
        "schema_version": 1,
        "device": {
            "source": source,
            "model": device["model"],
            "manufacturer": device["manufacturer"],
            "sdk_int": device["sdk_int"],
            "physical_provenance_supplied": source == "physical_reported",
        },
        "model_sha256": PINNED_SHA256,
        "latency_ms": {
            "first": first,
            "subsequent_p50": statistics.median(others),
            "subsequent_p95": nearest_rank(others, 0.95),
            "subsequent_samples": len(others),
            "all_measured_ms": [first] + others,
        },
        "memory_kib": {
            "baseline_pss": base,
            "peak_pss": peak,
            "peak_pss_increase": peak - base,
            "peak_java_heap": java,
        },
        "warm_model": {
            "native_model_loads": loads,
            "native_cache_hits": hits,
            "native_cache_evictions": evictions,
            "verified_sha_revision": True,
            "first_is_cold": True,
        },
        "output_characters": lengths,
        "quality_proxy": {
            "method": "keyword_proxy_v1",
            "matched": successes,
            "count": len(cases),
            "percent": round(100 * successes / len(cases), 2),
            "cases": cases,
            "semantic_certification": False,
        },
        "voice": {
            "gguf_voice_pipeline": REPORT_VOICE_STATUS,
            "asr_audio_validated": False,
            "tts_audio_validated": False,
            "llm_tool_authority": False,
        },
        "certification": {
            "overall": "NOT_CERTIFIED",
            "physical_vivo_verified": False,
            "voice_e2e_verified": False,
            "semantic_quality_certified": False,
            "hardware_comparable": False,
        },
    }


def as_markdown(report):
    l = report["latency_ms"]
    m = report["memory_kib"]
    q = report["quality_proxy"]
    cache = report["warm_model"]
    return (
        "# JARVIS — GGUF: métricas medidas (no certificación)\n\n"
        f"- Dispositivo: {report['device']['manufacturer']} "
        f"{report['device']['model']} ({report['device']['source']})\n"
        f"- Primera consulta: {l['first']} ms\n"
        f"- Consultas siguientes (n={l['subsequent_samples']}): "
        f"P50={l['subsequent_p50']} ms, P95={l['subsequent_p95']} ms\n"
        f"- PSS base/pico: {m['baseline_pss']}/{m['peak_pss']} KiB "
        f"(incremento medido: {m['peak_pss_increase']} KiB)\n"
        f"- Heap Java pico: {m['peak_java_heap']} KiB\n"        f"- Modelos nativos cargados: {cache['native_model_loads']}; "
        f"reutilizaciones SHA-verificadas: {cache['native_cache_hits']}; "
        f"evicciones: {cache['native_cache_evictions']}\n"
        f"- Respuestas con palabra clave esperada: "
        f"{q['matched']}/{q['count']} ({q['percent']}%) — indicador léxico, "
        "NO precisión semántica.\n"
        "- Voz → GGUF → TTS: **NO MEDIDO POR ESTA SUITE**. "
        "Prueba acústica del propietario: **NO EJECUTADA**.\n"
        "- Energía, temperatura real y Vivo V25 Pro: **NO MEDIDOS**.\n"
        "- Certificación: **NOT_CERTIFIED**.\n"
        "\nLos pesos GGUF se reutilizan en RAM con el mismo SHA, pero cada "
        "consulta crea un contexto nuevo: «subsecuentes» no significa KV-cache "
        "conversacional ni que el modelo esté necesariamente optimizado. "
        "PSS es del proceso, no la RAM "
        "total del teléfono. Los valores del emulador NO predicen el Vivo.\n"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args()
    report = evaluate(json.loads(args.input.read_text(encoding="utf-8")))
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    args.markdown.write_text(as_markdown(report), encoding="utf-8")
    print("GGUF_QA_REPORT_VALIDATED: measurements from genuine model; "
          "quality keyword proxy only; physical and voice NOT_CERTIFIED")


if __name__ == "__main__":
    main()

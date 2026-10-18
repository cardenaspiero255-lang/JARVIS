"""Offline microphone-to-command and optional local TTS.

Vosk and sounddevice load only on explicit --listen. No always-on capture,
no cloud fallback and no microphone auto-start.
"""

import json
from pathlib import Path
from queue import Empty, Full, Queue
from time import monotonic


def _recognized_text(payload: str) -> str:
    try:
        result = json.loads(payload)
        text = result.get("text", "")
        return text.strip() if isinstance(text, str) else ""
    except (ValueError, AttributeError, TypeError):
        return ""


def transcribe_frames(recognizer, frames) -> str:
    """Reduce PCM16 chunks into the first complete Vosk utterance."""
    for frame in frames:
        if recognizer.AcceptWaveform(frame):
            utterance = _recognized_text(recognizer.Result())
            if utterance:
                return utterance
    return _recognized_text(recognizer.FinalResult())


def listen_once(model_directory: str, seconds: int = 12) -> str:
    """Capture <= 20 seconds with a bounded queue; discard PCM after decoding."""
    if not 1 <= seconds <= 20:
        raise ValueError("La escucha debe durar entre 1 y 20 segundos")
    model = Path(model_directory)
    if not model.is_dir():
        raise FileNotFoundError("Modelo Vosk local no encontrado; nunca se descarga automáticamente")
    from vosk import Model, KaldiRecognizer, SetLogLevel
    import sounddevice as sd

    SetLogLevel(-1)
    recognizer = KaldiRecognizer(Model(str(model)), 16_000)
    incoming = Queue(maxsize=8)

    def callback(indata, frames, time, status):
        # Callback must never block the audio driver.
        try:
            incoming.put_nowait(bytes(indata))
        except Full:
            try:
                incoming.get_nowait()
            except Empty:
                pass
            try:
                incoming.put_nowait(bytes(indata))
            except Full:
                pass

    def pcm_chunks():
        deadline = monotonic() + seconds
        while monotonic() < deadline:
            try:
                yield incoming.get(timeout=min(0.5, max(0.01, deadline - monotonic())))
            except Empty:
                continue

    with sd.RawInputStream(samplerate=16_000, blocksize=4000,
                           dtype="int16", channels=1, callback=callback):
        return transcribe_frames(recognizer, pcm_chunks())


def speak_local(text: str) -> None:
    """Use the system voice on Windows (SAPI5), without external services."""
    import pyttsx3
    engine = pyttsx3.init()
    try:
        engine.say(text)
        engine.runAndWait()
    finally:
        engine.stop()



def stream_utterances(model_directory: str, seconds: int = 60):
    """Explicit opt-in microphone session, maximum 60 seconds, offline Vosk."""
    if not 1 <= seconds <= 60:
        raise ValueError("La sesión debe durar entre 1 y 60 segundos")
    model = Path(model_directory)
    if not model.is_dir():
        raise FileNotFoundError("Modelo Vosk local no encontrado")
    from vosk import Model, KaldiRecognizer, SetLogLevel
    import sounddevice as sd

    SetLogLevel(-1)
    recognizer = KaldiRecognizer(Model(str(model)), 16_000)
    incoming = Queue(maxsize=8)

    def callback(indata, frames, time, status):
        try:
            incoming.put_nowait(bytes(indata))
        except Full:
            try:
                incoming.get_nowait()
                incoming.put_nowait(bytes(indata))
            except (Empty, Full):
                pass

    with sd.RawInputStream(samplerate=16_000, blocksize=4000, dtype="int16",
                           channels=1, callback=callback):
        deadline = monotonic() + seconds
        while monotonic() < deadline:
            try:
                frame = incoming.get(timeout=min(0.5, max(0.01, deadline - monotonic())))
            except Empty:
                continue
            if recognizer.AcceptWaveform(frame):
                text = _recognized_text(recognizer.Result())
                if text:
                    yield text
        final = _recognized_text(recognizer.FinalResult())
        if final:
            yield final

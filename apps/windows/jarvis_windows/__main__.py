"""Run: python -m jarvis_windows --command 'Jarvis, qué hora es' --speak"""

import argparse
from contextlib import closing
from time import monotonic
from .agent import Agent, WindowsDesktop
from .speech import listen_once, speak_local, stream_utterances
from .session import VoiceSessionRouter


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="JARVIS Windows — agente local experimental")
    exclusive = parser.add_mutually_exclusive_group(required=True)
    exclusive.add_argument("--command", help="Orden escrita, requiere prefijo Jarvis")
    exclusive.add_argument("--listen", action="store_true", help="Escucha una sola orden; no es always-on")
    exclusive.add_argument("--wake-session", action="store_true", help="Sesión Vosk de máximo 60 s, activación explícita")
    parser.add_argument("--session-seconds", type=int, default=60, help="Duración de sesión de 1 a 60 segundos")
    parser.add_argument("--model", help="Ruta del modelo Vosk español instalado voluntariamente")
    parser.add_argument("--speak", action="store_true", help="Reproducir respuesta mediante TTS local")
    args = parser.parse_args(argv)
    if (args.listen or args.wake_session) and not args.model:
        parser.error("--listen/--wake-session requiere --model local")
    if args.wake_session:
        if not 1 <= args.session_seconds <= 60:
            parser.error("--session-seconds debe estar entre 1 y 60")
        router = VoiceSessionRouter(Agent(WindowsDesktop()))
        try:
            with closing(stream_utterances(args.model, args.session_seconds)) as turns:
                for utterance in turns:
                    result = router.handle(utterance, monotonic())
                    print(f"{result.code}: {result.message}")
                    if result.code == "STOPPED":
                        break
                    if args.speak and result.code != "WAKE_PENDING":
                        speak_local(result.message)
            return 0
        except (OSError, ImportError, RuntimeError, ValueError) as exc:
            print("VOICE_UNAVAILABLE: " + type(exc).__name__)
            return 2
    try:
        text = listen_once(args.model) if args.listen else args.command
        result = Agent(WindowsDesktop()).execute(text)
        print(f"{result.code}: {result.message}")
        if args.speak:
            speak_local(result.message)
        return 0 if result.code in {"LOCAL_TIME", "WINDOWS_LISTED", "NO_WINDOWS",
                                    "STOPPED", "FOCUS_REQUESTED", "LAUNCH_REQUESTED"} else 2
    except (OSError, ImportError, RuntimeError, ValueError) as exc:
        print("VOICE_UNAVAILABLE: no se pudo inicializar el audio local. " + type(exc).__name__)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

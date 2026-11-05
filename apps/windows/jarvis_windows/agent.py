"""Local Windows automation adapter inspired by UFO/pywinauto patterns.

The command parser is deliberately deterministic. Audio and screen contents are
untrusted input, not authority to execute arbitrary shell commands.
"""

from dataclasses import dataclass
from datetime import datetime
import os
import re
import subprocess
import unicodedata
from typing import Callable


@dataclass(frozen=True)
class Result:
    code: str
    message: str


def _normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text.casefold())
    text = "".join(char for char in text if not unicodedata.combining(char))
    return " ".join(re.sub(r"[^a-z0-9]+", " ", text).split())


def parse_command(text: str) -> tuple[str, str | None]:
    """Match only a few authorized Spanish phrases; never let a model choose code."""
    if not isinstance(text, str) or len(text) > 200 or not text.strip():
        return "unknown", None
    normalized = _normalize(text)
    commands = {
        "jarvis que hora es": "time",
        "jarvis dime la hora": "time",
        "jarvis abre bloc de notas": "notepad",
        "jarvis abre el bloc de notas": "notepad",
        "jarvis ventanas abiertas": "windows",
        "jarvis que ventanas hay": "windows",
        "jarvis detente": "stop",
        "jarvis para": "stop",
    }
    if normalized in commands:
        return commands[normalized], None
    focus = re.fullmatch(r"\s*jarvis[\s,;:.!?]+enfoca\s+(.+?)\s*", text, re.IGNORECASE)
    if focus:
        title = focus.group(1).strip()
        if 1 <= len(title) <= 100 and all(ord(c) >= 32 for c in title) and not title.endswith(("?", "!", ";")):
            return "focus", title
    return "unknown", None


class WindowsDesktop:
    """Actual UIA adapter. Dependencies are imported only when used on Windows."""

    def __init__(self, window_provider: Callable | None = None,
                 launcher: Callable | None = None, os_name: str | None = None):
        self._window_provider = window_provider or self._uia_windows
        self._launcher = launcher or subprocess.Popen
        self._os_name = os_name if os_name is not None else os.name

    @staticmethod
    def _uia_windows():
        from pywinauto import Desktop
        return Desktop(backend="uia").windows()

    def _visible(self):
        if self._os_name != "nt" and self._window_provider == self._uia_windows:
            raise RuntimeError("UI Automation requiere Windows")
        return [window for window in self._window_provider()
                if window.is_visible() and window.window_text().strip()]

    def list_windows(self) -> list[str]:
        return sorted({window.window_text().strip() for window in self._visible()}, key=str.casefold)

    def focus(self, title: str) -> Result:
        try:
            matches = [window for window in self._visible()
                       if window.window_text().strip().casefold() == title.casefold()]
            if len(matches) != 1:
                if matches:
                    return Result("AMBIGUOUS_WINDOW", "Hay varias ventanas con ese nombre; no se ha cambiado el foco.")
                return Result("WINDOW_NOT_FOUND", "No encontré una ventana visible con ese título exacto.")
            matches[0].set_focus()
            return Result("FOCUS_REQUESTED", "Se solicitó enfocar la ventana; verifica que esté activa.")
        except Exception:
            return Result("FOCUS_FAILED", "Windows no permitió enfocar la ventana; no se confirma el cambio.")

    def launch_notepad(self) -> Result:
        if self._os_name != "nt":
            return Result("UNSUPPORTED_PLATFORM", "Abrir Bloc de notas solo funciona en Windows.")
        try:
            # Direct, fixed argv; no shell, scripts, arguments, or LLM-generated paths.
            self._launcher(["notepad.exe"])
        except (OSError, RuntimeError):
            return Result("LAUNCH_FAILED", "Windows rechazó iniciar Bloc de notas.")
        return Result("LAUNCH_REQUESTED", "Windows aceptó iniciar Bloc de notas; comprueba la ventana.")


class Agent:
    def __init__(self, desktop: WindowsDesktop, clock: Callable | None = None):
        self.desktop = desktop
        self.clock = clock or datetime.now

    def execute(self, utterance: str) -> Result:
        command, argument = parse_command(utterance)
        if command == "unknown":
            return Result("UNKNOWN_COMMAND", "Orden no reconocida. No se ejecutó ninguna acción.")
        if command == "stop":
            return Result("STOPPED", "Comando detenido. No se realizará ninguna acción adicional.")
        if command == "time":
            return Result("LOCAL_TIME", "Son las " + self.clock().strftime("%H:%M") + ".")
        if command == "notepad":
            return self.desktop.launch_notepad()
        if command == "focus":
            return self.desktop.focus(argument or "")
        try:
            titles = self.desktop.list_windows()
        except Exception:
            return Result("DESKTOP_UNAVAILABLE", "No se pudo consultar la interfaz de Windows.")
        if not titles:
            return Result("NO_WINDOWS", "No encontré ventanas visibles en la sesión actual.")
        return Result("WINDOWS_LISTED", "Ventanas: " + "; ".join(titles[:15]))

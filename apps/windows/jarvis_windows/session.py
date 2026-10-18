"""Time-limited, opt-in voice turn router. No identity verification or autonomy."""
from .agent import Result, _normalize


class VoiceSessionRouter:
    def __init__(self, agent, followup_seconds: float = 8.0):
        if not 1 <= followup_seconds <= 30:
            raise ValueError("invalid follow-up duration")
        self._agent = agent
        self._followup_seconds = followup_seconds
        self._wake_at = None

    def handle(self, utterance: str, now: float) -> Result:
        if now < 0:
            return Result("UNKNOWN_COMMAND", "Tiempo de sesión inválido.")
        if self._wake_at is not None and (
            now < self._wake_at or now - self._wake_at >= self._followup_seconds
        ):
            self._wake_at = None
        normalized = _normalize(utterance) if isinstance(utterance, str) and len(utterance) <= 200 else ""
        if normalized == "jarvis":
            self._wake_at = now
            return Result("WAKE_PENDING", "Jarvis detectado: puedes decir una orden permitida durante 8 segundos.")
        pending = self._wake_at is not None
        self._wake_at = None
        if pending and normalized and not normalized.startswith("jarvis "):
            # Only the next complete segment may omit the exact wake prefix.
            return self._agent.execute("Jarvis " + utterance)
        return self._agent.execute(utterance)

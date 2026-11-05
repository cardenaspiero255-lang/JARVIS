import sys
import unittest
from pathlib import Path
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from jarvis_windows.agent import Agent, WindowsDesktop, Result, parse_command
from jarvis_windows.speech import transcribe_frames


class FakeWindow:
    def __init__(self, title, visible=True):
        self.title = title
        self.visible = visible
        self.focus_count = 0

    def is_visible(self):
        return self.visible

    def window_text(self):
        return self.title

    def set_focus(self):
        self.focus_count += 1


class CommandTests(unittest.TestCase):
    def test_exact_wake_phrase_and_time(self):
        self.assertEqual(parse_command('¡JARVIS, qué hora es!'), ('time', None))
        self.assertEqual(parse_command('Jarvis abre Bloc de notas'), ('notepad', None))
        self.assertEqual(parse_command('Jarvis enfoca Editor'), ('focus', 'Editor'))
        self.assertEqual(parse_command('Jarvis, ventanas abiertas'), ('windows', None))
        self.assertEqual(parse_command('Jarvis detente'), ('stop', None))

    def test_negations_hypotheticals_and_untrusted_text_do_not_execute(self):
        for text in ('No Jarvis abre Bloc de notas', '¿Qué pasaría si Jarvis abre Bloc de notas?',
                     'Jarvis abre PowerShell', 'Jarvis ejecuta rm -rf /',
                     'jarvis abre bloc de notas y borra mis archivos',
                     'Jarvis enfoca ', 'Jarvis', ''):
            self.assertEqual(parse_command(text)[0], 'unknown', text)

    def test_windows_listing_filters_hidden_and_blanks(self):
        desktop = WindowsDesktop(lambda: [FakeWindow(' Notepad '), FakeWindow('Notepad'),
                                          FakeWindow('Hidden', False), FakeWindow('')])
        self.assertEqual(desktop.list_windows(), ['Notepad'])

    def test_exact_focus_ambiguous_rejected(self):
        a, b = FakeWindow('Editor'), FakeWindow('Editor')
        desktop = WindowsDesktop(lambda: [a, b])
        result = desktop.focus('Editor')
        self.assertEqual(result.code, 'AMBIGUOUS_WINDOW')
        self.assertEqual((a.focus_count, b.focus_count), (0, 0))

    def test_only_exact_title_is_focusable(self):
        a, b = FakeWindow('Editor'), FakeWindow('Editor - secreto')
        desktop = WindowsDesktop(lambda: [a, b])
        self.assertEqual(desktop.focus('Editor').code, 'FOCUS_REQUESTED')
        self.assertEqual((a.focus_count, b.focus_count), (1, 0))
        self.assertEqual(desktop.focus('Edit').code, 'WINDOW_NOT_FOUND')

    def test_low_risk_notepad_launch_uses_allowlisted_executable(self):
        calls = []
        class P:
            pid = 123
        desktop = WindowsDesktop(lambda: [], launcher=lambda args: (calls.append(args), P())[1], os_name='nt')
        result = Agent(desktop).execute('Jarvis abre Bloc de notas')
        self.assertEqual(result.code, 'LAUNCH_REQUESTED')
        self.assertEqual(calls, [['notepad.exe']])

    def test_non_windows_notepad_refused(self):
        desktop = WindowsDesktop(lambda: [], launcher=lambda args: self.fail('should not launch'), os_name='posix')
        self.assertEqual(Agent(desktop).execute('Jarvis abre Bloc de notas').code, 'UNSUPPORTED_PLATFORM')

    def test_voice_only_unknown_without_wake_is_rejected(self):
        desktop = WindowsDesktop(lambda: [])
        self.assertEqual(Agent(desktop).execute('Abre Bloc de notas').code, 'UNKNOWN_COMMAND')
        self.assertEqual(Agent(desktop).execute('Jarvis detente').code, 'STOPPED')

    def test_focus_error_does_not_claim_success(self):
        w = FakeWindow('Editor')
        w.set_focus = Mock(side_effect=RuntimeError('access denied'))
        self.assertEqual(WindowsDesktop(lambda: [w]).focus('Editor').code, 'FOCUS_FAILED')

    def test_window_enum_failure_fails_closed(self):
        def broken():
            raise RuntimeError('UI unavailable')
        self.assertEqual(Agent(WindowsDesktop(broken)).execute('Jarvis ventanas abiertas').code, 'DESKTOP_UNAVAILABLE')


class VoiceTests(unittest.TestCase):
    def test_first_complete_recognition_is_returned(self):
        class FakeRec:
            def AcceptWaveform(self, chunk):
                return chunk == b'last'
            def Result(self):
                return '{"text":"jarvis que hora es"}'
            def FinalResult(self):
                return '{"text":"ignored"}'
        self.assertEqual(transcribe_frames(FakeRec(), [b'first', b'last']), 'jarvis que hora es')

    def test_final_result_used_if_no_full_utterance(self):
        class FakeRec:
            def AcceptWaveform(self, chunk):
                return False
            def FinalResult(self):
                return '{"text":"jarvis detente"}'
        self.assertEqual(transcribe_frames(FakeRec(), [b'first']), 'jarvis detente')

    def test_invalid_recognition_json_fails_closed(self):
        class FakeRec:
            def AcceptWaveform(self, chunk):
                return True
            def Result(self):
                return 'not json'
            def FinalResult(self):
                return 'not json'
        self.assertEqual(transcribe_frames(FakeRec(), [b'first']), '')


class SafetyAndCliTests(unittest.TestCase):
    def test_no_model_does_not_start_microphone(self):
        from jarvis_windows.speech import listen_once
        with self.assertRaises(FileNotFoundError):
            listen_once('/missing-model-dir-00000000')

    def test_listen_requires_explicit_model(self):
        from jarvis_windows.__main__ import main
        with self.assertRaises(SystemExit) as err:
            main(['--listen'])
        self.assertEqual(err.exception.code, 2)

    def test_launch_failure_returns_not_verified(self):
        def deny(argv):
            raise OSError('not found')
        self.assertEqual(WindowsDesktop(lambda: [], launcher=deny, os_name='nt').launch_notepad().code,
                         'LAUNCH_FAILED')

    def test_wrong_windows_title_cannot_trick_focus(self):
        sensitive = FakeWindow('Password manager')
        adapter = WindowsDesktop(lambda: [sensitive])
        self.assertEqual(Agent(adapter).execute('Jarvis enfoca Password').code, 'WINDOW_NOT_FOUND')
        self.assertEqual(sensitive.focus_count, 0)


if __name__ == '__main__':
    unittest.main()

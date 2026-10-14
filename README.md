# JARVIS OMEGA SSS+

Early-stage reference implementation. See [architecture](docs/architecture/SSS_PLUS_ARCHITECTURE.md) and [master issue](https://github.com/cardenaspiero255-lang/JARVIS/issues/1).

The Python standard-library contract reference is **not** an Android app, GitHub integration, or production service. It provides envelope validation, capability allowlisting, expiry and in-memory deduplication for development tests.

Run tests: `python -m unittest discover -s tests -v`.

Next: persistent replay protection, authenticated provider transport, Android/Kotlin modules, schema-driven cross-language tests and CI. Do not use this reference gateway to authorize real destructive actions.

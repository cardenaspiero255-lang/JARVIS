# JARVIS OMEGA — endpoint de nube retirado

**Estado:** servicio externo de conversación retirado a favor de JARVIS
Offline Research y una futura inferencia generativa local.

El antiguo dominio `/v1/chat` se conserva temporalmente para las APK
anteriores. La función `api/chat.mjs` responde siempre **HTTP 410** con:

```json
{"error":"cloud_model_retired","mode":"offline_only"}
```

No lee claves API, no verifica tokens, no envía solicitudes externas,
no ejecuta un modelo y no realiza ninguna operación de cobro por tokens.
El endpoint no está diseñado para recibir conversaciones privadas ni
para mantener un servicio remoto. La aplicación Android deberá eliminar
el emparejamiento anterior; las versiones que aún lo usan reciben 410.

## Revocación pendiente fuera de GitHub

El propietario debe **revocar** la clave de OpenAI previamente creada
en OpenAI Platform y **eliminar** `OPENAI_API_KEY` y
`JARVIS_GATEWAY_TOKEN` de Vercel. Retirar el código NO revoca una clave
que también pudiera haberse filtrado ni garantiza que el despliegue
de producción se haya actualizado; hay que verificar el nuevo estado
de Vercel y controlar sus costos de alojamiento.

Una API gratuita sin identidad segura puede causar abuso; no se
sustituye un gateway facturable por una API anónima.

## Pruebas

Ejecutar:

```sh
node --test services/ai-gateway/test/*.test.mjs
```

Los tests comprueban que ambas solicitudes GET y POST reciben 410,
que no hay operaciones de red ni dependencias con el motor de OpenAI,
y que la reescritura Vercel sigue apuntando al nuevo manejador inerte.

El motor local gratuito está en `apps/android`; el trabajo de un LLM
local con pesos cuantizados sigue pendiente de una implementación
y mediciones físicas. No implica que se haya entrenado un modelo desde cero.

# JARVIS OMEGA — GPT-6 Astra gateway (prototype)

This optional service is **not deployed**. JARVIS functions offline until
its owner explicitly pairs a secure HTTPS gateway. The Android premium
interface and icon are unchanged. The model has no permissions to click
buttons, launch apps, operate Omega Hands, view the screen, or alter GitHub.

## Design

- Model: gpt-6-astra with OpenAI Responses API, store=false, high reasoning
  effort, bounded output and NO tools/function calls.
- API secret is stored only in server environment; never in the Android APK.
- A SEPARATE random gateway access token identifies the user in this
  single-user prototype. Android encrypts it using AndroidKeyStore AES-GCM.
- The server requires authorization, rejects untrusted model/role/tools,
  limits body sizes, doesn't follow upstream redirects and rate limits calls.
- The Android app validates HTTPS and its certificate, disallows redirects,
  bounds read sizes and times, and revokes in-flight calls on background/lock.
- Voice-to-cloud is separately opted into; each voice query requires a tap.
  OCR, screen capture, performance metrics and phone data are never uploaded.

## Server setup (deployment NOT yet performed)

Use Node.js 22+, a private host with trusted HTTPS proxy, server-side
environment secrets and API billing. Set:

- OPENAI_API_KEY: valid API project key from OpenAI Platform.
- JARVIS_GATEWAY_TOKEN: separate random 32+-character gateway secret.
  Generate one with: openssl rand -hex 32

Launch via: node services/ai-gateway/gateway.mjs

The process listens only on 127.0.0.1:8787 by default. Configure an HTTPS
proxy to forward /v1/chat to this local port; NEVER expose loopback HTTP
directly to the internet. Production needs independent per-device accounts,
server-side revocation, abuse defenses, quotas, billing caps and monitoring.

Run tests without OpenAI traffic:
node --test services/ai-gateway/test/*.test.mjs

## Deploy with the connected Vercel account

This repository currently keeps these files on JARVIS PR #27 (a draft).
The Vercel project needs the source to exist in the deployment branch;
do not assume the current main branch contains the gateway.

1. Import the GitHub repository `cardenaspiero255-lang/JARVIS` into Vercel.
   Choose **Root Directory: services/ai-gateway**, **Framework: Other**,
   and use Node.js 22. The checked-in `vercel.json` routes the public
   `/v1/chat` URL to a Node serverless function in `api/chat.mjs`.
2. Under **Project > Settings > Environment Variables**, add
   `OPENAI_API_KEY` as a **sensitive server-side secret**. Do not put it
   into the Vercel Git repository, frontend variables or Android source.
3. Add a different **sensitive** `JARVIS_GATEWAY_TOKEN` with at least
   32 random characters. Generate with `openssl rand -hex 32` locally.
   This token is copied only into JARVIS's password-protected pairing
   dialog; it is not your OpenAI API key.
4. Select the relevant environment (Preview for branch tests, Production
   after a reviewed merge). **Redeploy** after environment variables are
   configured. A request to the undeployed or unconfigured server cannot
   use Astra.
5. Open the protected URL `https://YOUR_PROJECT.vercel.app/v1/chat`
   from JARVIS's pairing dialog. Verify one authorized request and the
   server's model reply. Requests without the gateway bearer token should
   be rejected (HTTP 401). Avoid publishing the real token in tickets,
   commit messages or chat transcripts.

Vercel Functions do not run the persistent `gateway.mjs` loopback
listener; instead they export the Node handler. The in-process rate
limiter is **instance-local only** and must be replaced or backed by a
shared rate limiter plus reliable spend caps before production use.
Public deployments also require monitoring, identity lifecycle and
token rotation. Vercel usage can incur hosting charges; OpenAI API
charges are separate. Verify limits and set a cost budget before tests.

## Pair from Android without changing the approved interface

1. Deploy the backend with an HTTPS domain and set BOTH server secrets.
2. In the existing JARVIS chat, type conectar ia and press ENVIAR MENSAJE.
3. Enter https://YOUR_DOMAIN/v1/chat and the separate gateway token
   (not your OpenAI API key) into the native password-protected dialog.
4. Optionally select consent for cloud voice. Only then do HABLAR taps
   send a transcript. Typed chat messages are sent only when you tap SEND.
5. To remove pairing on this device type desconectar ia. To revoke a
   stolen/copied token globally, ROTATE IT ON THE SERVER AS WELL.
   Privacy lock only cancels active requests and clears in-RAM history.

## Limitations

- No production deployment or actual Astra response verified yet.
- ChatGPT subscription is not a substitute for OpenAI API billing.
- Voice output still uses locally installed Android text-to-speech.
  The British profile prioritizes voices identified as masculine by their
  local engine when available, and tunes rate and pitch. It is not an
  actor's original movie voice or an audio clone. Results vary by device.
- Full reasoning research tools, GameHub direct control, Ultra Sentinel
  execution, automation and general phone actions remain separate projects
  requiring permissions, authoritative adapters and independent E2E tests.
- SSS+ remains an unverified project target, not an official certification.

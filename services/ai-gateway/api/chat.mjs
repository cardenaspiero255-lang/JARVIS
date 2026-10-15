import {createGateway} from "../gateway.mjs";

// Vercel Functions run on demand; the real OpenAI key lives in project secrets.
// This module does not bind a public HTTP port or expose private configuration.
let gateway;
export default async function handler(req, res) {
  const apiKey = process.env.OPENAI_API_KEY;
  const accessToken = process.env.JARVIS_GATEWAY_TOKEN;
  if (!apiKey || !accessToken) {
    const body = JSON.stringify({error:"server_not_configured"});
    res.writeHead(503, {"content-type":"application/json; charset=utf-8",
      "cache-control":"no-store","content-length":Buffer.byteLength(body)});
    res.end(body);
    return;
  }
  if (!gateway) gateway = createGateway({apiKey, accessToken});
  // The hosting platform has already matched this *specific* Vercel
  // function through /api/chat or the public /v1/chat rewrite. Vercel may
  // preserve its original URL with internal query parameters on req.url.
  // Normalize only inside this function so the core gateway still performs
  // strict POST, bearer-token and payload validation. Do not loosen its
  // own route allowlist for an arbitrary standalone server.
  req.url = "/api/chat";
  return gateway(req, res);
}

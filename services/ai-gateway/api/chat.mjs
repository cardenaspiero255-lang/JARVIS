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
  return gateway(req, res);
}

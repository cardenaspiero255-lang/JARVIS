/**
 * JARVIS cloud model retired in favor of offline-first inference.
 *
 * No OpenAI keys, bearer tokens, model dispatch, outbound network requests
 * or billing can be triggered from this endpoint. Keep the route deployed
 * for older APKs that still send requests; they receive HTTP 410.
 *
 * NOTE: The owner must separately revoke OpenAI keys and remove secrets
 * from Vercel. Removing code does not revoke already issued credentials.
 */
export default function handler(_req,res) {
  const data=JSON.stringify({error:"cloud_model_retired",mode:"offline_only"});
  res.writeHead(410,{
    "content-type":"application/json; charset=utf-8",
    "cache-control":"no-store",
    "x-content-type-options":"nosniff",
    "content-length":Buffer.byteLength(data)
  });
  res.end(data);
}

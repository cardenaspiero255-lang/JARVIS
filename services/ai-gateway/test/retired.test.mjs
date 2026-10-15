import test from "node:test";
import assert from "node:assert/strict";
import {createServer} from "node:http";
import {readFileSync} from "node:fs";
import handler from "../api/chat.mjs";

test("The retired gateway never requires, reads or sends private API keys",()=>{
  const source=readFileSync(new URL("../api/chat.mjs",import.meta.url),"utf8");
  assert.doesNotMatch(source,/OPENAI_API_KEY|JARVIS_GATEWAY_TOKEN|api\.openai\.com|fetch\(|createGateway|gpt-6-astra/i);
  const config=JSON.parse(readFileSync(new URL("../vercel.json",import.meta.url),"utf8"));
  assert.ok(config.rewrites.some(r=>r.source==="/v1/chat"&&r.destination==="/api/chat"));
});

test("GET and POST return 410 with no upstream model invocation",async()=>{
  const savedKey=process.env.OPENAI_API_KEY;
  const savedToken=process.env.JARVIS_GATEWAY_TOKEN;
  process.env.OPENAI_API_KEY="sk-not-a-real-key";
  process.env.JARVIS_GATEWAY_TOKEN="fake-token-that-must-be-ignored-by-retired-service";
  let providerCalls=0;
  const oldFetch=globalThis.fetch;
  globalThis.fetch=async()=>{providerCalls++;throw Error("network must not be used");};
  const server=createServer((req,res)=>handler(req,res));
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  try{
    for(const method of ["GET","POST"]){
      const result=await oldFetch("http://127.0.0.1:"+server.address().port+"/v1/chat",{
        method,headers:{"content-type":"application/json"},
        ...(method==="POST"?{body:JSON.stringify({message:"Hola"})}:{})
      });
      assert.equal(result.status,410);
      assert.equal(result.headers.get("cache-control"),"no-store");
      assert.deepEqual(await result.json(),{error:"cloud_model_retired",mode:"offline_only"});
    }
    assert.equal(providerCalls,0);
  } finally {
    await new Promise(resolve=>server.close(resolve));
    globalThis.fetch=oldFetch;
    if(savedKey===undefined)delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY=savedKey;
    if(savedToken===undefined)delete process.env.JARVIS_GATEWAY_TOKEN;
    else process.env.JARVIS_GATEWAY_TOKEN=savedToken;
  }
});

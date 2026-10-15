import test from "node:test";
import assert from "node:assert/strict";
import {createServer} from "node:http";
import {readFileSync} from "node:fs";
import {createGateway} from "../gateway.mjs";

test("Vercel root maps the public stable chat path to a Node function",()=>{
  const config=JSON.parse(readFileSync(new URL("../vercel.json",import.meta.url),"utf8"));
  assert.ok(config.rewrites.some(x=>x.source==="/v1/chat"&&x.destination==="/api/chat"));
  const handler=readFileSync(new URL("../api/chat.mjs",import.meta.url),"utf8");
  assert.match(handler,/createGateway/);
  assert.match(handler,/OPENAI_API_KEY/);
  assert.match(handler,/JARVIS_GATEWAY_TOKEN/);
  assert.ok(!handler.includes("sk-proj-"));
});

test("gateway accepts Vercel-parsed request.body but validates exact schema",async()=>{
  let seen=0;
  const gateway=createGateway({
    apiKey:"sk-test-not-real",accessToken:"gateway-token-long-enough-for-tests-123",
    fetchImpl:async(_url,opt)=>{
      seen++;
      const body=JSON.parse(opt.body);
      assert.equal(body.input.at(-1).content,"texto del body parseado");
      return {ok:true,status:200,json:async()=>({
        output:[{type:"message",content:[{type:"output_text",text:"Bien."}]}]
      })};
    }
  });
  const srv=createServer((req,res)=>{
    req.body={message:"texto del body parseado",history:[]};
    return gateway(req,res);
  });
  await new Promise(done=>srv.listen(0,"127.0.0.1",done));
  try {
    const url="http://127.0.0.1:"+srv.address().port+"/api/chat";
    const r=await fetch(url,{
      method:"POST",
      headers:{"content-type":"application/json",
        "authorization":"Bearer gateway-token-long-enough-for-tests-123"},
      body:"{}"
    });
    assert.equal(r.status,200);
    assert.equal((await r.json()).text,"Bien.");
    assert.equal(seen,1);
  } finally {
    await new Promise(done=>srv.close(done));
  }
});

test("oversized pre-parsed Vercel body denied before calling provider",async()=>{
  let sent=0;
  const gateway=createGateway({
    apiKey:"sk-test-not-real",accessToken:"gateway-token-long-enough-for-tests-123",
    fetchImpl:async()=>{sent++;throw Error("must not call");}
  });
  const srv=createServer((req,res)=>{
    req.body={message:"ok",padding:"P".repeat(15000)};
    return gateway(req,res);
  });
  await new Promise(done=>srv.listen(0,"127.0.0.1",done));
  try {
    const response=await fetch(
      "http://127.0.0.1:"+srv.address().port+"/api/chat",
      {method:"POST",headers:{"content-type":"application/json",
       "authorization":"Bearer gateway-token-long-enough-for-tests-123"},body:"{}"});
    assert.equal(response.status,413);
    assert.equal(sent,0);
  } finally {
    await new Promise(done=>srv.close(done));
  }
});


test("serverless handler normalizes Vercel-rewritten request URLs; never treats GET as missing", async()=>{
  // Vercel may expose the pre-rewrite public URL and route query parameters
  // to the function, even though the matching function is /api/chat.
  const {default:handler}=await import("../api/chat.mjs");
  const beforeKey=process.env.OPENAI_API_KEY;
  const beforeToken=process.env.JARVIS_GATEWAY_TOKEN;
  process.env.OPENAI_API_KEY="sk-test-key-not-real";
  process.env.JARVIS_GATEWAY_TOKEN="fake-gateway-token-test-only-123456789012345";
  const server=createServer((req,res)=>{
    // Simulate a Vercel rewrite preserving its incoming URL/route query.
    req.url="/v1/chat?path=api%2Fchat";
    return handler(req,res);
  });
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  try {
    const url="http://127.0.0.1:"+server.address().port+"/v1/chat";
    const get=await fetch(url);
    assert.equal(get.status,405);
    assert.deepEqual(await get.json(),{error:"method_not_allowed"});
    const unauth=await fetch(url,{
      method:"POST",
      headers:{"content-type":"application/json"},
      body:JSON.stringify({message:"Hola"})
    });
    assert.equal(unauth.status,401);
    assert.deepEqual(await unauth.json(),{error:"unauthorized"});
  } finally {
    await new Promise(resolve=>server.close(resolve));
    if(beforeKey===undefined)delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY=beforeKey;
    if(beforeToken===undefined)delete process.env.JARVIS_GATEWAY_TOKEN;
    else process.env.JARVIS_GATEWAY_TOKEN=beforeToken;
  }
});

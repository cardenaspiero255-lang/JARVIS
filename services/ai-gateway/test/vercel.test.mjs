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

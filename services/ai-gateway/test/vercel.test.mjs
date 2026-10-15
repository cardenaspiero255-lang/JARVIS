import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

test("Vercel keeps the former public path mapped to a retired inert handler",()=>{
  const config=JSON.parse(readFileSync(new URL("../vercel.json",import.meta.url),"utf8"));
  assert.ok(config.rewrites.some(x=>x.source==="/v1/chat"&&x.destination==="/api/chat"));
  const handler=readFileSync(new URL("../api/chat.mjs",import.meta.url),"utf8");
  assert.match(handler,/cloud_model_retired/);
  assert.match(handler,/410/);
  assert.doesNotMatch(handler,/createGateway|api\.openai\.com|fetch\(|OPENAI_API_KEY|JARVIS_GATEWAY_TOKEN/);
});

test("Retired Vercel route does not route any request to a model",async()=>{
  const {default:handler}=await import("../api/chat.mjs");
  const events=[];
  const res={
    writeHead(status,headers){events.push({status,headers});},
    end(data){events.push(JSON.parse(data));}
  };
  handler({url:"/v1/chat?path=api%2Fchat",method:"POST",
      headers:{authorization:"Bearer old-token"}},res);
  assert.equal(events[0].status,410);
  assert.deepEqual(events[1],{error:"cloud_model_retired",mode:"offline_only"});
});

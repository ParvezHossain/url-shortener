// CI-only reputation fixture. Never package or configure this as a production scanner.
import { createServer } from "node:http";

createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    response.writeHead(200).end("ok");
    return;
  }
  if (request.method !== "POST" || request.url !== "/scan") {
    response.writeHead(404).end();
    return;
  }
  let body = "";
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 8192) {
      response.writeHead(413).end();
      return;
    }
  }
  try {
    const url = new URL(JSON.parse(body).url);
    // Only the public example domain used by browser fixtures is accepted.
    const verdict = url.hostname === "example.com" && url.protocol === "https:"
      ? "SAFE" : "MALICIOUS";
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ verdict }));
  } catch {
    response.writeHead(400).end();
  }
}).listen(8081, "0.0.0.0");

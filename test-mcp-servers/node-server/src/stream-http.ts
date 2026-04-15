import { createServer as createHttpServer } from "node:http";

import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

import { createServer } from "./server.js";

const host = process.env.HOST ?? "127.0.0.1";
const port = Number(process.env.PORT ?? "3001");

const httpServer = createHttpServer(async (req, res) => {
  if (!req.url?.startsWith("/mcp")) {
    res.writeHead(404).end("Not found");
    return;
  }

  if (req.method !== "GET" && req.method !== "POST" && req.method !== "DELETE") {
    res.writeHead(405).end("Method not allowed");
    return;
  }

  const server = createServer();
  const transport = new StreamableHTTPServerTransport();

  await server.connect(transport as Parameters<typeof server.connect>[0]);
  await transport.handleRequest(req, res);
});

httpServer.listen(port, host, () => {
  console.log(`Streamable HTTP MCP server listening on http://${host}:${port}/mcp`);
});

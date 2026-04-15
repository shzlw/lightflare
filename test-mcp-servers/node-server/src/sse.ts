import { createServer as createHttpServer } from "node:http";

import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";

import { createServer } from "./server.js";

const host = process.env.HOST ?? "127.0.0.1";
const port = Number(process.env.PORT ?? "3002");

type SseSession = {
  server: ReturnType<typeof createServer>;
  transport: SSEServerTransport;
};

const sessions = new Map<string, SseSession>();

const httpServer = createHttpServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://${req.headers.host ?? `${host}:${port}`}`);

  if (req.method === "GET" && url.pathname === "/sse") {
    const server = createServer();
    const transport = new SSEServerTransport("/messages", res);

    transport.onclose = () => {
      sessions.delete(transport.sessionId);
    };

    await server.connect(transport as Parameters<typeof server.connect>[0]);
    sessions.set(transport.sessionId, { server, transport });
    return;
  }

  if (req.method === "POST" && url.pathname === "/messages") {
    const sessionId = url.searchParams.get("sessionId");

    if (!sessionId) {
      res.writeHead(400).end("Missing sessionId");
      return;
    }

    const session = sessions.get(sessionId);

    if (!session) {
      res.writeHead(404).end("Unknown sessionId");
      return;
    }

    await session.transport.handlePostMessage(req, res);
    return;
  }

  res.writeHead(404).end("Not found");
});

httpServer.listen(port, host, () => {
  console.log(`SSE MCP server listening on http://${host}:${port}/sse`);
});

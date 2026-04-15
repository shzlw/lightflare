import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

export function createServer() {
  const server = new McpServer({
    name: "my-mcp-server",
    version: "1.0.0",
  });

  server.registerTool(
    "hello",
    {
      description: "Says hello to a name",
      inputSchema: { name: z.string().describe("Name to greet") },
    },
    async ({ name }) => ({
      content: [{ type: "text", text: `Hello, ${name}!` }],
    })
  );

  server.registerTool(
    "get-users",
    {
      description: "Fetches all users from the API",
      inputSchema: {},
    },
    async () => {
      const response = await fetch("https://jsonplaceholder.typicode.com/users");
      const users = await response.json();

      return {
        content: [{ type: "text", text: JSON.stringify(users, null, 2) }],
      };
    }
  );

  server.registerResource(
    "config",
    "app://config",
    {
      description: "Current app configuration",
      mimeType: "application/json",
    },
    async () => ({
      contents: [
        {
          uri: "app://config",
          text: JSON.stringify({ env: "development", version: "1.0.0" }, null, 2),
        },
      ],
    })
  );

  server.registerPrompt(
    "code-review",
    {
      description: "Reviews code and suggests improvements",
      argsSchema: { code: z.string().describe("Code to review") },
    },
    async ({ code }) => ({
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text: `Please review this code and suggest improvements:\n\n${code}`,
          },
        },
      ],
    })
  );

  return server;
}

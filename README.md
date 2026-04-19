# Lightflare

[![Docker Pulls](https://img.shields.io/docker/pulls/shzlwio/lightflare?logo=docker&label=docker%20pulls)](https://hub.docker.com/r/shzlwio/lightflare)

Lightflare is a self-hosted agent platform for team AI work.

## Why Lightflare

- **Self-hosted.** Runs on your own infrastructure and hardware.
- **Persistent storage.** Conversations, memory, schedules, and state are stored in your database.
- **Browser UI.** No local software to install - users access it from any browser.
- **Flexible memory.** Supports both user-scoped and organization-shared memory.
- **Centralized tools.** Server-side integrations with managed credentials and access policy.
- **Private network access.** Connect internal tools without exposing them to a cloud provider.
- **LLM choice.** Use local or external LLM providers behind a single control plane.
- **Open source.** Audit and adapt the full codebase to fit your team's needs.
- **Safe by default.** Runs approved server-side tools - no direct writes to user machines.

## Example Use Cases

- Use Lightflare as a shared Slack assistant that can answer questions and run approved tools from team channels.
- Schedule recurring checks, such as reviewing open tasks, summarizing recent activity, or sending reminders.
- Run approved internal tools from one server-side environment instead of each user's local machine.

## License

Lightflare is released under the MIT License. See [LICENSE](LICENSE) for the full license text.

Manage applications as reusable runnable assets.

Use this tool to inspect, create, update, delete, run, and manage triggers for applications.

Guidance:
- Prefer `upsert` when the user wants to create or update application metadata.
- Use `create-trigger`, `update-trigger`, and `delete-trigger` for application version triggers.
- Use `run` to execute immediately.
- Use `runs` and `run-steps` to inspect execution history.

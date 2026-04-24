Manage triggers for an application version.

Actions:
- `create`
- `update`
- `delete`

Required for create/update:
- `application_id`
- `version_id`

Trigger fields:
- `trigger_type`: manual, webhook, or cron
- `start_step_id`
- `config_json`

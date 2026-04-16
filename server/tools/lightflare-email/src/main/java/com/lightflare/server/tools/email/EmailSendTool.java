package com.lightflare.server.tools.email;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lightflare.tools.email", name = "enabled", havingValue = "true")
public class EmailSendTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("send-email")
            .description("Send an email message to a recipient.")
            .category("communication")
            .integrationId("email")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("to")
                            .type("string")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("subject")
                            .type("string")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("text")
                            .type("string")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("from")
                            .type("string")
                            .required(false)
                            .build()
            ))
            .build();

    private final EmailService emailService;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String to = getStringArgument(arguments, "to");
        String subject = getStringArgument(arguments, "subject");
        String text = getStringArgument(arguments, "text");
        String from = getOptionalStringArgument(arguments, "from");

        if (to == null) {
            return ToolResult.failure("Missing required argument: to");
        }
        if (subject == null) {
            return ToolResult.failure("Missing required argument: subject");
        }
        if (text == null) {
            return ToolResult.failure("Missing required argument: text");
        }

        try {
            boolean success = emailService.send(to, subject, text, from);
            if (success) {
                return ToolResult.success("Email sent successfully to: " + to);
            } else {
                return ToolResult.failure("Failed to send email to: " + to);
            }
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() != null
                    ? e.getMessage()
                    : "Failed to send email");
        }
    }

    private String getStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}

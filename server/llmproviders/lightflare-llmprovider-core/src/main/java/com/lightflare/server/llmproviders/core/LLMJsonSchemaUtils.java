package com.lightflare.server.llmproviders.core;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

public final class LLMJsonSchemaUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SchemaGenerator SCHEMA_GENERATOR = new SchemaGenerator(buildConfig());

    private LLMJsonSchemaUtils() {
    }

    public static synchronized ObjectNode generateSchema(Class<?> responseType) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        return SCHEMA_GENERATOR.generateSchema(responseType);
    }

    public static String generateSchemaString(Class<?> responseType) {
        return generateSchema(responseType).toPrettyString();
    }

    public static Map<String, Object> structuredResponseFormat(Class<?> responseType) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        return Map.of(
                "type", "json_schema",
                "name", schemaName(responseType),
                "schema", generateSchemaMap(responseType),
                "strict", true
        );
    }

    public static ObjectNode structuredOutputSchema(Class<?> responseType) {
        return generateSchema(responseType);
    }

    private static Object generateSchemaMap(Class<?> responseType) {
        try {
            return OBJECT_MAPPER.readValue(generateSchemaString(responseType), Object.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to build structured response schema", e);
        }
    }

    private static String schemaName(Class<?> responseType) {
        return responseType.getSimpleName()
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .toLowerCase();
    }

    private static SchemaGeneratorConfig buildConfig() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                OBJECT_MAPPER,
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON
        );

        configBuilder.with(new JacksonModule(
                JacksonOption.RESPECT_JSONPROPERTY_ORDER,
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY
        ));
        configBuilder.with(
                Option.FLATTENED_ENUMS,
                Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT,
                Option.NULLABLE_FIELDS_BY_DEFAULT
        );
        configBuilder.forFields().withRequiredCheck(field -> true);

        return configBuilder.build();
    }
}

package com.lightflare.server.config;

import com.lightflare.server.memory.EmbeddingVector;
import java.sql.SQLException;
import java.util.List;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

@Configuration
public class JdbcVectorConvertersConfig extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return List.of(
                StringToEmbeddingVectorConverter.INSTANCE,
                PgObjectToEmbeddingVectorConverter.INSTANCE,
                EmbeddingVectorToPgObjectConverter.INSTANCE
        );
    }

    @ReadingConverter
    enum StringToEmbeddingVectorConverter implements Converter<String, EmbeddingVector> {
        INSTANCE;

        @Override
        public EmbeddingVector convert(String source) {
            return EmbeddingVector.of(source);
        }
    }

    @ReadingConverter
    enum PgObjectToEmbeddingVectorConverter implements Converter<PGobject, EmbeddingVector> {
        INSTANCE;

        @Override
        public EmbeddingVector convert(PGobject source) {
            return source == null ? null : EmbeddingVector.of(source.getValue());
        }
    }

    @WritingConverter
    enum EmbeddingVectorToPgObjectConverter implements Converter<EmbeddingVector, PGobject> {
        INSTANCE;

        @Override
        public PGobject convert(EmbeddingVector source) {
            if (source == null) {
                return null;
            }

            PGobject pgObject = new PGobject();
            pgObject.setType("vector");
            try {
                pgObject.setValue(source.value());
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Failed to convert embedding vector", ex);
            }
            return pgObject;
        }
    }
}

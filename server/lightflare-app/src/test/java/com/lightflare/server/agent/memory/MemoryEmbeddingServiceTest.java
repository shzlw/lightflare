package com.lightflare.server.agent.memory;

import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.llmproviders.core.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryEmbeddingServiceTest {

    @Test
    void shouldSkipEmbeddingGenerationWhenProviderDoesNotSupportEmbeddings() {
        MemoryEmbeddingService service = new MemoryEmbeddingService(new ResponseOnlyProvider(), null, null);

        String embedding = service.createQueryEmbedding("hello world");

        assertNull(embedding);
    }

    @Test
    void shouldCreateEmbeddingWhenProviderSupportsEmbeddings() {
        MemoryEmbeddingService service = new MemoryEmbeddingService(new EmbeddingCapableProvider(), null, null);

        String embedding = service.createQueryEmbedding("hello world");

        assertEquals("[1.0,2.0,3.0]", embedding);
    }

    @Test
    void defaultProviderEmbeddingMethodShouldBeUnsupported() {
        LLMProvider provider = new ResponseOnlyProvider();

        assertThrows(UnsupportedOperationException.class, () -> provider.getEmbeddings("hello world"));
    }

    private static class ResponseOnlyProvider implements LLMProvider {

        @Override
        public LLMResponse<LLMGetResponse> getResponse(String input) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }

    private static final class EmbeddingCapableProvider extends ResponseOnlyProvider {

        @Override
        public boolean supportsEmbeddings() {
            return true;
        }

        @Override
        public List<Float> getEmbeddings(String content) {
            return List.of(1.0f, 2.0f, 3.0f);
        }
    }
}

package com.lightflare.server.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkingServiceTest {

    private final DocumentChunkingService service = new DocumentChunkingService();

    @Test
    void keepsSmallDocumentsInSingleChunk() {
        List<String> chunks = service.splitIntoChunks("First paragraph.\n\nSecond paragraph.");

        assertEquals(List.of("First paragraph.\n\nSecond paragraph."), chunks);
    }

    @Test
    void splitsLargeParagraphsWithOverlap() {
        String content = "a".repeat(2_100);

        List<String> chunks = service.splitIntoChunks(content);

        assertEquals(2, chunks.size());
        assertEquals(2_000, chunks.getFirst().length());
        assertTrue(chunks.getLast().startsWith("a".repeat(250)));
    }
}

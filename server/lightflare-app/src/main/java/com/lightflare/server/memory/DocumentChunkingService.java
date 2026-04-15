package com.lightflare.server.memory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentChunkingService {

    private static final int MAX_CHUNK_CHARACTERS = 2_000;
    private static final int OVERLAP_CHARACTERS = 250;

    public List<String> splitIntoChunks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String normalizedParagraph = paragraph.trim();
            if (!StringUtils.hasText(normalizedParagraph)) {
                continue;
            }

            if (normalizedParagraph.length() > MAX_CHUNK_CHARACTERS) {
                flushCurrentChunk(chunks, currentChunk);
                splitLargeParagraph(chunks, normalizedParagraph);
                continue;
            }

            int additionalLength = currentChunk.isEmpty()
                    ? normalizedParagraph.length()
                    : normalizedParagraph.length() + 2;
            if (!currentChunk.isEmpty() && currentChunk.length() + additionalLength > MAX_CHUNK_CHARACTERS) {
                flushCurrentChunk(chunks, currentChunk);
            }
            if (!currentChunk.isEmpty()) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(normalizedParagraph);
        }

        flushCurrentChunk(chunks, currentChunk);
        return chunks;
    }

    private void splitLargeParagraph(List<String> chunks, String paragraph) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARACTERS, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            if (end == paragraph.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARACTERS, start + 1);
        }
    }

    private void flushCurrentChunk(List<String> chunks, StringBuilder currentChunk) {
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
            currentChunk.setLength(0);
        }
    }
}

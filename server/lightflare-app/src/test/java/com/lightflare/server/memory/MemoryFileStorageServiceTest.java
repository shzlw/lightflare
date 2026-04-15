package com.lightflare.server.memory;

import com.lightflare.server.config.MemoryProperties;
import com.lightflare.server.document.PdfDocumentTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesTextFileWithContent() {
        MemoryFileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello from text".getBytes(StandardCharsets.UTF_8)
        );

        MemoryFileStorageService.StoredMemoryFile storedFile = service.storeFile(file);

        assertEquals("notes.txt", storedFile.originalFileName());
        assertEquals("text/plain", storedFile.contentType());
        assertEquals("hello from text", storedFile.extractedContent());
    }

    @Test
    void storesPdfFileWithExtractedContent() throws IOException {
        MemoryFileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                pdfBytes("hello from pdf")
        );

        MemoryFileStorageService.StoredMemoryFile storedFile = service.storeFile(file);

        assertEquals("document.pdf", storedFile.originalFileName());
        assertEquals("application/pdf", storedFile.contentType());
        assertTrue(storedFile.extractedContent().contains("hello from pdf"));
        assertTrue(storedFile.storedPath().endsWith("-document.pdf"));
    }

    private MemoryFileStorageService service() {
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setUploadDir(tempDir.toString());
        return new MemoryFileStorageService(memoryProperties, new PdfDocumentTextExtractor());
    }

    private byte[] pdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}

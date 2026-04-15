package com.lightflare.server.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsPdfText() throws IOException {
        Path pdfPath = writePdf("hello from pdfbox");

        String result = new PdfDocumentTextExtractor().extractText(pdfPath);

        assertTrue(result.contains("hello from pdfbox"));
    }

    private Path writePdf(String text) throws IOException {
        Path pdfPath = tempDir.resolve("document.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(pdfPath.toFile());
        }
        return pdfPath;
    }
}

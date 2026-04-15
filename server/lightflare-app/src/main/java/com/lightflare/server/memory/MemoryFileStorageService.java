package com.lightflare.server.memory;

import com.lightflare.server.config.MemoryProperties;
import com.lightflare.server.document.PdfDocumentTextExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MemoryFileStorageService {

    private static final Set<String> TEXT_FILE_EXTENSIONS = Set.of(
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "log"
    );

    private final MemoryProperties memoryProperties;
    private final PdfDocumentTextExtractor pdfDocumentTextExtractor;

    public StoredMemoryFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? Path.of(file.getOriginalFilename()).getFileName().toString()
                : "upload.txt";
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType().trim() : null;
        FileType fileType = resolveFileType(originalFilename, contentType);

        try {
            byte[] bytes = file.getBytes();
            Path storageDir = Path.of(memoryProperties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(storageDir);

            String storedFileName = UUID.randomUUID() + "-" + sanitizeFileName(originalFilename);
            Path storedPath = storageDir.resolve(storedFileName);
            Files.write(storedPath, bytes, StandardOpenOption.CREATE_NEW);

            String content = extractContent(fileType, storedPath, bytes);
            if (!StringUtils.hasText(content)) {
                throw new IllegalArgumentException("Uploaded file does not contain readable text content");
            }

            return new StoredMemoryFile(
                    originalFilename,
                    storedPath.toString(),
                    (long) bytes.length,
                    contentType,
                    content
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store uploaded file", exception);
        }
    }

    private FileType resolveFileType(String filename, String contentType) {
        String extension = "";
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator >= 0 && extensionSeparator < filename.length() - 1) {
            extension = filename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        }

        boolean isTextContentType = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/");
        boolean isKnownTextExtension = TEXT_FILE_EXTENSIONS.contains(extension);
        if (isTextContentType || isKnownTextExtension) {
            return FileType.TEXT;
        }

        boolean isPdfContentType = contentType != null && "application/pdf".equalsIgnoreCase(contentType);
        if (isPdfContentType || "pdf".equals(extension)) {
            return FileType.PDF;
        }

        throw new IllegalArgumentException("Only text and PDF file uploads are supported right now");
    }

    private String extractContent(FileType fileType, Path storedPath, byte[] bytes) {
        return switch (fileType) {
            case TEXT -> new String(bytes, StandardCharsets.UTF_8);
            case PDF -> pdfDocumentTextExtractor.extractText(storedPath);
        };
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private enum FileType {
        TEXT,
        PDF
    }

    public record StoredMemoryFile(
            String originalFileName,
            String storedPath,
            Long size,
            String contentType,
            String extractedContent
    ) {
    }
}

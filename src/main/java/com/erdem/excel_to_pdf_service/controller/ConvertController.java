package com.erdem.excel_to_pdf_service.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/convert")
public class ConvertController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertController.class);
    
    @Autowired
    private DocumentConverter documentConverter;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> convertExcelToPdf(@RequestParam("file") MultipartFile file) throws Exception {
        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                LOGGER.error("No file provided or file is empty");
                return ResponseEntity.badRequest().build();
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && 
                !originalFilename.toLowerCase().endsWith(".xls"))) {
                LOGGER.error("Invalid file type: {}", originalFilename);
                return ResponseEntity.badRequest().build();
            }

            LOGGER.info("Converting file: {} (size: {} bytes)", originalFilename, file.getSize());

            // Debug için /tmp klasörünü kullan, production'da temp klasör oluştur
            String debugMode = System.getenv("DEBUG_MODE");
            Path tempDir = "true".equals(debugMode) ? 
                Paths.get("/tmp") : Files.createTempDirectory("excel-convert");

            
            String originalFilenameForName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            String ext = originalFilenameForName.contains(".") ? originalFilenameForName.substring(originalFilenameForName.lastIndexOf('.')) : ".xlsx";
            String baseName = (originalFilenameForName.contains(".") ? originalFilenameForName.substring(0, originalFilenameForName.lastIndexOf('.')) : originalFilenameForName) + "-" + timestamp;
            Path inputPath = tempDir.resolve(baseName + ext);
            file.transferTo(inputPath);

            // Output path'i belirle
            Path outputPath = inputPath.getParent().resolve(baseName + ".pdf");

            LOGGER.info("Input file: {}, Output file: {}", inputPath, outputPath);

            // JODConverter kullanarak dönüştürme işlemini gerçekleştir
            try {
                LOGGER.info("Starting document conversion using JODConverter");
                documentConverter.convert(inputPath.toFile()).to(outputPath.toFile()).execute();
                LOGGER.info("JODConverter conversion completed successfully");
            } catch (OfficeException e) {
                LOGGER.error("JODConverter error: {}", e.getMessage(), e);
                throw new RuntimeException("Document conversion failed: " + e.getMessage(), e);
            }

            // Output dosyasının oluştuğunu kontrol et
            if (!Files.exists(outputPath)) {
                LOGGER.error("Output PDF file was not created: {}", outputPath);
                throw new RuntimeException("Output PDF file was not created");
            }

            LOGGER.info("Conversion successful. Output file: {}", outputPath);

            // PDF'i response olarak döndür
            FileSystemResource resource = new FileSystemResource(outputPath.toFile());
            
            // Cleanup: Debug modunda dosyaları sakla, production'da sil
            if (!"true".equals(debugMode)) {
                try {
                    Files.deleteIfExists(inputPath);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete temporary input file: {}", e.getMessage());
                }
            } else {
                LOGGER.info("Debug mode: Files preserved in /tmp for inspection");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
                    
        } catch (MultipartException e) {
            LOGGER.error("Multipart request error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Error during conversion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Handle HttpMediaTypeNotSupportedException
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        LOGGER.error("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body("{\"error\": \"Unsupported media type. Please send a multipart/form-data request with a 'file' parameter.\"}");
    }
}

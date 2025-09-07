package com.erdem.excel_to_pdf_service.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.erdem.excel_to_pdf_service.service.ExcelToPdfService;

@RestController
@RequestMapping("/api/convert")
public class ExcelToPdfController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelToPdfController.class);

    private final ExcelToPdfService excelToPdfService;

    @Autowired
    public ExcelToPdfController(ExcelToPdfService excelToPdfService) {
        this.excelToPdfService = excelToPdfService;
    }

    @PostMapping
    public ResponseEntity<?> convertExcelToPdf(@RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            if (file == null) {
                LOGGER.error("No file parameter provided in multipart request");
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"No file parameter provided. Please ensure you're sending a multipart request with a 'file' parameter.\"}");
            }

            // file parametresi boşsa hata dön
            if (file.isEmpty()) {
                LOGGER.error("Empty file provided");
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"File is empty. Please provide a valid Excel file.\"}");
            }

            // Validate file type
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && 
                !originalFilename.toLowerCase().endsWith(".xls"))) {
                LOGGER.error("Invalid file type: {}", originalFilename);
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"Invalid file type. Please provide an Excel file (.xlsx or .xls).\"}");
            }

            LOGGER.info("Received file: {} (size: {} bytes)", originalFilename, file.getSize());
            
            byte[] pdfContent = excelToPdfService.convertToPdf(file.getInputStream());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
                    
        } catch (MultipartException e) {
            LOGGER.error("Multipart request error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Invalid multipart request. Please ensure you're sending a proper multipart/form-data request with a 'file' parameter.\"}");
        } catch (Exception e) {
            LOGGER.error("Error during conversion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal server error during conversion: " + e.getMessage() + "\"}");
        }
    }
    // Handle HttpMediaTypeNotSupportedException
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        LOGGER.error("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body("{\"error\": \"Unsupported media type. Please send a multipart/form-data request with a 'file' parameter. Expected: multipart/form-data\"}");
    }
}

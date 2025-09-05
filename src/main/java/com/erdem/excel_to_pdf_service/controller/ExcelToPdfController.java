package com.erdem.excel_to_pdf_service.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
    public ResponseEntity<byte[]> convertExcelToPdf(@RequestParam("file") MultipartFile file) {
        try {
            LOGGER.info("Received file: {}", file.getOriginalFilename());
            byte[] pdfContent = excelToPdfService.convertToPdf(file.getInputStream());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (Exception e) {
            LOGGER.error("Error during conversion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

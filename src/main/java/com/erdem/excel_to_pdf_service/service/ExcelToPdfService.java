package com.erdem.excel_to_pdf_service.service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ExcelToPdfService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelToPdfService.class);

    @Value("${debug:false}")
    private boolean debug;

    @Value("${app.output.directory:outputs}")
    private String outputDirectory;

    // Cached BaseFont loaded once; if null, fall back to default Helvetica
    private com.lowagie.text.pdf.BaseFont unicodeBaseFont;

    // Explicit no-args constructor to initialize fonts eagerly and avoid "unused" warnings
    public ExcelToPdfService() {
        initFonts();
    }

    /**
     * Initializes the cached Unicode BaseFont once at application startup.
     */
    private void initFonts() {
        try (java.io.InputStream is = this.getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is != null) {
                byte[] fontBytes = is.readAllBytes();
                unicodeBaseFont = com.lowagie.text.pdf.BaseFont.createFont(
                        "DejaVuSans.ttf",
                        com.lowagie.text.pdf.BaseFont.IDENTITY_H,
                        com.lowagie.text.pdf.BaseFont.EMBEDDED,
                        true,
                        fontBytes,
                        null
                );
                LOGGER.info("Loaded Unicode font from classpath: DejaVuSans.ttf");
                return;
            }
        } catch (java.io.IOException | com.lowagie.text.DocumentException e) {
            LOGGER.debug("Classpath Unicode font not available: {}", e.getMessage());
        }
        
        // Fast fallback: try a few known system font locations (no directory scanning)
        String[] candidatePaths = new String[] {
                "/Library/Fonts/Arial Unicode.ttf",
                "/Library/Fonts/Arial Unicode MS.ttf",
                "/System/Library/Fonts/Supplemental/NotoSans-Regular.ttf",
                "/System/Library/Fonts/Supplemental/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/opentype/noto/NotoSans-Regular.ttf"
        };
        for (String path : candidatePaths) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(path);
                if (java.nio.file.Files.exists(p)) {
                    unicodeBaseFont = com.lowagie.text.pdf.BaseFont.createFont(
                            path,
                            com.lowagie.text.pdf.BaseFont.IDENTITY_H,
                            com.lowagie.text.pdf.BaseFont.EMBEDDED,
                            true,
                            null,
                            null
                    );
                    LOGGER.info("Loaded Unicode font from system path: {}", path);
                    return;
                }
            } catch (com.lowagie.text.DocumentException | java.io.IOException e) {
                LOGGER.debug("Failed to load system Unicode font {}: {}", path, e.getMessage());
            }
        }
        
        // If none found, keep null to use Helvetica. Turkish glyphs may not render until a Unicode TTF is provided.
        LOGGER.warn("No Unicode TTF found. Add DejaVuSans.ttf to src/main/resources/fonts/ for full Turkish support.");
    }

    public byte[] convertToPdf(InputStream excelInputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(excelInputStream);
             ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.getSheetAt(0);
            
            // Find the actual number of columns by checking all rows
            int maxColumns = findMaxColumns(sheet);
            LOGGER.info("Detected {} columns in Excel sheet", maxColumns);
            
            try (Document document = new Document()) {
                PdfWriter.getInstance(document, pdfOutputStream);
                document.open();

                // Process each column
                for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                    LOGGER.info("Processing column {}", colIndex);
                    
                    // Process all rows for this column
                    boolean hasContent = false;
                    for (Row row : sheet) {
                        if (row != null) {
                            Cell cell = row.getCell(colIndex);
                            String cellValue = getCellValue(cell);
                            
                            if (!cellValue.isEmpty()) {
                                hasContent = true;
                                Paragraph cellParagraph = createStyledParagraph(cell, cellValue, workbook);
                                cellParagraph.setSpacingAfter(5f);
                                document.add(cellParagraph);
                            }
                        }
                    }
                    
                    // Add spacing between columns
                    if (hasContent) {
                        Paragraph spacing = new Paragraph(" ");
                        spacing.setSpacingAfter(20f);
                        document.add(spacing);
                    }
                    
                    // Add page break after each column (except the last one)
                    if (colIndex < maxColumns - 1) {
                        document.newPage();
                    }
                }
            }

            byte[] pdfBytes = pdfOutputStream.toByteArray();

            if (debug) {
                saveDebugOutput(pdfBytes);
            }

            return pdfBytes;
        }
    }

    /**
     * Find the maximum number of columns across all rows in the sheet
     */
    private int findMaxColumns(Sheet sheet) {
        int maxColumns = 0;
        for (Row row : sheet) {
            if (row != null) {
                int lastCellNum = row.getLastCellNum();
                if (lastCellNum > maxColumns) {
                    maxColumns = lastCellNum;
                }
            }
        }
        return Math.max(maxColumns, 1); // Ensure at least 1 column
    }

    private void saveDebugOutput(byte[] pdfContent) {
        try {
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            Path pdfPath = outputPath.resolve("debug_output.pdf");
            try (FileOutputStream fos = new FileOutputStream(pdfPath.toFile())) {
                fos.write(pdfContent);
                fos.flush();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save debug output: {}", e.getMessage(), e);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Custom date formatting to get DD/MM/YYYY format
                    java.util.Date date = cell.getDateCellValue();
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    yield dateFormat.format(date);
                } else {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                // For formula cells, check if it's a date formula
                if (DateUtil.isCellDateFormatted(cell)) {
                    java.util.Date date = cell.getDateCellValue();
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    yield dateFormat.format(date);
                } else {
                    // Use Excel's data formatter for non-date formulas
                    org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
                    yield formatter.formatCellValue(cell);
                }
            }
            default -> "";
        };
    }

    /**
     * Create a styled Paragraph from an Excel cell with proper formatting
     */
    private Paragraph createStyledParagraph(Cell cell, String value, Workbook workbook) {
        int alignment = Element.ALIGN_LEFT;
        com.lowagie.text.pdf.RGBColor textColor = null;
        com.lowagie.text.pdf.RGBColor backgroundColor = null;
        int resolvedFontStyle = com.lowagie.text.Font.NORMAL;
        short resolvedFontSize = 12;
        
        if (cell != null) {
            var cellStyle = cell.getCellStyle();
            if (cellStyle != null && cellStyle instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle xssfCellStyle) {
                
                // Handle alignment
                var align = cellStyle.getAlignment();
                switch (align) {
                    case CENTER:
                        alignment = Element.ALIGN_CENTER;
                        break;
                    case RIGHT:
                        alignment = Element.ALIGN_RIGHT;
                        break;
                    case JUSTIFY:
                        alignment = Element.ALIGN_JUSTIFIED;
                        break;
                    case LEFT:
                    default:
                        alignment = Element.ALIGN_LEFT;
                        break;
                }
                
                // Handle background color
                try {
                    var fillColor = cellStyle.getFillForegroundColorColor();
                    if (fillColor instanceof org.apache.poi.xssf.usermodel.XSSFColor xssfColor && xssfColor.getRGB() != null) {
                        byte[] rgb = xssfColor.getRGB();
                        backgroundColor = new com.lowagie.text.pdf.RGBColor(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not extract background color: {}", e.getMessage());
                }
                
                // Handle font styling and text color
                try {
                    int fontIdx = xssfCellStyle.getFontIndex();
                    var poiFont = workbook.getFontAt(fontIdx);
                    
                    // Set font style
                    resolvedFontStyle = com.lowagie.text.Font.NORMAL;
                    if (poiFont.getBold()) {
                        resolvedFontStyle |= com.lowagie.text.Font.BOLD;
                    }
                    if (poiFont.getItalic()) {
                        resolvedFontStyle |= com.lowagie.text.Font.ITALIC;
                    }
                    
                    // Set font size
                    short fontSize = poiFont.getFontHeightInPoints();
                    if (fontSize > 0) {
                        resolvedFontSize = fontSize;
                    }
                    
                    // Handle text color - use the correct POI API
                    if (poiFont instanceof org.apache.poi.xssf.usermodel.XSSFFont xssfFont) {
                        org.apache.poi.xssf.usermodel.XSSFColor fontColor = xssfFont.getXSSFColor();
                        if (fontColor != null && fontColor.getRGB() != null) {
                            byte[] rgb = fontColor.getRGB();
                            textColor = new com.lowagie.text.pdf.RGBColor(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.debug("Could not extract font styling: {}", e.getMessage());
                }
            }
        }
        
        // Lightweight font selection: reuse cached Unicode BaseFont if present; otherwise Helvetica
        com.lowagie.text.Font font = (unicodeBaseFont != null)
                ? new com.lowagie.text.Font(unicodeBaseFont, resolvedFontSize, resolvedFontStyle, textColor)
                : new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, resolvedFontSize, resolvedFontStyle, textColor);
        
        // Create the paragraph
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setAlignment(alignment);
        
        // Apply background color using a chunk approach
        if (backgroundColor != null) {
            com.lowagie.text.Chunk chunk = new com.lowagie.text.Chunk(value, font);
            chunk.setBackground(backgroundColor);
            Paragraph p = new Paragraph();
            p.setAlignment(alignment);
            p.add(chunk);
            return p;
        }
        
        return paragraph;
    }
}

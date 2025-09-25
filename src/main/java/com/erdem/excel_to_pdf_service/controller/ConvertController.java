package com.erdem.excel_to_pdf_service.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/convert")
public class ConvertController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertController.class);
    
    // Thread pool for parallel processing
    private ExecutorService executorService;
    
    @Autowired(required = false)
    private DocumentConverter documentConverter;

    @PostConstruct
    public void initThreadPool() {
        // CPU optimized thread pool - system çekirdek sayısının 2 katı
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.max(4, availableProcessors * 2);
        LOGGER.info("Initializing thread pool with {} threads for parallel processing", threadPoolSize);
        executorService = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    @PreDestroy
    public void shutdownThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            LOGGER.info("Shutting down thread pool...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> convertExcelToPdf(@RequestParam("file") MultipartFile file) throws Exception {
        // Check if JODConverter is available
        if (documentConverter == null) {
            LOGGER.error("Excel conversion not available - LibreOffice not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Error", "Excel conversion requires LibreOffice which is not available in this environment")
                    .build();
        }
        
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

            // Local development için outputs klasörünü kullan, production'da temp klasör oluştur
            String debugMode = System.getenv("DEBUG_MODE");
            Path tempDir;
            
            // Check if running locally (outputs folder exists in current directory)
            Path localOutputsDir = Paths.get("outputs");
            if (Files.exists(localOutputsDir) && Files.isDirectory(localOutputsDir)) {
                tempDir = localOutputsDir;
                LOGGER.info("Using local outputs directory for file storage: {}", tempDir.toAbsolutePath());
            } else if ("true".equals(debugMode)) {
                tempDir = Paths.get("/tmp/outputs");
                Files.createDirectories(tempDir);
                LOGGER.info("Using debug outputs directory: {}", tempDir.toAbsolutePath());
            } else {
                tempDir = Files.createTempDirectory("excel-convert");
                LOGGER.info("Using temporary directory: {}", tempDir.toAbsolutePath());
            }

            
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
            
            // Cleanup: Local development'ta ve debug modunda dosyaları sakla, production'da sil
            boolean isDocker = Files.exists(Paths.get("/.dockerenv"));
            boolean isLocalDevelopment = !isDocker && Files.exists(Paths.get("outputs")) && Files.isDirectory(Paths.get("outputs"));
            boolean isDebugMode = "true".equals(debugMode);
            
            if (!isLocalDevelopment && !isDebugMode) {
                try {
                    Files.deleteIfExists(inputPath);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete temporary input file: {}", e.getMessage());
                }
            } else {
                if (isLocalDevelopment && !isDocker) {
                    LOGGER.info("Local development mode: Excel and PDF files preserved in outputs folder for inspection");
                } else if (isDebugMode) {
                    // Debug mode: PDF'i /tmp/outputs'a kopyala
                    try {
                        Path debugDir = Paths.get("/tmp/outputs");
                        Files.createDirectories(debugDir);
                        Path debugPdfPath = debugDir.resolve("excel-to-pdf-" + System.currentTimeMillis() + ".pdf");
                        Files.copy(outputPath, debugPdfPath);
                        LOGGER.info("Debug mode: PDF copied to {} for inspection", debugPdfPath);
                    } catch (IOException e) {
                        LOGGER.warn("Could not copy PDF to debug directory: {}", e.getMessage());
                    }
                    LOGGER.info("Debug mode: Files preserved in /tmp/outputs for inspection");
                }
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

    // HTML to PDF using Chrome Headless
    @PostMapping(value = "/html-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> convertHtmlToPdf(@RequestParam("file") MultipartFile file) throws Exception {
        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                LOGGER.error("No HTML file provided or file is empty");
                return ResponseEntity.badRequest().build();
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".html") && 
                !originalFilename.toLowerCase().endsWith(".htm"))) {
                LOGGER.error("Invalid file type: {}. Only HTML files are supported.", originalFilename);
                return ResponseEntity.badRequest().build();
            }

            LOGGER.info("Converting HTML file: {} (size: {} bytes)", originalFilename, file.getSize());

            // Local development için outputs klasörünü kullan, production'da temp klasör oluştur
            String debugMode = System.getenv("DEBUG_MODE");
            Path tempDir;
            
            // Check if running locally (outputs folder exists in current directory)
            Path localOutputsDir = Paths.get("outputs");
            if (Files.exists(localOutputsDir) && Files.isDirectory(localOutputsDir)) {
                tempDir = localOutputsDir;
                LOGGER.info("Using local outputs directory for file storage: {}", tempDir.toAbsolutePath());
            } else if ("true".equals(debugMode)) {
                tempDir = Paths.get("/tmp/outputs");
                Files.createDirectories(tempDir);
                LOGGER.info("Using debug outputs directory: {}", tempDir.toAbsolutePath());
            } else {
                tempDir = Files.createTempDirectory("html-convert");
                LOGGER.info("Using temporary directory: {}", tempDir.toAbsolutePath());
            }

            
            String originalFilenameForName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            String ext = originalFilenameForName.contains(".") ? originalFilenameForName.substring(originalFilenameForName.lastIndexOf('.')) : ".html";
            String baseName = (originalFilenameForName.contains(".") ? originalFilenameForName.substring(0, originalFilenameForName.lastIndexOf('.')) : originalFilenameForName) + "-" + timestamp;
            Path inputPath = tempDir.resolve(baseName + ext);
            file.transferTo(inputPath);

            // Output path'i belirle
            Path outputPath = inputPath.getParent().resolve(baseName + ".pdf");

            LOGGER.info("HTML Input file: {}, PDF Output file: {}", inputPath, outputPath);

            // Chrome headless kullanarak HTML'den PDF'e dönüştürme
            try {
                LOGGER.info("Starting HTML to PDF conversion using Chrome headless");
                
                // HTML'i oku ve PDF için optimize et
                String htmlContent = Files.readString(inputPath, StandardCharsets.UTF_8);
                htmlContent = optimizeHtmlForPdf(htmlContent);
                
                // Optimize edilmiş HTML'i geçici dosyaya yaz
                Path optimizedHtmlPath = inputPath.getParent().resolve(baseName + "_optimized.html");
                Files.writeString(optimizedHtmlPath, htmlContent, StandardCharsets.UTF_8);
                
                convertHtmlToPdfWithChrome(optimizedHtmlPath, outputPath);
                
                // Geçici optimize dosyasını temizle
                Files.deleteIfExists(optimizedHtmlPath);
                
                LOGGER.info("Chrome headless HTML to PDF conversion completed successfully");
            } catch (Exception e) {
                LOGGER.error("Chrome headless HTML to PDF error: {}", e.getMessage(), e);
                throw new RuntimeException("HTML to PDF conversion failed: " + e.getMessage(), e);
            }

            // Output dosyasının oluştuğunu kontrol et
            if (!Files.exists(outputPath)) {
                LOGGER.error("Output PDF file was not created: {}", outputPath);
                throw new RuntimeException("Output PDF file was not created");
            }

            LOGGER.info("HTML to PDF conversion successful. Output file: {}", outputPath);

            // PDF'i response olarak döndür
            FileSystemResource resource = new FileSystemResource(outputPath.toFile());
            
            // Cleanup: Local development'ta ve debug modunda dosyaları sakla, production'da sil
            boolean isDocker = Files.exists(Paths.get("/.dockerenv"));
            boolean isLocalDevelopment = !isDocker && Files.exists(Paths.get("outputs")) && Files.isDirectory(Paths.get("outputs"));
            boolean isDebugMode = "true".equals(debugMode);
            
            if (!isLocalDevelopment && !isDebugMode) {
                try {
                    Files.deleteIfExists(inputPath);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete temporary HTML input file: {}", e.getMessage());
                }
            } else {
                if (isLocalDevelopment && !isDocker) {
                    LOGGER.info("Local development mode: HTML and PDF files preserved in outputs folder for inspection");
                } else if (isDebugMode) {
                    // Debug mode: PDF'i /tmp/outputs'a kopyala
                    try {
                        Path debugDir = Paths.get("/tmp/outputs");
                        Files.createDirectories(debugDir);
                        Path debugPdfPath = debugDir.resolve("html-to-pdf-" + System.currentTimeMillis() + ".pdf");
                        Files.copy(outputPath, debugPdfPath);
                        LOGGER.info("Debug mode: PDF copied to {} for inspection", debugPdfPath);
                    } catch (IOException e) {
                        LOGGER.warn("Could not copy PDF to debug directory: {}", e.getMessage());
                    }
                    LOGGER.info("Debug mode: HTML and PDF files preserved in /tmp/outputs for inspection");
                }
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted-from-html.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
                    
        } catch (MultipartException e) {
            LOGGER.error("Multipart request error during HTML to PDF: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Error during HTML to PDF conversion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Optimize HTML content for PDF generation - remove headers/footers
     */
    private String optimizeHtmlForPdf(String htmlContent) {
        try {
            // HTML içinde <head> tag'ini bul ve CSS ekle
            String pdfOptimizedCss = """
                <style type="text/css" media="print">
                    @page {
                        margin: 0.5cm;
                        size: A4;
                        /* Chrome header/footer'ı tamamen kaldır */
                        margin-top: 0.5cm;
                        margin-bottom: 0.5cm;
                        margin-left: 0.5cm; 
                        margin-right: 0.5cm;
                    }
                    
                    body {
                        margin: 0;
                        padding: 10px;
                        font-family: Arial, sans-serif;
                        -webkit-print-color-adjust: exact;
                        print-color-adjust: exact;
                    }
                    
                    /* Sayfa kırılmalarını kontrol et */
                    table {
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }
                    
                    tr {
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }
                    
                    /* Chrome'un otomatik header/footer'ını gizle */
                    @media print {
                        .no-print {
                            display: none !important;
                        }
                        
                        * {
                            -webkit-print-color-adjust: exact !important;
                            print-color-adjust: exact !important;
                        }
                    }
                </style>
                """;
            
            // <head> tag'ini bul ve CSS'i ekle
            if (htmlContent.toLowerCase().contains("<head>")) {
                htmlContent = htmlContent.replaceFirst("(?i)(<head[^>]*>)", "$1" + pdfOptimizedCss);
            } else if (htmlContent.toLowerCase().contains("<html>")) {
                // <head> tag'i yoksa, <html>'den sonra ekle
                htmlContent = htmlContent.replaceFirst("(?i)(<html[^>]*>)", "$1<head>" + pdfOptimizedCss + "</head>");
            } else {
                // HTML tag'i de yoksa, başa ekle
                htmlContent = "<!DOCTYPE html><html><head>" + pdfOptimizedCss + "</head><body>" + htmlContent + "</body></html>";
            }
            
            LOGGER.debug("HTML optimized for PDF generation with print-specific CSS");
            return htmlContent;
            
        } catch (Exception e) {
            LOGGER.warn("Failed to optimize HTML for PDF, using original content: {}", e.getMessage());
            return htmlContent;
        }
    }
    
    /**
     * Convert HTML file to PDF using Chrome headless
     */
    private void convertHtmlToPdfWithChrome(Path htmlFilePath, Path outputPath) throws Exception {
        LOGGER.info("Converting HTML to PDF using Chrome headless");
        
        // Chrome headless command for PDF conversion - OPTIMIZED
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // Chrome'un farklı path'lerini dene - Playwright Chromium öncelik
        String[] chromePaths = {
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", // macOS
            "/opt/chromium/chrome",          // Docker - Playwright Chromium (öncelik)
            "/usr/local/bin/chrome",         // Docker symlink
            "/usr/local/bin/google-chrome",  // Docker symlink
            "/usr/bin/google-chrome-stable", // Linux - Google Chrome stable
            "/usr/bin/google-chrome",        // Linux - Google Chrome
            "/usr/bin/chromium",             // Linux - Chromium alternative
            "/snap/bin/chromium",            // Snap installation
            "/usr/bin/chromium-browser",     // Ubuntu/Debian - Chromium (son sırada)
            "google-chrome-stable",          // System PATH - Google Chrome stable
            "google-chrome",                 // System PATH - Google Chrome  
            "chromium",                      // System PATH - Chromium alternative
            "chromium-browser",              // System PATH - Chromium (son sırada)
            "chrome"                         // System PATH - fallback
        };
        
        String chromeExecutable = null;
        for (String path : chromePaths) {
            if (Files.exists(Paths.get(path)) || isCommandAvailable(path)) {
                chromeExecutable = path;
                break;
            }
        }
        
        if (chromeExecutable == null) {
            throw new RuntimeException("Chrome/Chromium not found. Please install Google Chrome or Chromium browser.");
        }
        
        LOGGER.info("Using Chrome executable: {}", chromeExecutable);
        
        processBuilder.command(
            chromeExecutable,
            "--headless",                    // Headless mode
            "--disable-gpu",                 // GPU'yu devre dışı bırak
            "--disable-software-rasterizer", // Software rasterizer'ı kapat
            "--no-sandbox",                  // Sandbox'ı kapat (Docker için gerekli)
            "--disable-dev-shm-usage",       // /dev/shm kullanımını kapat
            "--disable-extensions",          // Eklentileri kapat
            "--disable-plugins",             // Plugin'leri kapat
            "--disable-default-apps",        // Varsayılan uygulamaları kapat
            "--disable-background-timer-throttling", // Arka plan timer'ı kapat
            "--disable-renderer-backgrounding", // Renderer arka plan işlemlerini kapat
            "--disable-features=TranslateUI,VizDisplayCompositor", // Gereksiz özellikler
            "--disable-ipc-flooding-protection", // IPC flood korumasını kapat
            "--disable-background-networking", // Arka plan network istekleri
            "--disable-sync",                // Chrome sync'i kapat
            "--disable-translate",           // Çeviri özelliğini kapat
            "--disable-features=AudioServiceOutOfProcess", // Audio service'i kapat
            "--disable-domain-reliability",  // Domain güvenilirlik kontrolü kapat
            "--disable-component-update",    // Komponent güncellemelerini kapat
            "--disable-client-side-phishing-detection", // Phishing korumasını kapat
            "--disable-3d-apis",            // 3D API'lerini kapat
            "--disable-accelerated-2d-canvas", // 2D canvas hızlandırmasını kapat
            "--disable-web-security",       // Web güvenliği kontrollerini gevşet
            "--memory-pressure-off",        // Bellek baskısı kontrollerini kapat
            "--max_old_space_size=512",     // V8 heap limit (MB)
            "--aggressive-cache-discard",   // Agresif cache temizleme
            "--enable-low-end-device-mode", // Düşük kaynak modu
            "--virtual-time-budget=10000",   // 10 saniye timeout (15'ten 10'a düşürüldü)
            "--timeout=10000",              // Genel timeout
            "--run-all-compositor-stages-before-draw", // Render tamamlanmadan önce bekle
            "--print-to-pdf=" + outputPath.toAbsolutePath().toString(), // PDF output path
            "--print-to-pdf-no-header",      // Header'ları kaldır
            "--disable-print-preview",       // Print preview'ı kapat
            "file://" + htmlFilePath.toAbsolutePath().toString() // HTML file URL
        );
        
        try {
            Process process = processBuilder.start();
            
            // Process output'unu logla
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // Timeout'u 30'dan 15'e düşürdük
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Chrome headless process timed out after 15 seconds");
            }
            
            int exitCode = process.exitValue();
            
            if (!output.isEmpty()) {
                LOGGER.info("Chrome output: {}", output);
            }
            if (!error.isEmpty()) {
                LOGGER.warn("Chrome error output: {}", error);
            }
            
            if (exitCode != 0) {
                throw new RuntimeException("Chrome headless failed with exit code: " + exitCode + ". Error: " + error);
            }
            
            LOGGER.info("Chrome headless conversion completed successfully");
            
        } catch (IOException | InterruptedException | RuntimeException e) {
            LOGGER.error("Error running Chrome headless: {}", e.getMessage());
            throw new Exception("Chrome headless conversion failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a command is available in system PATH
     */
    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
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

package com.erdem.document_converter_service.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.filter.DefaultFilterChain;
import org.jodconverter.local.filter.Filter;
import org.jodconverter.local.filter.FilterChain;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.multipart.MultipartFile;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.XComponent;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.uno.UnoRuntime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/convert")
public class ConvertController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertController.class);

    private ExecutorService executorService;
    private Playwright playwright;
    private Browser browser;
    private Semaphore browserSemaphore;
    private int poolSize;

    @Value("${playwright.browser.pool.size:4}")
    private int configuredPoolSize;

    // Use Spring Boot's standard 'debug' flag to control cleanup behavior
    // When debug=false (production), output files will be deleted after serving
    // When debug=true (dev), output files will be retained
    @Value("${debug:false}")
    private boolean debugEnabled;

    // Optional configurable output directory; if set, all temp files will be created here
    @Value("${app.output.directory:}")
    private String configuredOutputDirectory;

    @Autowired(required = false)
    private DocumentConverter documentConverter;
    
    @Autowired(required = false)
    private LocalOfficeManager officeManager;

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @PostConstruct
    public void init() {
        initThreadPool();
        initPlaywright();
    }

    private void initThreadPool() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = Math.max(8, availableProcessors * 4);
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(200);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "convert-pool-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        poolSize = Math.max(1, Math.min(configuredPoolSize, Math.max(1, availableProcessors / 2)));
        browserSemaphore = new Semaphore(poolSize);

        LOGGER.info("Executor initialized: poolSize={} queueCapacity={}", threadPoolSize, 200);
    }

    private void initPlaywright() {
        try {
            playwright = Playwright.create();
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(java.util.Arrays.asList(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--disable-extensions",
                            "--disable-plugins",
                            "--disable-background-timer-throttling",
                            "--disable-backgrounding-occluded-windows",
                            "--disable-renderer-backgrounding"
                    ));

            browser = playwright.chromium().launch(opts);
            LOGGER.info("Playwright browser launched (concurrency limit={})", poolSize);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Playwright: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdownThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            LOGGER.info("Shutting down executor...");
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

        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing Playwright browser: {}", e.getMessage());
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing Playwright: {}", e.getMessage());
            }
        }
    }

    @PostMapping(value = "/ExcelToPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Resource>> convertExcelToPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "landscape", defaultValue = "false") boolean landscape,
            @RequestParam(value = "fitToPage", defaultValue = "false") boolean fitToPage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertExcelToPdfSync(file, landscape, fitToPage);
            } catch (Exception e) {
                LOGGER.error("Error in async Excel to PDF: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }, executorService);
    }

    private ResponseEntity<Resource> convertExcelToPdfSync(MultipartFile file, boolean landscape, boolean fitToPage) throws Exception {
        if (documentConverter == null) {
            LOGGER.error("Excel conversion unavailable: LibreOffice not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Error", "Excel conversion requires LibreOffice")
                    .build();
        }

        if (file == null || file.isEmpty()) {
            LOGGER.warn("Empty Excel upload");
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            LOGGER.warn("Invalid Excel file type: {}", originalFilename);
            return ResponseEntity.badRequest().build();
        }

        LOGGER.info("Starting Excel conversion: {} size={} bytes, landscape={}, fitToPage={}", 
                   originalFilename, file.getSize(), landscape, fitToPage);

        Path tempDir = determineTempDir();
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String ext = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.')) : ".xlsx";
        String baseName = (safeName.contains(".") ? safeName.substring(0, safeName.lastIndexOf('.')) : safeName) + "-" + timestamp;
        Path inputPath = tempDir.resolve(baseName + ext);
        file.transferTo(inputPath);
        Path outputPath = inputPath.getParent().resolve(baseName + ".pdf");

        try {
            if (landscape || fitToPage) {
                // Use LibreOffice UNO API to set page properties before conversion
                Filter pagePropertiesFilter = createPagePropertiesFilter(landscape, fitToPage);
                Filter pdfExportFilter = createPdfExportFilter(landscape);
                
                // Create filter chain
                FilterChain filterChain = new DefaultFilterChain(pagePropertiesFilter, pdfExportFilter);
                
                // Create PDF format
                org.jodconverter.core.document.DocumentFormat pdfFormat = 
                    org.jodconverter.core.document.DocumentFormat.builder()
                        .name("PDF")
                        .extension("pdf")
                        .mediaType("application/pdf")
                        .storeProperty(org.jodconverter.core.document.DocumentFamily.SPREADSHEET, 
                                      "FilterName", "calc_pdf_Export")
                        .build();
                
                // Use LocalConverter with filter support
                LocalConverter.builder()
                    .officeManager(officeManager)
                    .filterChain(filterChain)
                    .build()
                    .convert(inputPath.toFile())
                    .as(pdfFormat)
                    .to(outputPath.toFile())
                    .execute();
                    
                LOGGER.info("Excel converted with UNO API settings: landscape={}, fitToPage={}", landscape, fitToPage);
            } else {
                // Standard conversion
                documentConverter.convert(inputPath.toFile()).to(outputPath.toFile()).execute();
                LOGGER.info("Excel converted with default settings");
            }
        } catch (OfficeException e) {
            LOGGER.error("JODConverter failed: {}", e.getMessage(), e);
            throw new RuntimeException("Document conversion failed", e);
        }

        if (!Files.exists(outputPath)) {
            LOGGER.error("JODConverter output missing: {}", outputPath);
            throw new RuntimeException("Output PDF not created");
        }

        InputStream is = Files.newInputStream(outputPath);
        InputStreamResource resource = new InputStreamResource(is);
        long contentLength = Files.size(outputPath);

        ResponseEntity<Resource> response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(contentLength)
                .body(resource);

        scheduleCleanupAsync(inputPath, outputPath);
        return response;
    }

    @PostMapping(value = "/HtmlToPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Resource>> convertHtmlToPdf(@RequestParam("file") MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertHtmlToPdfSync(file);
            } catch (Exception e) {
                LOGGER.error("Error in async HTML to PDF: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }, executorService);
    }

    private ResponseEntity<Resource> convertHtmlToPdfSync(MultipartFile file) throws Exception {
        if (browser == null) {
            LOGGER.error("HTML conversion unavailable: Playwright not initialized");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Error", "HTML conversion not available")
                    .build();
        }

        if (file == null || file.isEmpty()) {
            LOGGER.warn("Empty HTML upload");
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".html") && !originalFilename.toLowerCase().endsWith(".htm"))) {
            LOGGER.warn("Invalid HTML file type: {}", originalFilename);
            return ResponseEntity.badRequest().build();
        }

        LOGGER.info("Starting HTML conversion: {} size={} bytes", originalFilename, file.getSize());

        Path tempDir = determineTempDir();
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String ext = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.')) : ".html";
        String baseName = (safeName.contains(".") ? safeName.substring(0, safeName.lastIndexOf('.')) : safeName) + "-" + timestamp;
        Path inputPath = tempDir.resolve(baseName + ext);
        file.transferTo(inputPath);
        Path outputPath = inputPath.getParent().resolve(baseName + ".pdf");

        String htmlContent = Files.readString(inputPath, StandardCharsets.UTF_8);
        htmlContent = optimizeHtmlForPdf(htmlContent);

        boolean permitAcquired = browserSemaphore.tryAcquire(30, TimeUnit.SECONDS);
        if (!permitAcquired) {
            LOGGER.warn("No Playwright permits available (max={})", poolSize);
            throw new RuntimeException("Server is busy. Try again later.");
        }

        try (com.microsoft.playwright.BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setDefaultTimeout(15000);
            page.setDefaultNavigationTimeout(15000);
            page.setContent(htmlContent, new Page.SetContentOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));
            page.pdf(new Page.PdfOptions()
                    .setPath(outputPath)
                    .setFormat("A4")
                    .setMargin(new com.microsoft.playwright.options.Margin().setTop("5mm").setRight("5mm").setBottom("5mm").setLeft("5mm"))
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true)
                    .setDisplayHeaderFooter(false)
                    .setScale(0.9));
            LOGGER.info("HTML to PDF completed. Output: {}", outputPath);
        } catch (Exception e) {
            LOGGER.error("Playwright conversion failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            browserSemaphore.release();
        }

        if (!Files.exists(outputPath)) {
            LOGGER.error("Playwright output missing: {}", outputPath);
            throw new RuntimeException("Output PDF not created");
        }

        InputStream is = Files.newInputStream(outputPath);
        InputStreamResource resource = new InputStreamResource(is);
        long contentLength = Files.size(outputPath);

        ResponseEntity<Resource> response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted-from-html.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(contentLength)
                .body(resource);

        scheduleCleanupAsync(inputPath, outputPath);
        return response;
    }

    private Path determineTempDir() throws IOException {
        if (configuredOutputDirectory != null && !configuredOutputDirectory.isBlank()) {
            Path configuredDir = Paths.get(configuredOutputDirectory);
            Files.createDirectories(configuredDir);
            return configuredDir;
        }

        Path localOutputsDir = Paths.get("outputs");
        if (Files.exists(localOutputsDir) && Files.isDirectory(localOutputsDir)) {
            return localOutputsDir;
        }

        if (debugEnabled) {
            Path debugDir = Paths.get("/tmp/outputs");
            Files.createDirectories(debugDir);
            return debugDir;
        }

        return Files.createTempDirectory("convert-");
    }

    private void scheduleCleanupAsync(Path inputPath, Path outputPath) {
        executorService.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                Files.deleteIfExists(inputPath);
                if (!debugEnabled) {
                    TimeUnit.SECONDS.sleep(30);
                    Files.deleteIfExists(outputPath);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Async cleanup failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Creates a JODConverter Filter that uses LibreOffice UNO API to set page properties.
     * This allows us to programmatically set landscape orientation and fit-to-page scaling.
     */
    private Filter createPagePropertiesFilter(boolean landscape, boolean fitToPage) {
        return (context, document, chain) -> {
            try {
                // Get the document as XComponent
                XComponent xComponent = document;
                
                // Query for XSpreadsheetDocument interface
                XSpreadsheetDocument xSpreadsheetDoc = UnoRuntime.queryInterface(
                    XSpreadsheetDocument.class, xComponent);
                
                if (xSpreadsheetDoc != null) {
                    // Get the style families
                    XStyleFamiliesSupplier xStyleFamiliesSupplier = UnoRuntime.queryInterface(
                        XStyleFamiliesSupplier.class, xSpreadsheetDoc);
                    
                    XNameAccess xStyleFamilies = xStyleFamiliesSupplier.getStyleFamilies();
                    
                    // Get PageStyles family
                    Object pageStylesObj = xStyleFamilies.getByName("PageStyles");
                    XNameAccess xPageStyles = UnoRuntime.queryInterface(XNameAccess.class, pageStylesObj);
                    
                    // Get all sheets and apply settings to each
                    XIndexAccess xSheets = UnoRuntime.queryInterface(
                        XIndexAccess.class, xSpreadsheetDoc.getSheets());
                    
                    int sheetCount = xSheets.getCount();
                    for (int i = 0; i < sheetCount; i++) {
                        Object sheetObj = xSheets.getByIndex(i);
                        XSpreadsheet xSheet = UnoRuntime.queryInterface(XSpreadsheet.class, sheetObj);
                        
                        // Get the page style property set for this sheet
                        XPropertySet xSheetProps = UnoRuntime.queryInterface(XPropertySet.class, xSheet);
                        String pageStyleName = (String) xSheetProps.getPropertyValue("PageStyle");
                        
                        // Get the actual page style
                        Object pageStyleObj = xPageStyles.getByName(pageStyleName);
                        XStyle xPageStyle = UnoRuntime.queryInterface(XStyle.class, pageStyleObj);
                        XPropertySet xPageProps = UnoRuntime.queryInterface(XPropertySet.class, xPageStyle);
                        
                        // Set landscape orientation
                        if (landscape) {
                            // First get current dimensions
                            int currentWidth = (Integer) xPageProps.getPropertyValue("Width");
                            int currentHeight = (Integer) xPageProps.getPropertyValue("Height");
                            
                            LOGGER.info("Sheet {}: Original dimensions Width={}, Height={}", i, currentWidth, currentHeight);
                            
                            // Set IsLandscape property
                            xPageProps.setPropertyValue("IsLandscape", Boolean.TRUE);
                            
                            // CRITICAL: For landscape to work in PDF export, we must swap Width and Height
                            // LibreOffice doesn't automatically swap dimensions when IsLandscape=true
                            if (currentWidth < currentHeight) {
                                // Currently portrait, swap to landscape
                                xPageProps.setPropertyValue("Width", currentHeight);
                                xPageProps.setPropertyValue("Height", currentWidth);
                                LOGGER.info("Sheet {}: Swapped dimensions to Width={}, Height={}", i, currentHeight, currentWidth);
                            } else {
                                LOGGER.info("Sheet {}: Already landscape (Width >= Height)", i);
                            }
                            
                            // Verify final state
                            Boolean isLandscapeValue = (Boolean) xPageProps.getPropertyValue("IsLandscape");
                            int finalWidth = (Integer) xPageProps.getPropertyValue("Width");
                            int finalHeight = (Integer) xPageProps.getPropertyValue("Height");
                            LOGGER.info("Sheet {}: Final state - IsLandscape={}, Width={}, Height={}", 
                                       i, isLandscapeValue, finalWidth, finalHeight);
                        }
                        
                        // Set fit-to-page scaling
                        if (fitToPage) {
                            // ScaleToPages = 1 means fit all content to 1 page
                            xPageProps.setPropertyValue("ScaleToPages", (short) 1);
                            
                            // Verify it was set
                            Short scaleValue = (Short) xPageProps.getPropertyValue("ScaleToPages");
                            LOGGER.info("Sheet {}: Set ScaleToPages=1, verified={}", i, scaleValue);
                        }
                    }
                    
                    LOGGER.info("Successfully applied UNO page properties: landscape={}, fitToPage={}", 
                               landscape, fitToPage);
                } else {
                    LOGGER.warn("Document is not a spreadsheet, skipping page property modifications");
                }
                
            } catch (com.sun.star.uno.Exception e) {
                LOGGER.error("Failed to apply UNO page properties: {}", e.getMessage(), e);
                // Don't throw - let conversion continue even if properties fail
            } catch (RuntimeException e) {
                LOGGER.error("Runtime error applying UNO page properties: {}", e.getMessage(), e);
                // Don't throw - let conversion continue even if properties fail
            }
            
            // Continue the filter chain
            chain.doFilter(context, document);
        };
    }

    /**
     * Creates a filter to apply PDF export settings like orientation.
     * This filter manipulates the FilterData that gets passed to LibreOffice's PDF export.
     */
    private Filter createPdfExportFilter(boolean landscape) {
        return (context, document, chain) -> {
            LOGGER.info("Applying PDF export filter: landscape={}", landscape);
            
            if (landscape) {
                try {
                    // Get the XComponent
                    XComponent xComponent = document;
                    
                    // Query for XSpreadsheetDocument
                    com.sun.star.sheet.XSpreadsheetDocument xSpreadsheetDoc = 
                        com.sun.star.uno.UnoRuntime.queryInterface(
                            com.sun.star.sheet.XSpreadsheetDocument.class, xComponent);
                    
                    if (xSpreadsheetDoc != null) {
                        // Access the printer property set for the document
                        com.sun.star.beans.XPropertySet xDocProps = 
                            com.sun.star.uno.UnoRuntime.queryInterface(
                                com.sun.star.beans.XPropertySet.class, xSpreadsheetDoc);
                        
                        if (xDocProps != null) {
                            // Try to set printer/export orientation
                            try {
                                // Some LibreOffice versions support this property
                                xDocProps.setPropertyValue("PrinterOrientation", 
                                    com.sun.star.view.PaperOrientation.LANDSCAPE);
                                LOGGER.info("Set PrinterOrientation to LANDSCAPE");
                            } catch (com.sun.star.uno.Exception e) {
                                LOGGER.debug("PrinterOrientation not available: {}", e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not apply PDF export orientation: {}", e.getMessage());
                }
            }
            
            // Continue the filter chain
            chain.doFilter(context, document);
        };
    }

    private String optimizeHtmlForPdf(String htmlContent) {
        try {
            String pdfOptimizedCss = """
                <style type=\"text/css\" media=\"print\">@page{margin:0.5cm;size:A4;}body{margin:0;padding:10px;font-family:Arial, sans-serif;-webkit-print-color-adjust:exact;}</style>
                """;
            if (htmlContent.toLowerCase().contains("<head>")) {
                htmlContent = htmlContent.replaceFirst("(?i)(<head[^>]*>)", "$1" + pdfOptimizedCss);
            } else if (htmlContent.toLowerCase().contains("<html>")) {
                htmlContent = htmlContent.replaceFirst("(?i)(<html[^>]*>)", "$1<head>" + pdfOptimizedCss + "</head>");
            } else {
                htmlContent = "<!DOCTYPE html><html><head>" + pdfOptimizedCss + "</head><body>" + htmlContent + "</body></html>";
            }
            return htmlContent;
        } catch (Exception e) {
            LOGGER.warn("HTML optimization failed: {}", e.getMessage());
            return htmlContent;
        }
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        LOGGER.warn("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body("{\"error\": \"Unsupported media type. Send multipart/form-data with 'file'.\"}");
    }
}
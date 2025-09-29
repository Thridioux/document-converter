package com.erdem.excel_to_pdf_service.controller;

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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/convert")
public class ConvertController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertController.class);

    // Thread pool for parallel processing
    private ExecutorService executorService;

    // Playwright single browser + semaphore to limit concurrency (lighter than multiple browsers)
    private Playwright playwright;
    private Browser browser;
    private Semaphore browserSemaphore;
    private int poolSize;

    @Value("${playwright.browser.pool.size:4}")
    private int configuredPoolSize;

    @Autowired(required = false)
    private DocumentConverter documentConverter;

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
            // fallback: conversions will return 503 if browser == null
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<Resource>> convertExcelToPdf(@RequestParam("file") MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertExcelToPdfSync(file);
            } catch (Exception e) {
                LOGGER.error("Error in async Excel to PDF: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }, executorService);
    }

    private ResponseEntity<Resource> convertExcelToPdfSync(MultipartFile file) throws Exception {
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

        LOGGER.info("Starting Excel conversion: {} size={} bytes", originalFilename, file.getSize());

        Path tempDir = determineTempDir();
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String ext = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.')) : ".xlsx";
        String baseName = (safeName.contains(".") ? safeName.substring(0, safeName.lastIndexOf('.')) : safeName) + "-" + timestamp;
        Path inputPath = tempDir.resolve(baseName + ext);
        file.transferTo(inputPath);
        Path outputPath = inputPath.getParent().resolve(baseName + ".pdf");

        try {
            documentConverter.convert(inputPath.toFile()).to(outputPath.toFile()).execute();
        } catch (OfficeException e) {
            LOGGER.error("JODConverter failed: {}", e.getMessage(), e);
            throw new RuntimeException("Document conversion failed", e);
        }

        if (!Files.exists(outputPath)) {
            LOGGER.error("JODConverter output missing: {}", outputPath);
            throw new RuntimeException("Output PDF not created");
        }

        // Stream the file to client and schedule async cleanup
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

    @PostMapping(value = "/html-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

        // Read HTML into memory and avoid writing optimized file to disk if small
        String htmlContent = Files.readString(inputPath, StandardCharsets.UTF_8);
        htmlContent = optimizeHtmlForPdf(htmlContent);

        boolean permitAcquired = browserSemaphore.tryAcquire(30, TimeUnit.SECONDS);
        if (!permitAcquired) {
            LOGGER.warn("No Playwright permits available (max={})", poolSize);
            throw new RuntimeException("Server is busy. Try again later.");
        }

        try (com.microsoft.playwright.BrowserContext context = browser.newContext()) {
            Page page = context.newPage();

            page.setDefaultTimeout(15000); // fail fast
            page.setDefaultNavigationTimeout(15000);

            // Use setContent to avoid disk I/O
            page.setContent(htmlContent, new Page.SetContentOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

            page.pdf(new Page.PdfOptions()
                    .setPath(outputPath)
                    .setFormat("A4")
                    .setMargin(new com.microsoft.playwright.options.Margin()
                            .setTop("5mm").setRight("5mm").setBottom("5mm").setLeft("5mm"))
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
        String debugMode = System.getenv("DEBUG_MODE");
        Path localOutputsDir = Paths.get("outputs");
        if (Files.exists(localOutputsDir) && Files.isDirectory(localOutputsDir)) {
            return localOutputsDir;
        } else if ("true".equals(debugMode)) {
            Path debugDir = Paths.get("/tmp/outputs");
            Files.createDirectories(debugDir);
            return debugDir;
        } else {
            return Files.createTempDirectory("convert-");
        }
    }

    private void scheduleCleanupAsync(Path inputPath, Path outputPath) {
        executorService.submit(() -> {
            try {
                // small sleep to ensure stream consumers have started
                TimeUnit.SECONDS.sleep(2);
                Files.deleteIfExists(inputPath);
                // keep PDF for a short time then delete
                TimeUnit.SECONDS.sleep(30);
                Files.deleteIfExists(outputPath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Async cleanup failed: {}", e.getMessage());
            }
        });
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

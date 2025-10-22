# 📄 Document Converter Service

🚀 A Spring Boot application for converting various document formats (Excel, HTML) to PDF.  
The service allows users to upload document files, which get processed and converted into PDF format.  
The generated PDF can then be downloaded directly.

---

## ✨ Features

✅ **Convert Excel to PDF**: Upload XLSX files and receive PDF conversions with customizable formatting options (landscape orientation, fit-to-page scaling).  
✅ **Convert HTML to PDF**: Upload HTML files and generate high-quality PDF documents.  
✅ **Download PDF Files**: Download the generated PDF by making conversion requests.  
✅ **Concurrent Processing**: Handles multiple conversion requests efficiently with thread pool management.  

---

## 🛠 Technologies & Dependencies

- **Java 21** - Latest LTS version
- **Spring Boot 3.5.5** - Web framework and application container
- **JODConverter 4.4.6** - Office document conversion library
- **Microsoft Playwright 1.47.0** - Browser automation for HTML to PDF conversion
- **Maven** - Build and dependency management

---

## 🚀 Running the Project

### 📋 Prerequisites

Before running this project, ensure you have the following installed:

### 🔹 For Local Development

#### **macOS Prerequisites**

1. **Java Development Kit (JDK) 21**
   ```bash
   # Using Homebrew
   brew install openjdk@21
   
   # Add to your shell profile (.zshrc or .bash_profile)
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ```

2. **Maven 3.9+**
   ```bash
   # Using Homebrew
   brew install maven
   
   # Verify installation
   mvn -version
   ```

3. **LibreOffice** (Required for Excel conversions)
   ```bash
   # Using Homebrew
   brew install --cask libreoffice
   ```

#### **Windows Prerequisites**

1. **Java Development Kit (JDK) 21**  
   🔗 **Download:** [Oracle JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)  
   - Download and install the Windows x64 Installer
   - Set `JAVA_HOME` environment variable to JDK installation path

2. **Maven**
   - Download **apache-maven-3.9.9-bin.zip** from [Maven Downloads](https://maven.apache.org/download.cgi)
   - Extract to: `C:\Program Files\Maven\apache-maven-3.9.9`
   
   **Setting up Maven Environment:**
   
   📝 **Step 1: Add `MAVEN_HOME` System Variable**
   1. Open **Start menu** → search for *environment variables*
   2. Click **Edit the system environment variables**
   3. Under **Advanced** tab → **Environment Variables**
   4. Click **New** (System variables) and enter:
      - **Variable Name:** `MAVEN_HOME`
      - **Variable Value:** `C:\Program Files\Maven\apache-maven-3.9.9`
   
   📝 **Step 2: Add Maven to `PATH`**
   1. Select **Path** (System variables) → **Edit**
   2. Click **New** and add: `%MAVEN_HOME%\bin`
   
   📝 **Step 3: Verify Installation**
   ```cmd
   mvn -version
   ```

3. **LibreOffice** (Required for Excel conversions)
   - Download from [LibreOffice Downloads](https://www.libreoffice.org/download/download/)
   - Install using the Windows installer

---

## 🐳 Option 1: Running with Docker (Recommended)

### **Quick Start with Docker**

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Link.Cloud.DocumentConverter
   ```

2. **Build and run with Docker**
   ```bash
   # Build the Docker image
   docker build -t document-converter-service:latest .
   
   # Run the container (production mode)
   docker run -p 8080:8080 document-converter-service:latest
   ```

3. **Running in Debug Mode with Volume Mount**
   ```bash
   # Create local outputs directory first
   mkdir -p outputs
   
   # Run in debug mode with persistent output files
   docker run -d --name document-converter-service \
     -p 8080:8080 \
     -v "$(pwd)/outputs:/tmp/outputs" \
     -e DEBUG_MODE=true \
     document-converter-service:latest
   
   # For Windows (PowerShell)
   # Create outputs directory
   New-Item -ItemType Directory -Force -Path "outputs"
   
   docker run -d --name document-converter-service `
     -p 8080:8080 `
     -v "${PWD}/outputs:/tmp/outputs" `
     -e DEBUG_MODE=true `
     document-converter-service:latest
   
   # For Windows (Command Prompt)
   # Create outputs directory
   mkdir outputs
   
   docker run -d --name document-converter-service ^
     -p 8080:8080 ^
     -v "%cd%/outputs:/tmp/outputs" ^
     -e DEBUG_MODE=true ^
     document-converter-service:latest
   ```


**✅ Advantages of Docker approach:**
- No need to install Java, Maven, or LibreOffice locally
- Consistent environment across different systems
- All dependencies are containerized

### **🔧 Docker Debug Mode Options**

**Debug Mode Features:**
- **`-d`**: Run container in detached mode (background)
- **`--name`**: Assign a custom name to the container for easy management
- **`-v "$(pwd)/outputs:/tmp/outputs"`**: Mount local `outputs` directory to container's `/tmp/outputs`
- **`-e DEBUG_MODE=true`**: Override the default `DEBUG_MODE=false` from Dockerfile to enable debug mode (keeps generated files for inspection)

**✅ No Dockerfile Changes Needed:**
The Docker environment variable `-e DEBUG_MODE=true` will override the default `DEBUG_MODE=false` setting in the Dockerfile. You can run the debug mode without modifying any files!

**Container Management Commands:**
```bash
# View container logs
docker logs document-converter-service

# Stop the container
docker stop document-converter-service

# Start the stopped container
docker start document-converter-service

# Remove the container
docker rm document-converter-service

# View generated files in local outputs directory
ls outputs/
```

---

## 💻 Option 2: Running Locally

### **Step 1: Configure the Application**

Create or modify `src/main/resources/application.properties`:

```properties
# Application settings
spring.application.name=document-converter-service
server.port=8080

# Debug mode - when false (production), output files will be auto-deleted after serving
# When true (development), output files will be retained
debug=true

# File upload limits
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Output directory (use /tmp for container-friendliness)
app.output.directory=/tmp/outputs

```

**📁 Create Output Directory:**
```bash
# macOS/Linux
mkdir -p /tmp/outputs

# Windows
mkdir C:\tmp\outputs
```

For Windows, update the output directory in `application.properties`:
```properties
app.output.directory=C:/tmp/outputs
```

### **Step 2: Build the Project**

```bash
# Clean and build the project
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests
```

### **Step 3: Run the Application**

```bash
# Method 1: Using Maven
mvn spring-boot:run

# Method 2: Using the built JAR
java -jar target/document-converter-service-0.0.1-SNAPSHOT.jar
```

### **Step 4: Verify the Application**

The application will start on `http://localhost:8080`

---

## 📡 API Endpoints

### **Convert Excel to PDF**
```bash
POST /api/convert/ExcelToPdf
Content-Type: multipart/form-data

# Basic conversion
curl -X POST \
  http://localhost:8080/api/convert/ExcelToPdf \
  -F "file=@path/to/your/file.xlsx"

# With landscape orientation
curl -X POST \
  http://localhost:8080/api/convert/ExcelToPdf \
  -F "file=@path/to/your/file.xlsx" \
  -F "landscape=true"

# With fit to page option
curl -X POST \
  http://localhost:8080/api/convert/ExcelToPdf \
  -F "file=@path/to/your/file.xlsx" \
  -F "fitToPage=true"

# With both landscape and fit to page
curl -X POST \
  http://localhost:8080/api/convert/ExcelToPdf \
  -F "file=@path/to/your/file.xlsx" \
  -F "landscape=true" \
  -F "fitToPage=true"
```

**Parameters:**
- `file` (required): Excel file (`.xlsx` or `.xls`)
- `landscape` (optional): Set to `true` for landscape orientation, `false` for portrait (default: `false`)
- `fitToPage` (optional): Set to `true` to fit content to page, `false` for original scaling (default: `false`)

**Response:** PDF file download with filename `converted.pdf`  
**Requirements:** LibreOffice must be installed and configured

### **Convert HTML to PDF**
```bash
POST /api/convert/HtmlToPdf
Content-Type: multipart/form-data

curl -X POST \
  http://localhost:8080/api/convert/HtmlToPdf \
  -F "file=@path/to/your/file.html"
```

**Supported file formats:** `.html`, `.htm`  
**Response:** PDF file download with filename `converted-from-html.pdf`  
**Features:**
- A4 format with 5mm margins
- Background colors and images preserved
- CSS print styles automatically optimized
- Network idle wait for complete resource loading

---

## 🔧 Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Application port |
| `debug` | `true` | Keep output files for debugging |
| `app.output.directory` | `/tmp/outputs` | Directory for generated files |
| `spring.servlet.multipart.max-file-size` | `10MB` | Maximum file upload size |
| `server.tomcat.threads.max` | `50` | Maximum thread pool size |
| `playwright.browser.pool.size` | `4` | Browser instance pool size |
| `playwright.browser.timeout` | `30000` | Browser timeout in milliseconds |
| `jodconverter.local.enabled` | `true` | Enable LibreOffice conversion |
| `jodconverter.local.max-tasks-per-process` | `10` | Max tasks per LibreOffice process |
| `jodconverter.local.office-home` | `/usr/lib/libreoffice` | LibreOffice installation path |

---

## 🚨 Troubleshooting

### **Common Issues**

1. **"LibreOffice not found" error**
   - Ensure LibreOffice is installed and accessible in system PATH
   - For macOS: Check `/Applications/LibreOffice.app/Contents/MacOS/soffice`
   - For Windows: Check `C:\Program Files\LibreOffice\program\soffice.exe`

2. **Out of memory errors**
   - Increase JVM heap size: `-Xmx2g -Xms1g`
   - Reduce concurrent processing threads in application.properties

3. **File upload errors**
   - Check file size limits in application.properties
   - Ensure output directory exists and has write permissions

4. **Port already in use**
   ```bash
   # Change port in application.properties
   server.port=8081
   ```

### **Docker-specific Issues**

1. **Container fails to start**
   ```bash
   # Check logs
   docker logs <container-id>
   
   # Run in interactive mode for debugging
   docker run -it document-converter /bin/bash
   ```

---

## 📝 Development Notes

- The service uses thread pools to handle concurrent conversion requests
- Output files are automatically cleaned up in production mode (`debug=false`)
- Playwright browsers are managed with proper lifecycle handling
- JODConverter uses LibreOffice in headless mode for Excel conversions
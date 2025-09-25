# Use Maven for the build process
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn clean package -DskipTests

# Use Eclipse Temurin JRE for runtime - Ubuntu 22.04 compatible
FROM eclipse-temurin:21-jre

# Install LibreOffice and Chrome for HTML-to-PDF (multi-platform)
RUN apt-get update && \
    # LibreOffice için
    apt-get install -y libreoffice curl fontconfig fonts-liberation && \
    # Chrome için basit yaklaşım - her platform için Chromium
    apt-get install -y wget unzip && \
    # Platform detection ve Chromium kurulumu
    ARCH=$(dpkg --print-architecture) && \
    echo "Detected architecture: $ARCH" && \
    if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then \
        # ARM64 için Microsoft Playwright Chromium
        CHROMIUM_URL="https://playwright.azureedge.net/builds/chromium/1129/chromium-linux-arm64.zip"; \
    else \
        # AMD64 için Microsoft Playwright Chromium  
        CHROMIUM_URL="https://playwright.azureedge.net/builds/chromium/1129/chromium-linux.zip"; \
    fi && \
    wget -O /tmp/chromium.zip "$CHROMIUM_URL" && \
    cd /tmp && unzip chromium.zip && \
    mv chrome-linux /opt/chromium && \
    chmod +x /opt/chromium/chrome && \
    # Chrome symlink'lerini oluştur
    ln -sf /opt/chromium/chrome /usr/local/bin/chrome && \
    ln -sf /opt/chromium/chrome /usr/local/bin/chromium && \
    ln -sf /opt/chromium/chrome /usr/local/bin/google-chrome && \
    # Cleanup
    rm -rf /tmp/chromium.zip /tmp/chrome-linux && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create LibreOffice user profile directory 
RUN mkdir -p /app/.config/libreoffice/4/user && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /app/.config/libreoffice/4/user/registrymodifications.xcu && \
    echo '<oor:items xmlns:oor="http://openoffice.org/2001/registry" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">' >> /app/.config/libreoffice/4/user/registrymodifications.xcu && \
    echo '<item oor:path="/org.openoffice.Office.Common/Print"><prop oor:name="PaperOrientation" oor:op="fuse"><value>0</value></prop></item>' >> /app/.config/libreoffice/4/user/registrymodifications.xcu && \
    echo '<item oor:path="/org.openoffice.Office.Common/Print"><prop oor:name="PaperFormat" oor:op="fuse"><value>9</value></prop></item>' >> /app/.config/libreoffice/4/user/registrymodifications.xcu && \
    echo '</oor:items>' >> /app/.config/libreoffice/4/user/registrymodifications.xcu 

WORKDIR /app
COPY --from=build /app/target/excel-to-pdf-service-*.jar app.jar

# Chrome headless için gerekli kullanıcı ve dizin ayarları
RUN groupadd -r appuser && useradd -r -g appuser -G audio,video appuser && \
    mkdir -p /home/appuser && chown -R appuser:appuser /home/appuser && \
    mkdir -p /app/outputs && chown -R appuser:appuser /app

ENV DEBUG_MODE=false
ENV CHROME_PATH=/usr/bin/google-chrome

EXPOSE 8080

USER appuser

# JVM heap options optimized for better performance under load
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]


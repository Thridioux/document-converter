# ----------------------------------------
# Stage 1: Build Java Application
# ----------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only pom.xml first for better caching
COPY pom.xml ./

# Download dependencies (cache)
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests


# ----------------------------------------
# Stage 2: Runtime Image with Playwright + Chromium
# ----------------------------------------
FROM mcr.microsoft.com/playwright/java:v1.47.0

# Install LibreOffice (for Excel → PDF) + fonts
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libreoffice \
        fontconfig \
        fonts-liberation \
        && rm -rf /var/lib/apt/lists/*

# LibreOffice default user profile (safe print configs)
RUN mkdir -p /app/.config/libreoffice/4/user && \
    cat > /app/.config/libreoffice/4/user/registrymodifications.xcu <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<oor:items xmlns:oor="http://openoffice.org/2001/registry"
           xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <item oor:path="/org.openoffice.Office.Common/Print">
    <prop oor:name="PaperOrientation" oor:op="fuse">
      <value>0</value>
    </prop>
  </item>
  <item oor:path="/org.openoffice.Office.Common/Print">
    <prop oor:name="PaperFormat" oor:op="fuse">
      <value>9</value>
    </prop>
  </item>
</oor:items>
EOF

# Create a non-root user
RUN useradd -m -r -g users appuser && \
    mkdir -p /app/outputs && \
    chown -R appuser:users /app

WORKDIR /app
COPY --from=build /app/target/excel-to-pdf-service-*.jar app.jar

# Environment variables
ENV DEBUG_MODE=false \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xms1g -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8"

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

# Use Maven for the build process
FROM maven:3.9.9-eclipse-temurin-23 AS build

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn clean package -DskipTests

# Use Eclipse Temurin JRE for runtime - smaller and more secure image
FROM eclipse-temurin:23-jre

# Install LibreOffice and curl for health checks
RUN apt-get update && apt-get install -y libreoffice curl && apt-get clean && \
    rm -rf /var/lib/apt/lists/* 

WORKDIR /app
COPY --from=build /app/target/excel-to-pdf-service-*.jar app.jar

ENV DEBUG_MODE=false

EXPOSE 8080

# JVM heap options optimized for better performance under load
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]


FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven
RUN mvn clean package -DskipTests

# Use full JDK 21 in runtime so javac is available for Java code submissions
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=builder /build/target/crs-assessment-service-1.0.0-SNAPSHOT.jar app.jar

# Install compilers and runtimes for multi-language evaluation
RUN apk add --no-cache \
    python3 \
    gcc \
    g++ \
    nodejs \
    musl-dev

ENV PORT=8085
EXPOSE 8085

ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]

# Pure Core Java + JDBC Dockerfile for Render (no Maven)
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Download MySQL Connector/J driver without Maven
RUN apk add --no-cache curl && \
    mkdir -p lib out && \
    curl -fsSL -o lib/mysql-connector-j-8.4.0.jar \
      https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar

COPY config/ config/
COPY sql/ sql/
COPY src/ src/
COPY web/ web/

# Compile all Core Java sources with javac
RUN find src -name "*.java" > sources.txt && \
    javac -encoding UTF-8 -d out @sources.txt && \
    rm -f sources.txt

EXPOSE 8080

CMD ["java", "-cp", "out:lib/*", "com.stockpulse.Main"]

# Build stage
FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY server/pom.xml server/pom.xml
COPY client/pom.xml client/pom.xml
COPY server/src server/src
COPY client/src client/src
RUN mvn -pl client -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:23-jre
WORKDIR /opt/rayan-jpos-client
RUN apt-get update \
    && apt-get install -y --no-install-recommends netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/client/target/client-1.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["/bin/sh", "-c", "set -a; [ -f .env ] && . ./.env; set +a; exec java $JAVA_OPTS -cp app.jar org.example.client.ClientMain"]
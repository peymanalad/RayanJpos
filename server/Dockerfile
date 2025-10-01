# Build stage
FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY server/pom.xml server/pom.xml
COPY client/pom.xml client/pom.xml
COPY server/src server/src
COPY client/src client/src
RUN mvn -pl server -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:23-jre
WORKDIR /opt/rayan-jpos
RUN apt-get update \
    && apt-get install -y --no-install-recommends netcat-openbsd bash\
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/server/target/server-1.0-SNAPSHOT.jar app.jar
COPY server/src/main/resources/deploy   /opt/rayan-jpos/runtime/deploy/
COPY server/src/main/resources/packager /opt/rayan-jpos/runtime/packager/
COPY server/wait-for.sh /usr/local/bin/wait-for
RUN chmod +x /usr/local/bin/wait-for

ENV JAVA_OPTS=""
ENV WAIT_FOR=""
ENV WAIT_FOR_TIMEOUT="60"

EXPOSE 5000

ENTRYPOINT ["/bin/sh", "-c", "set -a; [ -f .env ] && . ./.env; set +a; if [ -n \"$WAIT_FOR\" ]; then /usr/local/bin/wait-for \"$WAIT_FOR\" \"$WAIT_FOR_TIMEOUT\"; fi; exec java $JAVA_OPTS -jar app.jar"]
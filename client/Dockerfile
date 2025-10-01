# Build stage
FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY server/pom.xml server/pom.xml
COPY client/pom.xml client/pom.xml
COPY server/src server/src
COPY client/src client/src
RUN mvn -pl client -am -DskipTests package \
 && mkdir -p client/target/deps \
 && mvn -pl client -am -DskipTests dependency:copy-dependencies \
      -DincludeScope=runtime \
      -DoutputDirectory=client/target/deps

# Runtime stage
FROM eclipse-temurin:23-jre
WORKDIR /opt/rayan-jpos-client
RUN apt-get update \
    && apt-get install -y --no-install-recommends netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/client/target/client-1.0-SNAPSHOT.jar app.jar
COPY --from=build /workspace/client/target/deps ./deps
ENV JAVA_OPTS=""

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -cp \"app.jar:deps/*\" org.example.client.ClientMain"]
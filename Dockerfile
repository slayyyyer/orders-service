# Multi-stage build: the JDK and the Maven cache never reach the runtime image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first so the layer cache survives source-only changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre

# Non-root by construction: no shell, no package manager, no writable /app.
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=root:root /src/target/orders-service-0.1.0.jar /app/app.jar

USER 1001
EXPOSE 8080

# Container-aware memory sizing; the JVM reads the cgroup limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

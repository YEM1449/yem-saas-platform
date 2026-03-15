# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Cache Maven dependencies separately from source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
# Skip unit tests (run in CI separately); skip integration tests (require DB)
RUN ./mvnw package -DskipTests -q

# Explode the fat jar for faster layer-based startup
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /workspace/layers

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: non-root user
RUN groupadd --gid 1001 hlm && useradd --uid 1001 --gid hlm --shell /sbin/nologin hlm

WORKDIR /app

# Copy exploded layers in cache-friendly order
COPY --from=build /workspace/layers/dependencies/          ./
COPY --from=build /workspace/layers/spring-boot-loader/    ./
COPY --from=build /workspace/layers/snapshot-dependencies/ ./
COPY --from=build /workspace/layers/application/           ./

USER hlm

EXPOSE 8080

# JVM tuning for containers: use container CPU/memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

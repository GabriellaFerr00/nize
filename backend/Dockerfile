# Nize API image (D-13). Multi-stage: JDK 25 builds the jar; a slim JRE 25 runs it as a non-root user.
# Base images are pinned by digest; Dependabot proposes updates. No secret is ever baked in:
# NIZE_JWT_SECRET and NIZE_DB_* come from the Render environment at run time.

FROM eclipse-temurin:25-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build
WORKDIR /src
# Dependencies first, so source changes reuse the cached layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests already ran in the pipeline before the image is built.
RUN ./mvnw -B -q -DskipTests -Djacoco.skip=true package \
    && cp target/nize-backend-*.jar /app.jar

FROM eclipse-temurin:25-jre-alpine@sha256:56f273059de6b13a6b49fe0ded691ed2d0399bbee76156a8f2d385f7524a3d43
# Security fixes from Alpine that the pinned base does not have yet (trivy gate). Drop a package
# from this list once Dependabot bumps the base to a digest that already includes the fix.
RUN apk upgrade --no-cache libexpat \
    && addgroup -S -g 10001 nize && adduser -S -u 10001 -G nize -H -s /sbin/nologin nize
WORKDIR /app
# Owned by root and only readable by the app user: the process cannot rewrite its own code.
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080
EXPOSE 8080
# Generous start period: on Render's free plan a cold start takes tens of seconds.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1:${PORT}/actuator/health || exit 1"]
# Tuned for the 512 MB free instance: heap at 60% of the container, small thread stacks,
# capped metaspace/code cache, serial GC (lowest overhead on one CPU), crash fast on OOM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-Xss512k", \
    "-XX:MaxMetaspaceSize=160m", "-XX:ReservedCodeCacheSize=64m", "-XX:TieredStopAtLevel=1", \
    "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

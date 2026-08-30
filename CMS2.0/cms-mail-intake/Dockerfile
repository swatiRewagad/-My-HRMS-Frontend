# Build this with context set to the CMS2.0 repo root, not this directory — cms-mail-intake is
# part of the cms-parent Maven reactor and depends on the sibling cms-common module (AES-256-GCM
# payload encryption), which isn't published to any Maven repository. Building from this
# directory alone can't resolve that dependency. See deployment/docker/docker-compose.local.yml,
# which sets `context: ..` and `dockerfile: cms-mail-intake/Dockerfile` for exactly this reason.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /reactor
COPY . .
RUN apk add --no-cache maven && \
    mvn -q -pl cms-mail-intake -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S cms && adduser -S cms -G cms

COPY --from=build /reactor/cms-mail-intake/target/*.jar app.jar

RUN mkdir -p /data/cms/mail-intake/raw && chown -R cms:cms /data/cms/mail-intake /app

USER cms

# 8092: HTTP (admin API, actuator). 2525: the embedded SMTP listener.
EXPOSE 8092 2525

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8092/cms-mail-intake/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

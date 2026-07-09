FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven settings for Nexus access (cms-common, cms-parent)
COPY settings.xml /root/.m2/settings.xml

COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S cms && adduser -S cms -G cms

COPY --from=build /app/target/*.jar app.jar

USER cms

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

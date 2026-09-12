FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl common-lib,api-gateway,account-service,workspace-service,intelligence-service -am package -DskipTests
RUN javac -d /health deployment/Healthcheck.java
ARG MODULE
RUN case "$MODULE" in api-gateway|account-service|workspace-service|intelligence-service) ;; *) exit 1;; esac \
    && cp "$MODULE/target/$MODULE-0.0.1-SNAPSHOT.jar" /service.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /service.jar /app/service.jar
COPY --from=build /health /opt/health
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/service.jar"]

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl processing-service -am -DskipTests package
FROM eclipse-temurin:21-jre

ARG HEALTHCHECK_PORT=8084

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/processing-service/target/processing-service-*.jar app.jar
EXPOSE ${HEALTHCHECK_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:${HEALTHCHECK_PORT}/actuator/health || exit 1

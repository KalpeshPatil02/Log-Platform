FROM eclipse-temurin:21-jre

ARG JAR=app.jar
ARG HEALTHCHECK_PORT=8080

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY ${JAR} app.jar
EXPOSE ${HEALTHCHECK_PORT}
ENTRYPOINT ["java","-jar","/app.jar"]
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:${HEALTHCHECK_PORT}/actuator/health || exit 1

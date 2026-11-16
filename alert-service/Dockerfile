FROM eclipse-temurin:21-jre
ARG JAR=alert-service/target/alert-service-1.0.0-SNAPSHOT.jar
COPY ${JAR} /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

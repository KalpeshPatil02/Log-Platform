FROM eclipse-temurin:21-jre
ARG JAR
COPY ${JAR} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

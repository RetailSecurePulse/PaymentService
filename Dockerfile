FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/libs/spring-boot-api-0.0.1.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
LABEL authors="aungtuntun"
# Imagen para Google Cloud Run (ADR-10).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon buildFatJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/build/libs/exploracity-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

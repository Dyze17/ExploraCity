# Imagen para Google Cloud Run (ADR-10). La construye .github/workflows/deploy.yml; en el equipo:
#   docker build -t exploracity-api api-ktor
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Primero solo lo que define las dependencias: mientras no cambie, esta capa se reutiliza y no se vuelven a descargar.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon buildFatJar

FROM eclipse-temurin:25-jre
# Sin permisos de administrador. Solo puede escribir en media/, la carpeta de las fotos sin Cloudinary (desarrollo).
RUN useradd --system --uid 10001 --no-create-home exploracity
WORKDIR /app
RUN mkdir media && chown exploracity media
COPY --from=build /src/build/libs/exploracity-api.jar app.jar
USER exploracity
EXPOSE 8080
# Por omisión Java usa un cuarto de la memoria del contenedor, y la API no corre nada más. Netty carga código nativo.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]

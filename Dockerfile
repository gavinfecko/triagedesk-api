# Build with the Maven wrapper, run on a JRE as a non-root user. TD-71 adds layered jars and the prod compose profile.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp -q dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

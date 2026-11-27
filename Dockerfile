FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -o dependency:go-offline
COPY src src
RUN ./mvnw -q -o package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -m fincore
USER fincore
COPY --from=build /app/target/fincore-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

# Multi-stage build: compile with the full JDK + Maven, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 skyroute
WORKDIR /app
COPY --from=build /src/target/skyroute-api-*.jar app.jar
USER skyroute
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

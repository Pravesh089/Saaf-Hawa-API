# Multi-stage build for the Saaf Hawa API.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1001 saafhawa && mkdir -p /app/data/archive && chown -R saafhawa /app
COPY --from=build /build/target/saaf-hawa-api-*.jar /app/app.jar
USER saafhawa
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

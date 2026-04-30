# Build stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache dependencies first
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml pom.xml
RUN chmod +x ./mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

# Copy sources and build jar
COPY src src
RUN ./mvnw -q clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

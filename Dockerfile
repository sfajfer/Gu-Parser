# STAGE 1: Build
# Use a newer Maven image that supports the latest JDKs
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# COPY COMMANDS REMAIN THE SAME...
COPY pom.xml .
COPY src ./src

# RUN COMMAND REMAINS THE SAME...
RUN mvn clean package -DskipTests

# STAGE 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
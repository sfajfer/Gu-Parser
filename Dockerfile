# STAGE 1: Build the application
# We use a Maven image that includes JDK 21 to compile the code
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src

# Run the maven build to create the .jar file INSIDE the cloud container
# This is why you don't need a local .jar file!
RUN mvn clean package -DskipTests

# STAGE 2: Run the application
# We use a much smaller "JRE" image just to run the compiled code
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy only the built .jar from the previous "build" stage
COPY --from=build /app/target/*.jar app.jar

# Tell Render which port the app runs on
EXPOSE 8080

# The command to start your Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
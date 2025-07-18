# ----------- Stage 1: Build the application -----------
FROM gradle:8.7.0-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle build -x test

# ----------- Stage 2: Run the application -----------
FROM openjdk:21-jdk-slim
WORKDIR /app
# Copy the built jar from the previous stage
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
# Expose the correct port (8082)
EXPOSE 8082
# Use a non-root user for security
RUN useradd -ms /bin/bash appuser && chown appuser:appuser /app/app.jar
USER appuser
# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


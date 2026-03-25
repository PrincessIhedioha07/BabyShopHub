# Stage 1: Build the application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compile the code, then immediately delete the plain jar so Docker doesn't get confused
RUN mvn clean package -DskipTests && rm -f target/*-plain.jar

# Stage 2: Run the application
FROM openjdk:17.0.1-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

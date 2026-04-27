FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/student-feedback-backend-1.0.0.jar app.jar

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java -Xms300m -Xmx300m -Dserver.port=${PORT:-10000} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-render} -jar app.jar"]

FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY backend/pom.xml ./pom.xml
COPY backend/apps/api/pom.xml ./apps/api/pom.xml
COPY backend/apps/api/src ./apps/api/src

RUN mvn -B -ntp -pl apps/api -am clean package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/apps/api/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

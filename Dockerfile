# AI Compute Platform - 多阶段构建，最终镜像基于 openjdk11
FROM maven:3.8-eclipse-temurin-11-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

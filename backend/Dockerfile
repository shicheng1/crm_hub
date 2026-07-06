FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:8-jre-alpine

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC" \
    SPRING_PROFILES_ACTIVE=prod

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /opt/app/logs \
    && chown -R app:app /opt/app

WORKDIR /opt/app
COPY --from=builder /build/target/order-approval-1.0.0.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

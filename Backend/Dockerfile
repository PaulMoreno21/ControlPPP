FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /source
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon test bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl font-dejavu && addgroup -g 10001 app && adduser -D -u 10001 -G app app
WORKDIR /app
COPY --from=build /source/build/libs/practicas-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]

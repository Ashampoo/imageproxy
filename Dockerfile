FROM amazoncorretto:22 AS BUILD_STAGE
WORKDIR /tmp
COPY .git .git
COPY gradle gradle
COPY build.gradle.kts gradle.properties settings.gradle.kts gradlew ./
COPY src src
RUN ./gradlew --no-daemon --info buildFatJar

FROM amazoncorretto:22-alpine
RUN mkdir /app
COPY --from=BUILD_STAGE /tmp/build/libs/*-all.jar /app/ktor-server.jar
EXPOSE 8080:8080
ENTRYPOINT ["java","-Xlog:gc+init","-XX:+PrintCommandLineFlags","-jar","/app/ktor-server.jar"]

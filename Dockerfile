FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY src ./src
COPY VERSION LICENSE AUTHORS.md ./
RUN mkdir -p classes/META-INF && find src -name '*.java' -print > sources.txt \
    && javac --release 17 -encoding UTF-8 -Xlint:all -d classes @sources.txt \
    && cp LICENSE AUTHORS.md classes/META-INF/ \
    && mkdir -p classes/Files/Images \
    && cp src/Files/Images/com.nozhaco.quoridor_512x512.png classes/Files/Images/ \
    && version="$(tr -d '\r\n' < VERSION)" \
    && printf 'version=%s\n' "$version" > classes/app-version.properties \
    && printf 'Implementation-Title: Quoridor\nImplementation-Version: %s\n' "$version" > manifest.mf \
    && jar --create --file Quoridor.jar --main-class Main --manifest manifest.mf -C classes . \
    && jar --create --file Quoridor-server.jar --main-class Online.ServerMain --manifest manifest.mf -C classes .

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/Quoridor.jar /build/Quoridor-server.jar /build/VERSION /build/LICENSE /build/AUTHORS.md ./
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "-Xms32m", "-Xmx256m", "-jar", "/app/Quoridor-server.jar"]

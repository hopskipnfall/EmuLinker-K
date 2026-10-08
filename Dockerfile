# Multi-stage build: compile with the project's Gradle wrapper, then run on a slim JRE image as an
# unprivileged user. Build with `docker build -t emulinker-k .` and run with
# `docker run -p 27888:27888/udp emulinker-k`.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon jar -PprodBuild=true

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 emulinker
WORKDIR /home/emulinker
COPY --from=build /app/emulinker/build/libs/emulinker-k-*.jar lib/emulinker-k.jar
# Same default configuration as the release zip. Mount your own directory over conf/ to customize.
COPY release/emulinker.cfg release/log4j2.properties release/language.properties release/access.cfg conf/
RUN chown -R emulinker:emulinker /home/emulinker
USER emulinker
EXPOSE 27888/udp
# Same JVM settings as release/start-server.sh. conf/ must come first on the classpath.
CMD ["java", "-Xms64m", "-Xmx256m", "-XX:+UseSerialGC", "-cp", "conf:lib/emulinker-k.jar", "org.emulinker.kaillera.pico.ServerMainKt"]

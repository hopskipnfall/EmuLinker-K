ARG GRADLE_VERSION=8.1.1
ARG JDK_VERSION=17

FROM gradle:${GRADLE_VERSION}-jdk${JDK_VERSION}-jammy as develop

LABEL org.opencontainers.image.authors="hopskipnfall"
LABEL org.opencontainers.image.source="https://github.com/hopskipnfall/EmuLinker-K"

ARG JDK_VERSION
ARG KOTLIN_DEBUG_ADAPTER=https://github.com/fwcd/kotlin-debug-adapter.git
ARG KOTLIN_LANGUAGE_SERVER=https://github.com/fwcd/kotlin-language-server.git

USER gradle

# build kotlin-language-server and kotlin-debug-adapter directly
# kotlin-debug-adapter from the vscode extension does not seem
# to work correctly with external dependencies
WORKDIR /home/gradle

# hadolint ignore=DL3003
RUN git clone ${KOTLIN_DEBUG_ADAPTER} \
    && mkdir -p bin \
    && (cd kotlin-debug-adapter && ./gradlew :adapter:installDist -PjavaVersion=${JDK_VERSION}) \
    && ln -sf ~/kotlin-debug-adapter/adapter/build/install/adapter/bin/kotlin-debug-adapter bin/

# hadolint ignore=DL3003
RUN git clone ${KOTLIN_LANGUAGE_SERVER} \
    && mkdir -p bin \
    && (cd kotlin-language-server && ./gradlew :server:installDist -PjavaVersion=${JDK_VERSION}) \
    && ln -sf ~/kotlin-language-server/server/build/install/server/bin/kotlin-language-server bin/

WORKDIR /app

# miscellaneous fixes
RUN git config --global --add safe.directory /app

ENTRYPOINT []

# Android build image for Audio Garage.
# JDK 17 + Android command-line tools + the exact SDK packages matching compileSdk.
# The SDK is baked into the image (reproducible, cached as a layer); the Gradle
# cache lives in a named volume (see docker-compose.yml).
ARG BASE_IMAGE=eclipse-temurin:17-jdk-jammy
FROM ${BASE_IMAGE}

# Pinned SDK inputs. Bump together with compileSdk / AGP.
ARG CMDLINE_TOOLS_BUILD=16111833
ARG CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
ARG ANDROID_PLATFORM=android-37.0
ARG ANDROID_BUILD_TOOLS=37.0.0
# Optional JVM options for sdkmanager (e.g. -Dhttps.proxyHost=... behind a proxy).
ARG SDKMANAGER_OPTS=""

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle-cache \
    ANDROID_USER_HOME=/home/builder/.android \
    HOME=/home/builder \
    LANG=C.UTF-8
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

# Optional extra CA certificates (corporate/sandbox TLS proxy). Drop *.crt files
# into docker/certs/ (git-ignored); nothing happens when the folder is empty.
COPY certs/ /tmp/extra-certs/
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends unzip ca-certificates curl; \
    rm -rf /var/lib/apt/lists/*; \
    for c in /tmp/extra-certs/*.crt; do \
      [ -f "$c" ] || continue; \
      cp "$c" /usr/local/share/ca-certificates/; \
      # keytool imports only the first certificate of a file: split bundles first.
      awk -v p="/tmp/split-$(basename "$c" .crt)-" '/BEGIN CERT/{n++} n{print > (p n ".pem")}' "$c"; \
    done; \
    for s in /tmp/split-*.pem; do \
      [ -f "$s" ] || continue; \
      keytool -importcert -noprompt -cacerts -storepass changeit -alias "extra-$(basename "$s" .pem)" -file "$s" > /dev/null 2>&1 || true; \
    done; \
    rm -f /tmp/split-*.pem; \
    update-ca-certificates; \
    rm -rf /tmp/extra-certs

RUN set -eux; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools"; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"; \
    echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c -; \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline; \
    mv /tmp/cmdline/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"; \
    rm -rf /tmp/cmdline /tmp/cmdline-tools.zip; \
    export SDKMANAGER_OPTS="${SDKMANAGER_OPTS}"; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager --install \
      "platform-tools" \
      "platforms;${ANDROID_PLATFORM}" \
      "build-tools;${ANDROID_BUILD_TOOLS}"; \
    chmod -R a+rX "${ANDROID_HOME}"

# The container runs with the host UID/GID (arbitrary, unknown at build time):
# make HOME and the Gradle cache mount point writable by anyone. A fresh named
# volume inherits these permissions.
RUN mkdir -p /home/builder/.android /gradle-cache /workspace \
 && chmod 1777 /home/builder /home/builder/.android /gradle-cache

WORKDIR /workspace

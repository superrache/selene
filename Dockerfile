# Development image for Sélène (Android, Kotlin, Gradle) + Claude Code.
#
# Built for linux/amd64 on purpose: AGP downloads aapt2 from Maven and the
# Linux artifact only exists for x86_64. On Apple Silicon, OrbStack / Docker
# Desktop run this image through Rosetta, which is fast enough for Gradle.
#
# Build:  docker build --platform linux/amd64 -t selene-dev docker/
# Run:    docker run -it --platform linux/amd64 \
#           -v "$PWD":/workspace -v selene-gradle:/home/node/.gradle \
#           -w /workspace selene-dev
#
# Instrumented tests (connectedDebugAndroidTest) need a device: there is no
# KVM here, so no emulator. Use a host emulator or phone via adb over TCP,
# e.g. `adb connect host.docker.internal:5555`.

FROM --platform=linux/amd64 node:22-bookworm

ARG CMDLINE_TOOLS_BUILD=16111833
ARG CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60
ARG ANDROID_PLATFORM=android-37.0
ARG ANDROID_BUILD_TOOLS=37.0.0
ARG JDK_PACKAGE=temurin-21-jdk

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 \
    GRADLE_OPTS="-Dorg.gradle.daemon=true -Dorg.gradle.jvmargs=-Xmx4g"
ENV PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# JDK (Eclipse Temurin, Adoptium apt repo) + basic tools
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget apt-transport-https gpg ca-certificates unzip file \
 && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg \
 && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" \
      > /etc/apt/sources.list.d/adoptium.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends ${JDK_PACKAGE} \
 && rm -rf /var/lib/apt/lists/*

# Android SDK command-line tools (checksum from repository2-3.xml)
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && cd /tmp \
 && wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip -O cmdline-tools.zip \
 && echo "${CMDLINE_TOOLS_SHA1}  cmdline-tools.zip" | sha1sum -c - \
 && unzip -q cmdline-tools.zip \
 && mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm cmdline-tools.zip

# SDK packages + licences
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install \
      "platform-tools" \
      "platforms;${ANDROID_PLATFORM}" \
      "build-tools;${ANDROID_BUILD_TOOLS}" \
 && chown -R node:node $ANDROID_HOME

# Claude Code
RUN npm install -g @anthropic-ai/claude-code

USER node
RUN mkdir -p /home/node/.gradle /home/node/.android
WORKDIR /workspace

CMD ["bash"]
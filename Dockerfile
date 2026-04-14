# --- Stage 1: Build the Android APK ---
FROM eclipse-temurin:17-jdk-focal AS build

# Set environment variables
ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Install dependencies
RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

# Install Android SDK
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm cmdline-tools.zip

# Accept licenses
RUN yes | sdkmanager --licenses

# Copy project files
WORKDIR /app
COPY . .

# Build the APK
RUN ./gradlew assembleDebug --no-daemon

# --- Stage 2: Serve the APK via Nginx ---
FROM nginx:alpine

# Copy the built APK from the build stage
COPY --from=build /app/app/build/outputs/apk/debug/app-debug.apk /usr/share/nginx/html/prism-dietary-copilot.apk

# Create a simple index.html to allow direct browsing
RUN echo '<html><body><h1>Prism Build Artifacts</h1><a href="/prism-dietary-copilot.apk">Download APK</a></body></html>' > /usr/share/nginx/html/index.html

# Expose port (Cloud Run uses 8080 by default, Nginx uses 80)
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

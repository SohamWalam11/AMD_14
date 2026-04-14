plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.prism"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prism"
        minSdk = 31          // AICore requires API 31+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // CameraX
    val cameraVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // Guava (ListenableFuture for CameraX)
    implementation("com.google.guava:guava:31.1-android")

    // Proto DataStore
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:4.28.3")

    // Custom GCP Inference
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Guava (for ListenableFuture)
    implementation("com.google.guava:guava:31.1-android")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

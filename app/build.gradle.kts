plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.minimalistlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.minimalistlauncher"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // --- SIGNING CONFIG (Kotlin DSL) ---
    // read properties from gradle.properties (project or ~/.gradle/gradle.properties)
    val releaseStoreFileProp: String? = project.findProperty("RELEASE_STORE_FILE") as String?
    val releaseStorePasswordProp: String? = project.findProperty("RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAliasProp: String? = project.findProperty("RELEASE_KEY_ALIAS") as String?
    val releaseKeyPasswordProp: String? = project.findProperty("RELEASE_KEY_PASSWORD") as String?

    signingConfigs {
        // create only if we have required properties; still define a config so Gradle can reference it safely
        create("release") {
            if (!releaseStoreFileProp.isNullOrBlank()) {
                // storeFile expects a File
                storeFile = File(releaseStoreFileProp)
            }
            if (!releaseStorePasswordProp.isNullOrBlank()) {
                storePassword = releaseStorePasswordProp
            }
            if (!releaseKeyAliasProp.isNullOrBlank()) {
                keyAlias = releaseKeyAliasProp
            }
            if (!releaseKeyPasswordProp.isNullOrBlank()) {
                keyPassword = releaseKeyPasswordProp
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    // Use Compose compiler version compatible with Kotlin 1.9.x
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }


}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // new patch
    implementation("androidx.activity:activity-compose:1.8.0")

    // BOM to manage Compose library versions consistently
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))

    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.android.material:material:1.9.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Compose test dependencies (BOM controlled)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

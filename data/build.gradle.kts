import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")

    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.sss.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val localProperties = Properties().apply {
            load(File("local.properties").inputStream())
        }

        val naverClientId: String? = localProperties["naver_client_id"] as String?
        val naverClientPw: String? = localProperties["naver_client_pw"] as String?

        if (naverClientId != null) {
            buildConfigField("String", "naver_client_id", naverClientId)
        }

        if (naverClientPw != null) {
            buildConfigField("String", "naver_client_pw", naverClientPw)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        buildConfig = true
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {

    implementation(project(":domain"))

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // room
    implementation("androidx.room:room-runtime:2.6.0")
    ksp("androidx.room:room-compiler:2.6.0")

    // retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // google drive
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.http-client:google-http-client-gson:1.26.0")
    implementation("com.google.api-client:google-api-client-android:1.26.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev136-1.25.0")

    // firebase auth
    implementation("com.google.firebase:firebase-auth:22.3.0")

    // Preferences Data Store
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-preferences-core:1.0.0")
    implementation("androidx.datastore:datastore-core:1.0.0")

    // test
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-agent:1.13.8")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("io.mockk:mockk-agent:1.13.8")

    // hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
}
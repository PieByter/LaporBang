plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("androidx.navigation.safeargs")
    id ("de.undercouch.download")
}

android {
    namespace = "com.xeraphion.laporbang"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xeraphion.laporbang"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAPS_API_KEY", "\"AIzaSyCeocl50YzPPAfVAeZL-HnB48gTFb2YV2E\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        mlModelBinding = true
        //noinspection DataBindingWithoutKapt
        dataBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    configurations.all {
        resolutionStrategy {
            force("org.hamcrest:hamcrest-core:2.2")
        }
    }

    androidResources {
        noCompress("tflite")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.object1.detection.common)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.object1.detection)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera2)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Core Android dependencies
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)

    // Material Components
    implementation(libs.material)

    // RecyclerView and ViewPager2
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)

    // Shimmer for loading effects
    implementation(libs.shimmer)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Retrofit and OkHttp for networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(platform(libs.okhttp.bom))

    // Glide for image loading
    implementation(libs.glide)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.coordinatorlayout)
    ksp(libs.compiler)
    implementation(libs.rive.android)

    // Firebase Firestore
    implementation(libs.firebase.firestore.ktx)

    // Google Play Services
    implementation(libs.play.services.maps)
    implementation("com.google.maps.android:android-maps-utils:2.0.1")
    implementation(libs.play.services.location)

    // Paging library
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.room.paging)

    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // EXIF interface
    implementation(libs.androidx.exifinterface)

    // SwipeRefreshLayout
    implementation(libs.androidx.swiperefreshlayout)

    // CircleImageView
    implementation(libs.circleimageview)

    // Navigation components
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Dependency Injection with Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.androidx.activity.ktx)
    testImplementation(libs.android.async.http)
    testImplementation(libs.truth)

    // Android Test dependencies
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.idling.resource)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.hamcrest.library)
    androidTestImplementation(libs.mockito.android)


    // CameraX dependencies
    implementation(libs.androidx.camera.core.v140)

    // Guava for ListenableFuture
    implementation(libs.guava)

//
//    implementation(libs.tensorflow.lite.support)
//    implementation(libs.tensorflow.lite.metadata)
//    implementation(libs.tensorflow.lite.gpu)

//    implementation("org.tensorflow:tensorflow-lite:2.17.0")
//    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")
//    implementation("org.tensorflow:tensorflow-lite-metadata:0.5.0")
//
//    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
//    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.17.0")
//    implementation("org.tensorflow:tensorflow-lite-api:2.17.0")
//    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
//    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("com.google.ai.edge.litert:litert-gpu-api:1.2.0")

    implementation("org.yaml:snakeyaml:1.29")
    // Maps SDK for Android KTX Library
    implementation ("com.google.maps.android:maps-ktx:3.0.0")

    // Maps SDK for Android Utility Library KTX Library
    implementation("com.google.maps.android:maps-utils-ktx:3.0.0")

}
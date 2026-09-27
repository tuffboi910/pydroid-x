plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.arm.aichat"
    compileSdk = 35
    ndkVersion = "29.0.13113456"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            arguments += listOf("-DBUILD_SHARED_LIBS=ON", "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_COMMON=ON", "-DLLAMA_OPENSSL=OFF", "-DGGML_NATIVE=OFF", "-DGGML_BACKEND_DL=OFF", "-DGGML_CPU_ALL_VARIANTS=OFF", "-DGGML_LLAMAFILE=OFF", "-DGGML_OPENMP=OFF", "-DGGML_CPU_KLEIDIAI=OFF")
        } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }

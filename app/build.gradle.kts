plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// la app de música se compila en el NAS: su número de versión lo lleva el NAS
val musicaBuild = (System.getenv("MUSICA_BUILD") ?: "1").toInt()
val controlBuild = (System.getenv("CONTROL_BUILD") ?: "1").toInt()
// clave para actualizar fuera de casa: solo existe en el NAS (no se sube a GitHub)
val nasKey = System.getenv("NEBULA_KEY") ?: ""

android {
    namespace = "com.tony.explorador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tony.explorador"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        buildConfigField("String", "NAS_KEY", "\"$nasKey\"")
        buildConfigField("String", "NAS_NET", "\"\"")
    }

    signingConfigs {
        create("tony") {
            storeFile = file("tony.keystore")
            storePassword = "explorador"
            keyAlias = "tony"
            keyPassword = "explorador"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tony")
        }
        debug {
            signingConfig = signingConfigs.getByName("tony")
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
        buildConfig = true
    }
    flavorDimensions += "app"
    productFlavors {
        // DriveNebula: explorador + todo (se compila en GitHub)
        create("nebula") { dimension = "app" }
        // Nebula Música: solo el reproductor (se compila en el NAS)
        create("musica") {
            dimension = "app"
            applicationIdSuffix = ".musica"
            versionCode = musicaBuild
            versionName = "1.0.$musicaBuild"
            buildConfigField("String", "NAS_NET", "\"https://nas-claude.tailc36e88.ts.net:10000/musica\"")
            // solo para teléfonos de 64 bits (todos los actuales): sin las piezas para emuladores y celulares viejos
            ndk { abiFilters += "arm64-v8a" }
        }
        // Control Nebula: solo el control remoto del NAS (se compila en el NAS)
        create("control") {
            dimension = "app"
            applicationIdSuffix = ".control"
            versionCode = controlBuild
            versionName = "1.0.$controlBuild"
            buildConfigField("String", "NAS_NET", "\"https://nas-claude.tailc36e88.ts.net:10000/control\"")
            ndk { abiFilters += "arm64-v8a" }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

android {
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    // comprimidos: 7-Zip para abrir/extraer (zip, rar, rar5, 7z, tar, gz…) y Commons Compress para crear 7z y tar.gz
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.02")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    // mandar a pantallas con Chromecast / Google Cast
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // editar PDF: anotaciones, firmas y páginas (girar, borrar, unir)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") { exclude(group = "org.bouncycastle") }
}

// Nebula Música no usa la búsqueda de texto en fotos: fuera esas piezas (unos 12 MB)
androidComponents {
    onVariants(selector().withFlavor("app" to "musica")) { v ->
        v.packaging.jniLibs.excludes.add("**/libmlkit_google_ocr_pipeline.so")
    }
    // DriveNebula ya no trae el reproductor: sin el traductor de letras (pesa ~50 MB entre todos los procesadores)
    onVariants(selector().withFlavor("app" to "nebula")) { v ->
        v.packaging.jniLibs.excludes.add("**/libtranslate_jni.so")
    }
    // Control Nebula tampoco traduce letras
    onVariants(selector().withFlavor("app" to "control")) { v ->
        v.packaging.jniLibs.excludes.add("**/libmlkit_google_ocr_pipeline.so")
        v.packaging.jniLibs.excludes.add("**/libtranslate_jni.so")
    }
}

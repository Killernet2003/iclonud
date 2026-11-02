import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.iclonud.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.iclonud.bridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        vectorDrawables { useSupportLibrary = true }
    }

    // La llave de publicación vive fuera del repositorio. Ruta configurable con
    // -Piclonud.llave=/ruta/llave.properties; si no existe, el release sale sin firmar.
    val llave = file(
        (project.findProperty("iclonud.llave") as String?)
            ?: "${System.getProperty("user.home")}/proyectos/iclonud-llave/llave.properties"
    )
    if (llave.exists()) {
        val p = Properties().apply { llave.inputStream().use { load(it) } }
        signingConfigs {
            create("publicacion") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (llave.exists()) signingConfig = signingConfigs.getByName("publicacion")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{NOTICE.md,LICENSE.md}"
            // Jakarta Mail trae descriptores duplicados que hay que fusionar/excluir
            pickFirsts += "META-INF/mailcap"
            pickFirsts += "META-INF/javamail.providers"
            pickFirsts += "META-INF/javamail.address.map"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Pestañas personalizadas de Chrome para iCloud.com (Notas, Fotos, Drive…)
    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    // Almacenamiento cifrado de la contraseña específica de app
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTTP para CalDAV / CardDAV (PROPFIND, REPORT, PUT)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // IMAP / SMTP (puerto de Jakarta/JavaMail para Android)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}

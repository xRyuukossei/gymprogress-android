import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Dados da chave de assinatura da versão release. Ficam em "keystore.properties" na raiz do
// projeto, que NÃO vai para o Git (senhas). Sem esse arquivo, a release sai sem assinatura.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "br.com.ryuukossei.gymprogress"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.ryuukossei.gymprogress"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (!keystoreProps.isEmpty) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A versão de desenvolvimento instala como outro app ("GymProgress Dev"), com
            // dados próprios: testar no Android Studio nunca mexe nos dados do app de uso real.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (!keystoreProps.isEmpty) signingConfig = signingConfigs.getByName("release")
            // Otimização/encolhimento do R8 desligado: com ele a release fechava ao abrir
            // (a debug, que não usa R8, funciona). Para um app pessoal e pequeno, o ganho
            // de tamanho não compensa o risco.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Esquema do banco local versionado no Git (base para migrations futuras).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

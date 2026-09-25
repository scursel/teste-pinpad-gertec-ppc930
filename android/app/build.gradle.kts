plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.scursel.pinpadppc930"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.scursel.pinpadppc930"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // assinatura release: a chave fica FORA do repositorio. Defina as variaveis
    // PINPAD_KEYSTORE (caminho do .jks), PINPAD_KEYSTORE_PASS, PINPAD_KEY_ALIAS e
    // PINPAD_KEY_PASS e rode ./gradlew assembleRelease. Sem elas o release sai sem assinatura.
    val keystore = System.getenv("PINPAD_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("PINPAD_KEYSTORE_PASS")
                keyAlias = System.getenv("PINPAD_KEY_ALIAS")
                keyPassword = System.getenv("PINPAD_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

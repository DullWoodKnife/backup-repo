import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 读取版本号配置
val versionPropsFile = file("${rootDir}/version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
}
val appVersionName = versionProps.getProperty("VERSION_NAME", "1.0.0")
val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()

android {
    namespace = "com.example.databackup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.databackup"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // GitHub Token: 从环境变量或 local.properties 读取，避免硬编码在源码中
        val githubToken = providers.gradleProperty("GITHUB_TOKEN").orNull
            ?: System.getenv("GITHUB_TOKEN")
            ?: ""
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

// 版本号自动递增任务
tasks.register("incrementVersion") {
    group = "versioning"
    description = "递增版本号（versionCode 和 versionName 最后一位各 +1）"
    doLast {
        val props = Properties()
        if (versionPropsFile.exists()) {
            props.load(versionPropsFile.inputStream())
        }
        val currentCode = props.getProperty("VERSION_CODE", "1").toInt()
        val currentName = props.getProperty("VERSION_NAME", "1.0.0")

        // 递增 versionCode
        val newCode = currentCode + 1
        props.setProperty("VERSION_CODE", newCode.toString())

        // 递增 versionName 最后一位
        val nameParts = currentName.split(".")
        val newPatch = if (nameParts.size >= 3) nameParts[2].toInt() + 1 else 1
        val newName = if (nameParts.size >= 2) {
            "${nameParts[0]}.${nameParts[1]}.$newPatch"
        } else {
            "1.0.$newPatch"
        }
        props.setProperty("VERSION_NAME", newName)

        versionPropsFile.writer().use { props.store(it, "Version configuration") }
        println("版本号已更新: $currentName ($currentCode) -> $newName ($newCode)")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.documentfile)

    // Encrypted SharedPreferences for secure token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Apache POI for Word document processing (.docx)
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5")
    implementation("org.apache.xmlbeans:xmlbeans:5.2.1")
    // Apache POI scratchpad for .doc (OLE2 / Word 97-2003) support
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

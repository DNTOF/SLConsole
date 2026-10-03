import java.util.Base64
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 官方签名来源(按优先级):
 * 1. 环境变量 SLCONSOLE_KEYSTORE_BASE64 / SLCONSOLE_KEYSTORE_PASSWORD / SLCONSOLE_KEY_ALIAS / SLCONSOLE_KEY_PASSWORD(CI 用)
 * 2. SLCONSOLE_SIGNING_PROPS(Gradle 属性或环境变量)指向的 properties 文件,
 *    默认 ${user.home}/.slconsole-signing/keystore.properties
 * 都没有时退回默认 debug 签名,装出来的包会被识别为非官方版本。
 */
data class OfficialSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun loadOfficialSigning(): OfficialSigning? {
    val env = System.getenv()
    val b64 = env["SLCONSOLE_KEYSTORE_BASE64"]?.trim().orEmpty()
    if (b64.isNotEmpty()) {
        val storePassword = env["SLCONSOLE_KEYSTORE_PASSWORD"].orEmpty()
        val keyAlias = env["SLCONSOLE_KEY_ALIAS"].orEmpty()
        val keyPassword = env["SLCONSOLE_KEY_PASSWORD"]?.takeIf { it.isNotEmpty() } ?: storePassword
        if (storePassword.isNotEmpty() && keyAlias.isNotEmpty()) {
            val out = project.layout.buildDirectory.file("signing/slconsole-release.jks").get().asFile
            out.parentFile.mkdirs()
            out.writeBytes(Base64.getMimeDecoder().decode(b64))
            return OfficialSigning(out, storePassword, keyAlias, keyPassword)
        }
        project.logger.warn("SLConsole: SLCONSOLE_KEYSTORE_BASE64 已设置,但缺少 SLCONSOLE_KEYSTORE_PASSWORD 或 SLCONSOLE_KEY_ALIAS,改用 properties 文件。")
    }
    val propsPath = (project.findProperty("SLCONSOLE_SIGNING_PROPS") as String?)?.takeIf { it.isNotBlank() }
        ?: env["SLCONSOLE_SIGNING_PROPS"]?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.slconsole-signing/keystore.properties"
    val propsFile = File(propsPath)
    if (!propsFile.isFile) return null
    val props = Properties().apply { propsFile.inputStream().use { load(it) } }
    val storePath = props.getProperty("storeFile")?.trim().orEmpty()
    val storePassword = props.getProperty("storePassword").orEmpty()
    val keyAlias = props.getProperty("keyAlias")?.trim().orEmpty()
    val keyPassword = props.getProperty("keyPassword")?.takeIf { it.isNotEmpty() } ?: storePassword
    if (storePath.isEmpty() || storePassword.isEmpty() || keyAlias.isEmpty()) {
        project.logger.warn("SLConsole: $propsPath 缺少 storeFile / storePassword / keyAlias。")
        return null
    }
    val store = File(storePath).let { if (it.isAbsolute) it else File(propsFile.parentFile, storePath) }
    if (!store.isFile) {
        project.logger.warn("SLConsole: 找不到签名文件 $store。")
        return null
    }
    return OfficialSigning(store, storePassword, keyAlias, keyPassword)
}

val officialSigning = loadOfficialSigning()
if (officialSigning == null) {
    project.logger.warn("SLConsole: 未找到官方签名配置,本次构建使用默认 debug 签名,安装后会提示「非官方版本」。")
}

android {
    namespace = "com.dntof.slconsole"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dntof.slconsole"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.1.2"
    }

    signingConfigs {
        if (officialSigning != null) {
            create("release") {
                storeFile = officialSigning.storeFile
                storePassword = officialSigning.storePassword
                keyAlias = officialSigning.keyAlias
                keyPassword = officialSigning.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        // 有官方签名时 debug 也用它,本地调试包同样是官方签名;没有就退回默认 debug 签名
        val signing = if (officialSigning != null) {
            signingConfigs.getByName("release")
        } else {
            signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kyant.backdrop)
    implementation(libs.clarity.compose)
    debugImplementation(libs.compose.ui.tooling)
}

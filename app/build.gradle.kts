import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing: read from keystore.properties at the project root (never committed).
// Without it, assembleRelease still works but produces an unsigned APK.
val releaseSigning: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { file ->
        val props = Properties()
        file.inputStream().use { stream -> props.load(stream) }
        props
    }

android {
    namespace = "app.selene"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.selene"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets the debug build live next to the released app on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.logging.enabled", "false")
                // Dev tool: ./gradlew testDebugUnitTest --tests '*ScreenshotTour' -Pselene.screenshots=/some/dir
                providers.gradleProperty("selene.screenshots").orNull?.let { test.systemProperty("selene.screenshots", it) }
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Version-bump hints need the network; versions are checked by hand (see CLAUDE.md).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

// APK file name: selene-<versionName>-<buildType>.apk
base {
    archivesName = "selene-${android.defaultConfig.versionName}"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ---------------------------------------------------------------------------
// Privacy guard: the merged manifest must never request INTERNET, and may only
// request the permissions listed below. Runs before unit tests, assemble and check.
// ---------------------------------------------------------------------------

val manifestAllowedPermissions = setOf(
    "android.permission.READ_CALENDAR",
    "android.permission.WRITE_CALENDAR",
    // Normal (install-time) permissions merged by androidx.biometric for the optional lock.
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT",
)

abstract class VerifyManifestPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val xml = mergedManifest.get().asFile.readText()
        val requested = Regex("""<uses-permission(?:-sdk-23)?\b[^>]*?android:name\s*=\s*"([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toSortedSet()
        if ("android.permission.INTERNET" in requested) {
            throw GradleException(
                "The merged manifest requests android.permission.INTERNET. " +
                    "Sélène must stay 100% offline: find the dependency that adds it and remove it.",
            )
        }
        val unexpected = requested - allowedPermissions.get()
        if (unexpected.isNotEmpty()) {
            throw GradleException("The merged manifest requests unexpected permissions: $unexpected")
        }
        report.get().asFile.writeText("Requested permissions: $requested\n")
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyManifestPermissionsTask>("verify${variantName}ManifestPermissions") {
            group = "verification"
            description = "Fails if the merged $variantName manifest requests INTERNET or any non-allowed permission."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowedPermissions.set(manifestAllowedPermissions)
            report.set(layout.buildDirectory.file("reports/manifest-permissions/${variant.name}.txt"))
        }
        tasks.matching {
            it.name == "assemble$variantName" || it.name == "test${variantName}UnitTest" || it.name == "check"
        }.configureEach { dependsOn(verify) }
    }
}

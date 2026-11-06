import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

internal fun VersionCatalog.string(alias: String): String = findVersion(alias).get().requiredVersion

/**
 * Shared Android settings for application and library modules.
 * AGP 9 compiles Kotlin itself (built-in Kotlin): no kotlin-android plugin.
 */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.int("compileSdk")
        // Pinned to the build-tools baked into the Docker image (no download at build time).
        buildToolsVersion = libs.string("buildTools")
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        lint.apply {
            abortOnError = true
            checkDependencies = false
            // A newer AndroidX/AGP release always exists; version bumps are deliberate, not lint-driven.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            // targetSdk 36 is deliberate: it matches the Android 16 reference device (Galaxy A16).
            disable += "OldTargetApi"
        }
    }
    // AGP 9 built-in Kotlin: kotlin.compilerOptions.jvmTarget defaults to
    // compileOptions.targetCompatibility, no extra configuration needed.
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(libs.int("jvmTarget"))
    }
}

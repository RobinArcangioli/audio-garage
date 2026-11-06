import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin/JVM module (no Android dependency: fast unit tests). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
    }
}

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin/JVM module (no Android dependency: fast unit tests). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Android lint also checks plain JVM modules.
        pluginManager.apply("com.android.lint")
        configureKotlinJvm()

        // The Makefile targets call Android task names on the whole build
        // (compileDebugSources, compileDebugUnitTestSources, testDebugUnitTest):
        // map them so JVM modules are compiled and tested too.
        tasks.register("compileDebugSources") { dependsOn("classes") }
        tasks.register("compileDebugUnitTestSources") { dependsOn("testClasses") }
        tasks.register("testDebugUnitTest") { dependsOn("test") }
    }
}

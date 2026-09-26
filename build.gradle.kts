// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}
// Keep Gradle build output outside the OneDrive-synced project folder.
// OneDrive holds files open while syncing, which makes Gradle's output cleanup on
// Windows fail with "java.io.IOException: Unable to delete directory ...\build\...".
// Output goes to %LOCALAPPDATA%\GradleBuilds\<rootProject>\<module> instead.
System.getenv("LOCALAPPDATA")?.let { localAppData ->
    val externalBuildRoot = file("$localAppData/GradleBuilds/${rootProject.name}")
    allprojects {
        val moduleDir = if (project == rootProject) "root" else project.name
        layout.buildDirectory.set(externalBuildRoot.resolve(moduleDir))
    }
}

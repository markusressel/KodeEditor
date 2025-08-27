import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "de.markusressel.kodeeditor.buildlogic"
version = "0.1.0"

java {
    // Up to Java 11 APIs are available through desugaring
    // https://developer.android.com/studio/write/java11-minimal-support-table
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "kodeeditor.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "kodeeditor.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "kodeeditor.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "kodeeditor.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryPublishing") {
            id = "kodeeditor.android.library.publishing"
            implementationClass = "AndroidLibraryPublishingConventionPlugin"
        }
        register("androidTest") {
            id = "kodeeditor.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidFlavors") {
            id = "kodeeditor.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
    }
}

plugins {
    id("kodeeditor.android.application")
    id("kodeeditor.android.application.compose")
    id("kodeeditor.android.application.flavors")
}

android {
    namespace = "de.markusressel.kodeeditor.demo"

    defaultConfig {
        applicationId = "de.markusressel.kodeeditor"
        versionCode = 1
        versionName = "5.0.0"
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val outputImpl = output as com.android.build.api.variant.impl.VariantOutputImpl
                outputImpl.outputFileName.set("KodeEditor_v${defaultConfig.versionName}_(${defaultConfig.versionCode}).apk")
            }
        }
    }
}

dependencies {
    implementation(project(":library"))

    // Syntax Highlighting
    implementation(libs.kodehighlighter.markdown)

    implementation(libs.kotlin.stdlib.jdk8)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.fuel)
    implementation(libs.fuel.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

plugins {
    id("kodeeditor.android.library")
    id("kodeeditor.android.library.compose")
    id("kodeeditor.android.library.publishing")
}

android {
    namespace = "de.markusressel.kodeeditor.library"

    // Compose UI tests run on the JVM with Robolectric
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
//    api 'org.jetbrains.kotlinx:kotlinx-coroutines-android:0.25.0'

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Syntax Highlighting
    api(libs.kodehighlighter.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

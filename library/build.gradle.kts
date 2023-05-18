plugins {
    id("kodeeditor.android.library")
    id("kodeeditor.android.library.compose")
    id("kodeeditor.android.library.publishing")
}

android {
    namespace = "de.markusressel.kodeeditor.library"
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
//    api 'org.jetbrains.kotlinx:kotlinx-coroutines-android:0.25.0'

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.6.0")

    implementation("androidx.compose.ui:ui:1.4.3")
    implementation("androidx.compose.material:material:1.4.3")

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // RxFlow
    implementation("io.github.reactivecircus.flowbinding:flowbinding-android:+")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

    // Syntax Highlighting
    val kodeHighlighterVersion = "master-SNAPSHOT"
    api("com.github.markusressel.KodeHighlighter:core:$kodeHighlighterVersion")

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Zoom Layout Container
    api("com.otaliastudios:zoomlayout:1.9.0")

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

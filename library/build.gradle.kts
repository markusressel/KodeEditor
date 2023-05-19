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

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // RxFlow
    implementation(libs.flowbinding.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Syntax Highlighting
    api(libs.kodehighlighter.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Zoom Layout Container
    api(libs.zoomlayout)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

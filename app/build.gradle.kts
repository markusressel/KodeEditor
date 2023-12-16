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
        versionName = "4.0.1"

        setProperty("archivesBaseName", "KodeEditor_v${versionName}_(${versionCode})")
    }
}

dependencies {
    implementation(project(":library"))

    // Syntax Highlighting
    implementation(libs.kodehighlighter.markdown)

    implementation(libs.kotlin.stdlib.jdk8)

    implementation(libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-compose:1.7.1")

    val fuelVersion = "2.3.1"
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-android:$fuelVersion")

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

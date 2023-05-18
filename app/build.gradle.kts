plugins {
    id("kodeeditor.android.application")
    id("kodeeditor.android.application.compose")
    id("kodeeditor.android.application.flavors")
}

android {
    defaultConfig {
        applicationId = "de.markusressel.kodeeditor"
        versionCode = 1
        versionName = "4.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", "KodeEditor_v${versionName}_(${versionCode})")
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.addAll(
                listOf("LICENSE.txt", "META-INF/DEPENDENCIES", "META-INF/ASL2.0", "META-INF/NOTICE", "META-INF/LICENSE")
            )
            pickFirsts.add("META-INF/proguard/androidx-annotations.pro")
        }
    }

    namespace = "de.markusressel.kodeeditor"
}

dependencies {
    implementation(project(":library"))

    // Syntax Highlighting
    val kodeHighlighterVersion = "master-SNAPSHOT"
    implementation("com.github.markusressel.KodeHighlighter:markdown:$kodeHighlighterVersion")

    implementation(libs.kotlin.stdlib.jdk8)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
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

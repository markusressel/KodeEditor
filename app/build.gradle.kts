import com.android.build.api.artifact.SingleArtifact

plugins {
    id("kodeeditor.android.application")
    id("kodeeditor.android.application.compose")
    id("kodeeditor.android.application.flavors")
}

// Android only installs an update with a higher version code, so CI uses its run number
val appVersionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toInt() ?: 1

/**
 * Sets the version code in the merged manifest. As the versionCode of the build it would also end up in BuildConfig, and
 * then each CI run would compile the whole app again and run R8, instead of taking them from the build cache.
 */
abstract class SetManifestVersionCode : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun run() {
        val manifest = mergedManifest.get().asFile.readText()
        val updated = manifest.replace(Regex("""android:versionCode="\d+""""), """android:versionCode="${versionCode.get()}"""")
        check(updated.contains("""android:versionCode="${versionCode.get()}"""")) { "The merged manifest has no version code" }
        updatedManifest.get().asFile.writeText(updated)
    }
}

android {
    namespace = "de.markusressel.kodeeditor.demo"

    defaultConfig {
        applicationId = "de.markusressel.kodeeditor"
        // Builds from CI are signed with another key than local ones, so they can not replace each other.
        val localBuild = providers.environmentVariable("GITHUB_ACTIONS").orNull == null
        if (localBuild) applicationIdSuffix = ".local"
        resValue("string", "app_name", if (localBuild) "KodeEditor DEV" else "KodeEditor")
        // the real one is set by SetManifestVersionCode, see there
        versionCode = 1
        versionName = "9.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // This app is only a demo and is not published in a store, so the key does not have to be secret. On CI
            // the debug key is .ci/debug.keystore, so all builds of the releases update each other.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidComponents {
        onVariants { variant ->
            val setVersionCode = tasks.register<SetManifestVersionCode>("set${variant.name.replaceFirstChar { it.uppercase() }}VersionCode") {
                versionCode.set(appVersionCode)
            }
            variant.artifacts.use(setVersionCode)
                .wiredWithFiles(SetManifestVersionCode::mergedManifest, SetManifestVersionCode::updatedManifest)
                .toTransform(SingleArtifact.MERGED_MANIFEST)

            variant.outputs.forEach { output ->
                val outputImpl = output as com.android.build.api.variant.impl.VariantOutputImpl
                outputImpl.outputFileName.set("KodeEditor_v${defaultConfig.versionName}_($appVersionCode).apk")
            }
        }
    }
}

dependencies {
    implementation(project(":library"))

    // Syntax Highlighting
    implementation(libs.kodehighlighter)

    implementation(libs.kotlin.stdlib.jdk8)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

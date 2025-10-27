import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import de.markusressel.kodeeditor.TARGET_SDK
import de.markusressel.kodeeditor.configureKotlinAndroid
import de.markusressel.kodeeditor.configureKotlinAndroidToolchain
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            group = "de.markusressel.kodeeditor.demo"

            configureKotlinAndroidToolchain()
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                dataBinding {
                    enable = true
                }
                viewBinding {
                    enable = true
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
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
            }
        }
    }

}
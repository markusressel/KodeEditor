import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create

class AndroidLibraryPublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                with(pluginManager) {
                    apply("maven-publish")
                }
                publishing {
                    singleVariant("release") {
                        group = "com.github.markusressel.KodeEditor"
                        withJavadocJar()
                        withSourcesJar()
                    }
                }
                configure<PublishingExtension> {
                    publications {
                        create("maven", MavenPublication::class) {
                            groupId = "com.github.markusressel.KodeEditor"
                            artifactId = target.name
                            version = "${target.version}"

                            artifact("$buildDir/outputs/aar/${target.name}-release.aar") {
                                builtBy(tasks.getByName("assemble"))
                            }
                        }
                    }
                    repositories {
                        mavenLocal()
                    }
                }
            }
        }
    }
}
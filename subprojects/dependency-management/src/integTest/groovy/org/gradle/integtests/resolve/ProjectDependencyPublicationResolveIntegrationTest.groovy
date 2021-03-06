/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import spock.lang.Issue

import static org.gradle.util.TextUtil.escapeString

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value="maven")
class ProjectDependencyPublicationResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Issue("gradle/gradle#12324")
    def "project dependency can resolve configuration from target project on publication"() {
        given:
        settingsFile.delete()
        settingsKotlinFile << """
            include("a")
            include("b")
        """
        and:
        buildFile.delete()
        buildKotlinFile << """
            allprojects {
                group = "com.acme.foo"
                version = "1.0"
            }
        """

        and:
        file("a/build.gradle.kts") << """
            plugins {
                `java-library`
                `maven-publish`
                id("client-server")
            }
            publishing {
                publications {
                    create<MavenPublication>("client") {
                        from(components["client"])
                        artifactId += "-client"
                    }
                    create<MavenPublication>("server") {
                        from(components["server"])
                        artifactId += "-server"
                    }
                }
                repositories {
                    maven { url = uri("${escapeString(mavenRepo.rootDir)}") }
                }
            }
            dependencies { server(project(path = ":b", configuration = "server")) }
        """

        file("b/build.gradle.kts") << """
            plugins {
                `java-library`
                `maven-publish`
                id("client-server")
            }

            publishing {
                publications {
                    create<MavenPublication>("client") {
                        from(components["client"])
                        artifactId += "-client"
                    }
                    create<MavenPublication>("server") {
                        from(components["server"])
                        artifactId += "-server"
                    }
                }
                repositories {
                    maven { url = uri("${escapeString(mavenRepo.rootDir)}") }
                }
            }
        """

        //Plugin creating the components
        and:
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
            }

            gradlePlugin {
                plugins {
                    register("clientServerPlugin") {
                        id = "client-server"
                        implementationClass = "TestPlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/kotlin/TestPlugin.kt") << """
            import org.gradle.api.Named
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.artifacts.Configuration
            import org.gradle.api.attributes.Category
            import org.gradle.api.attributes.Usage
            import org.gradle.api.component.AdhocComponentWithVariants
            import org.gradle.api.component.SoftwareComponentFactory
            import org.gradle.api.tasks.bundling.Jar
            import org.gradle.kotlin.dsl.register
            import javax.inject.Inject

            class TestPlugin @Inject constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project> {

                override fun apply(project: Project) = project.run {
                    createComponent("server")
                    createComponent("client")
                }

                private fun Project.createComponent(name : String) {
                    val config = createConfiguration(name)
                    attachArtifact(config)
                    configurePublication(config)
                    addVariantToExistingComponent(config)
                }

                private fun Project.configurePublication(config: Configuration) {
                    val adhocComponent = softwareComponentFactory.adhoc(config.name)
                    components.add(adhocComponent)
                    adhocComponent.addVariantsFromConfiguration(config) {
                        mapToMavenScope("runtime")
                    }
                }

                private fun Project.attachArtifact(config : Configuration) {
                    val jar = tasks.register<Jar>("\${config.name}Jar") {}
                    artifacts { add(config.name, jar) }
                }

                private fun Project.createConfiguration(configName : String): Configuration {
                    return configurations.create(configName) {
                        isCanBeConsumed = true
                        isCanBeResolved = true
                        attributes {
                            attribute(Category.CATEGORY_ATTRIBUTE, namedAttribute(Category.LIBRARY))
                            attribute(Usage.USAGE_ATTRIBUTE, namedAttribute(Usage.JAVA_RUNTIME))
                        }
                    }
                }

                private fun Project.addVariantToExistingComponent(outgoing: Configuration) {
                    val javaComponent = components.findByName("java") as AdhocComponentWithVariants
                    javaComponent.addVariantsFromConfiguration(outgoing) {
                        mapToMavenScope("runtime")
                    }
                }
            }

            inline fun <reified T : Named> Project.namedAttribute(value: String) = objects.named(T::class.java, value)
        """

        when:
        run "publishAllPublicationsToMavenRepository"

        then:
        def aClient = mavenRepo.module("com.acme.foo", "a-client", "1.0")
        aClient.assertPublished()
        def aServer = mavenRepo.module("com.acme.foo", "a-server", "1.0")
        aServer.assertPublished()
        def bClient = mavenRepo.module("com.acme.foo", "b-client", "1.0")
        bClient.assertPublished()
        def bServer = mavenRepo.module("com.acme.foo", "b-server", "1.0")
        bServer.assertPublished()
    }
}

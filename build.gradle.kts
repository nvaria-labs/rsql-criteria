import io.github.simonhauck.release.version.api.Version

plugins {
    `java-library`
    idea
    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.barfuin.gradle.jacocolog") version "3.1.0"
    id("io.github.simonhauck.release") version "1.4.0"
    id("org.spdx.sbom") version "0.9.0"
    id("dev.yumi.gradle.licenser") version "2.1.1"
    `maven-publish`
    jacoco
    antlr
}

group = "ch.nvaria.rsql"

val versionFile = layout.settingsDirectory.file("version.properties").asFile
version =  Version.fromPropertiesFile(versionFile)

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

release {
    checkForPreReleaseVersions.set(false)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "ch.nvaria.rsql"
            artifactId = "rsql-parser"

            from(components["java"])
            pom {
                name.set("rsql-parser")
                description.set("A Library to parse rsql")
                url.set("")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("tm")
                        name.set("Thomas Müllerleile")
                        email.set("thomas.muellerleile@nvaria.ch")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "maven-snapshots"
                url = uri(System.getenv("NEXUS_MVN_SNAPSHOTS"))
                credentials {
                    username = System.getenv("NEXUS_USER")
                    password = System.getenv("NEXUS_PW")
                }
            }
        }
        repositories {
            maven {
                name = "maven-release"
                url = uri(System.getenv("NEXUS_MVN_RELEASE"))
                credentials {
                    username = System.getenv("NEXUS_USER")
                    password = System.getenv("NEXUS_PW")
                }
            }
        }
    }
}


repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

sourceSets.configureEach {
    val generateGrammarSource = tasks.named(getTaskName("generate", "GrammarSource"))
    java.srcDir(generateGrammarSource.map { files() })
}

dependencies {
    antlr(libs.antlr.v4)
    implementation(libs.antlr.v4.runtime)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.date)

    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)

}

tasks.named<Test>("test") {

    jvmArgs("-XX:+EnableDynamicAgentLoading",  "-XX:+UnlockExperimentalVMOptions","-XX:+EnableJVMCI", "-javaagent:${mockitoAgent.asPath}")
    useJUnitPlatform()
}



tasks.generateGrammarSource {
    outputDirectory = file(layout.projectDirectory.dir("src/main/java/ch/nvaria/generated"))
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages", "-package" ,"ch.nvaria.generated")
}

tasks.named("test").configure { dependsOn("jar") }
tasks.named("checkLicenseMain").configure { dependsOn("generateGrammarSource") }

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
    dependsOn(tasks.test) // tests are required to run before generating the report
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("ch/nvaria/generated/*")
        }
    )
}

spdxSbom {
    targets {

        create("release") {
            outputFile.set(layout.buildDirectory.file("release.spdx.json"))
            description = "This library can be used to easily access bpmn models"

            scm {
                uri.set("https://nexus.nvaria.cloud")
                if (project.hasProperty("spdxRevision")) {
                    revision.set(project.property("spdxRevision").toString())
                }
            }
            document {
                name.set("Impulse Model BPMN")
                creator.set("Organization: Nvaria GmbH")
                namespace.set("https://nvaria.ch/spdx/1234-5678-9632-7412")
                packageSupplier.set("Organization: Nvaria GmbH")
            }

        }
    }
}


license {
    // Add a license header rule, at least one must be present.
    rule(file("codeformat/ASF-2.0"))

    // Exclude/include certain file types, defaults are provided to easily deal with Java/Kotlin projects.
    include("src/main/java/visitor/*.java") // Include Java files into the file resolution.
    exclude("**/*.properties") // Exclude properties files from the file resolution.
    exclude("src/main/java/ch/nvaria/generated/*")
    exclude("src/main/java/antlr/*")
}
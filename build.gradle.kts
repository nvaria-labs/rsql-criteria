import io.github.simonhauck.release.version.api.Version
import java.util.UUID

plugins {
    `java-library`
    idea
    id("com.github.ben-manes.versions") version "0.53.0"
    id("org.barfuin.gradle.jacocolog") version "4.0.2"
    id("io.github.simonhauck.release") version "1.5.1"
    id("org.spdx.sbom") version "0.10.0"
    id("dev.yumi.gradle.licenser") version "3.0.1"
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
            artifactId = "rsql-criteria"

            from(components["java"])
            artifact(layout.buildDirectory.file("release.spdx.json")) {
                classifier = "sbom"
                extension  = "spdx.json"
            }
            pom {
                name.set("rsql-criteria")
                description.set("A Library to parse rsql and create criteria API predicates")
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
    api(libs.jakarta.persistence.api)

    testImplementation(libs.hibernate.core)
    testImplementation(libs.h2)

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

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("jar") {
    dependsOn("spdxSbomForRelease")
    from(layout.buildDirectory.file("release.spdx.json")) {
        into("META-INF/sbom")
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn("spdxSbomForRelease")
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
            description = "This library can be used to create criteria api predicates from rsql"

            scm {
                uri.set("https://nexus.nvaria.cloud")
                revision.set(project.version.toString())
            }
            document {
                name.set("Rsql Criteria API generator")
                creator.set("Organization: Nvaria GmbH")
                namespace.set("https://nvaria.ch/spdx/${UUID.randomUUID()}")
                packageSupplier.set("Organization: Nvaria GmbH")
                uberPackage {
                    name.set("rsql-criteria-generator")
                    version.set(project.version.toString())
                    supplier.set("Organization: Nvaria GmbH")
                }
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
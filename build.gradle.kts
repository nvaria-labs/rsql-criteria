import io.github.simonhauck.release.version.api.Version
import java.util.UUID

plugins {
    `java-library`
    idea
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.jacocolog)
    alias(libs.plugins.release)
    alias(libs.plugins.spdx.sbom)
    alias(libs.plugins.licenser)
    `maven-publish`
    jacoco
    antlr
}

group = "ch.nvaria.rsql"

val versionFile = layout.settingsDirectory.file("version.properties").asFile
version =  Version.fromPropertiesFile(versionFile)

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
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

    // Hibernate's HibernateCriteriaBuilder.cast() emits a real SQL CAST (json_value
    // extraction is Hibernate-specific anyway). compileOnly keeps it off the runtime
    // classpath of consumers who supply their own Hibernate.
    compileOnly(libs.hibernate.core)

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
    // Generated into build/ (plugin default); packageName also lays out the package directories
    maxHeapSize = "64m"
    packageName = "ch.nvaria.rsql.internal.parser"
    arguments = arguments + listOf("-visitor", "-long-messages")
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
            exclude("ch/nvaria/rsql/internal/parser/*")
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
    include("**/*.java")
    exclude("**/*.properties") // Exclude properties files from the file resolution.
    exclude("ch/nvaria/rsql/internal/parser/**")
    exclude("src/main/java/antlr/*")
}
plugins {
    `java-library`
    idea
    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.barfuin.gradle.jacocolog") version "3.1.0"
    id("io.github.simonhauck.release") version "1.4.0"
    `maven-publish`
    jacoco
    antlr
}

group = "ch.nvaria"

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
            groupId = "ch.nvaria"
            artifactId = "rest-sql"

            from(components["java"])
            pom {
                name.set("rest-sql")
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
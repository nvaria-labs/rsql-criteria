import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import io.github.simonhauck.release.version.api.Version

plugins {
    `java-library`
    idea
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.jacocolog)
    alias(libs.plugins.release)
    alias(libs.plugins.spdx.sbom)
    alias(libs.plugins.maven.publish)
    jacoco
    antlr
}

group = "ch.nvaria"

val versionFile = layout.settingsDirectory.file("version.properties").asFile
version = Version.fromPropertiesFile(versionFile)

release {
    checkForPreReleaseVersions.set(false)
}

// Built with JDK 25, but runs on Java 21+. targetCompatibility also sets the Java version
// in the published Gradle module metadata, so consumers on 21 can resolve the library.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}


// Single source of truth for our own license: both the POM and the SBOM patch below
// read these, so the published metadata cannot drift apart. SPDX identifier form —
// it is what the SBOM requires, and it is valid as a POM license name too.
val licenseId = "Apache-2.0"
val licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
val copyrightHolder = "Nvaria GmbH"

// Public home of the project. Gitea is the source of truth; GitHub is its read-only mirror.
val projectUrl = "https://github.com/nvaria-labs/rsql-criteria"

// Commit the SBOM points to
val gitHash: String = providers.exec { commandLine("git", "rev-parse", "HEAD") }
    .standardOutput.asText.get().trim()


spdxSbom {
    onlyUseLocalLicenses.set(true)
    targets {
        create("release") {
            // Everything on the runtime classpath (test deps stay out; Hibernate is compileOnly).
            configurations.set(listOf("runtimeClasspath"))

            scm {
                tool.set("git")
                uri.set(projectUrl)
                revision.set(gitHash)
            }

            document {
                name.set("rsql-criteria")
                namespace.set("https://nvaria.ch/spdx/rsql-criteria/${project.version}")
                creator.set("Organization: Nvaria GmbH")
                packageSupplier.set("Organization: Nvaria GmbH")
            }
        }
    }
}

// Two things the plugin cannot express, patched into the finished document:
//
//  1. the license and download location of the root (project) package — hardcoded to
//     NOASSERTION, no DSL and no task-extension hook for it (dependency licenses come
//     from their POMs and are already correct);
//  2. the purls — the plugin stamps the resolving repository into every purl as a
//     ?repository_url= qualifier. Maven Central is the purl default, so it is redundant.
//
// Deliberately NOT done via the plugin's taskExtension.mapRepoUri: that property is
// @Internal, so changing the mapping would not invalidate the task and the SBOM would
// silently go stale. Mutating the declared @OutputFile here keeps caching honest.
tasks.named("spdxSbomForRelease") {
    doLast {
        val json = layout.buildDirectory.file("spdx/release.spdx.json").get().asFile
        @Suppress("UNCHECKED_CAST")
        val doc = groovy.json.JsonSlurper().parse(json) as MutableMap<String, Any>

        // The root package is whatever SPDXRef-DOCUMENT DESCRIBES; don't match on name.
        @Suppress("UNCHECKED_CAST")
        val relationships = doc["relationships"] as List<Map<String, Any>>
        val rootId = relationships.single {
            it["spdxElementId"] == "SPDXRef-DOCUMENT" && it["relationshipType"] == "DESCRIBES"
        }["relatedSpdxElement"]

        @Suppress("UNCHECKED_CAST")
        val packages = doc["packages"] as List<MutableMap<String, Any>>
        val root = packages.single { it["SPDXID"] == rootId }
        root["licenseDeclared"] = licenseId
        root["licenseConcluded"] = licenseId
        root["copyrightText"] = "Copyright $copyrightHolder"
        root["downloadLocation"] = "git+$projectUrl.git@$gitHash"

        // The plugin turns a POM license it can't match by URL into a LicenseRef, even when its
        // name already is an SPDX id (antlr4-runtime: "BSD-3-Clause"). Swap those back.
        val knownSpdxIds = setOf("Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "MIT", "EPL-2.0")
        @Suppress("UNCHECKED_CAST")
        val extracted = doc["hasExtractedLicensingInfos"] as? MutableList<Map<String, Any>> ?: mutableListOf()
        val replacements = extracted
            .filter { (it["name"] as? String) in knownSpdxIds }
            .associate { it["licenseId"] as String to it["name"] as String }
        extracted.removeIf { it["licenseId"] in replacements }
        if (extracted.isEmpty()) doc.remove("hasExtractedLicensingInfos")

        // A POM cannot say "either license", so the plugin joins several POM licenses with AND.
        // Jakarta EE APIs are dual-licensed: EPL-2.0 or EDL-1.0 (SPDX: BSD-3-Clause).
        val licenseOverrides = mapOf(
            "jakarta.persistence:jakarta.persistence-api" to "EPL-2.0 OR BSD-3-Clause",
        )

        packages.forEach { pkg ->
            for (field in listOf("licenseDeclared", "licenseConcluded")) {
                val expr = pkg[field] as? String ?: continue
                pkg[field] = licenseOverrides[pkg["name"]] ?: replacements.entries.fold(expr) { acc, (ref, id) ->
                    acc.replace(Regex("\\b${Regex.escape(ref)}\\b"), id)
                }
            }
            @Suppress("UNCHECKED_CAST")
            val refs = pkg["externalRefs"] as? List<MutableMap<String, Any>> ?: emptyList()
            refs.forEach { ref ->
                val purl = ref["referenceLocator"] as? String ?: return@forEach
                // Drop only the repository qualifier; keep any others (classifier, type, …).
                val kept = purl.substringAfter('?', "").split("&")
                    .filterNot { it.startsWith("repository_url=") }
                ref["referenceLocator"] = purl.substringBefore('?') +
                        if (kept.any { it.isNotEmpty() }) "?" + kept.filter { it.isNotEmpty() }.joinToString("&") else ""
            }
        }

        json.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(doc)))
    }
}

val sbomFile = layout.buildDirectory.file("spdx/release.spdx.json")

mavenPublishing {
    coordinates(group.toString(), "rsql-criteria", version.toString())
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))

    // Upload, validate, then wait for a manual "Publish" click on central.sonatype.com.
    publishToMavenCentral(automaticRelease = false)

    // Central requires signatures. Only CI release builds have the key
    // (ORG_GRADLE_PROJECT_signingInMemoryKey), so local and snapshot builds stay unsigned.
    if (!providers.gradleProperty("signingInMemoryKey").orNull.isNullOrBlank()) {
        signAllPublications()
    }

    pom {
        name.set("rsql-criteria")
        description.set("Translates RSQL / FIQL queries into JPA Criteria API predicates, including filters on JSON columns")
        url.set(projectUrl)
        inceptionYear.set("2024")

        organization {
            name.set(copyrightHolder)
            url.set("https://nvaria.ch")
        }

        licenses {
            license {
                name.set(licenseId)
                url.set(licenseUrl)
            }
        }
        developers {
            developer {
                id.set("tm")
                name.set("Thomas Müllerleile")
                email.set("oss@nvaria.ch")
                organization.set(copyrightHolder)
            }
        }
        scm {
            url.set(projectUrl)
            connection.set("scm:git:$projectUrl.git")
            developerConnection.set("scm:git:$projectUrl.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("$projectUrl/issues")
        }
    }
}

publishing {
    // The SBOM goes everywhere the jar goes: nexus and Maven Central.
    publications.withType<MavenPublication>().configureEach {
        artifact(sbomFile) {
            builtBy(tasks.named("spdxSbomForRelease"))
            classifier = "sbom"
            extension = "spdx.json"
        }
    }
    // Internal nexus, in addition to Maven Central
    repositories {
        maven {
            name = "maven-snapshots"
            url = uri(System.getenv("NEXUS_MVN_SNAPSHOTS") ?: "")
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_PW")
            }
        }
        maven {
            name = "maven-release"
            url = uri(System.getenv("NEXUS_MVN_RELEASE") ?: "")
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_PW")
            }
        }
    }
}


sourceSets.configureEach {
    val generateGrammarSource = tasks.named(getTaskName("generate", "GrammarSource"))
    java.srcDir(generateGrammarSource.map { files() })
}

// The antlr plugin makes 'api' extend 'antlr', which would publish the ANTLR *tool*
// (plus ST4, antlr3, icu4j, ...) as a compile dependency. Consumers only need the runtime.
configurations.api {
    setExtendsFrom(extendsFrom.filterNot { it.name == "antlr" })
}

dependencies {
    antlr(libs.antlr.v4)
    implementation(libs.antlr.v4.runtime)
    // Criteria API types appear in the public API
    api(libs.jakarta.persistence.api)

    // Only for JSON columns: HibernateCriteriaBuilder.cast() emits a real SQL CAST, and
    // json_value extraction is Hibernate-specific anyway. compileOnly keeps it off the
    // runtime classpath of consumers, who bring their own Hibernate.
    compileOnly(libs.hibernate.core)

    testImplementation(libs.hibernate.core)
    testImplementation(libs.h2)
    testImplementation(libs.hamcrest)

    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}


tasks.generateGrammarSource {
    // Generated into build/ (plugin default); packageName also lays out the package directories
    maxHeapSize = "64m"
    packageName = "ch.nvaria.rsql.internal.parser"
    arguments = arguments + listOf("-visitor", "-long-messages")
}


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

tasks.withType<JavaCompile>().configureEach {
    // --release also rejects JDK APIs newer than 21, unlike source/target alone
    options.release = 21
    options.compilerArgs.add("-Xlint:unchecked")
    options.isDeprecation = true
}

// Document only the package exported by module-info. The excluded internals are
// patched into the module from the compiled classes so the API sources still resolve.
tasks.named<Javadoc>("javadoc") {
    val mainClasses = sourceSets.main.get().output.classesDirs
    inputs.files(mainClasses)
    exclude("ch/nvaria/rsql/internal/**")
    (options as StandardJavadocDocletOptions).addStringOption(
        "-patch-module", "ch.nvaria.rsql=" + mainClasses.asPath)
}

tasks.withType<Javadoc>().configureEach {
    val opts = options as StandardJavadocDocletOptions
    opts.addStringOption("Xdoclint:all,-missing", "-quiet")
    // The bundled DejaVu web fonts are ~4 MB of a ~4.5 MB javadoc jar; the stylesheet falls back
    // to system fonts. Keeps each release well below Central's monthly size limit.
    opts.addBooleanOption("-no-fonts", true)
}

// LICENSE and NOTICE travel with every published jar, as the Apache License asks.
// The javadoc jar comes from the publish plugin's own task type, which is not a Jar.
tasks.matching { it is Jar || it is com.vanniktech.maven.publish.tasks.JavadocJar }.configureEach {
    (this as AbstractCopyTask).from(layout.projectDirectory) {
        include("LICENSE", "NOTICE")
        into("META-INF")
    }
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

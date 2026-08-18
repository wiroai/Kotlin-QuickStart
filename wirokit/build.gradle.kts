plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.android.bcv.bridge)
}

group = "ai.wiro"
version = libs.versions.wirokit.get()

android {
    namespace = "ai.wiro.wirokit"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        aarMetadata {
            minCompileSdk = 26
        }
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17,
        )
        explicitApi()
    }
}

androidBcvBridge {
    variant.set("release")
}

kover {
    reports {
        filters {
            includes {
                classes("ai.wiro.wirokit.*")
            }
            excludes {
                classes(
                    "ai.wiro.wirokit.WiroKtorHttpTransport*",
                    "ai.wiro.wirokit.WiroDefaultSocketSessionFactory*",
                    "ai.wiro.wirokit.KtorWebSocketSession*",
                    "ai.wiro.wirokit.WiroUriContentSource*",
                    "ai.wiro.wirokit.WiroHttpTransport*",
                    "ai.wiro.wirokit.WiroRuntimeDefaults*",
                    "ai.wiro.wirokit.WiroClientAndroidKt*",
                )
            }
        }
        variant("debug") {
            verify {
                rule("sdk-logic-line-coverage") {
                    bound {
                        minValue.set(90)
                        coverageUnits.set(
                            kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE,
                        )
                    }
                }
            }
        }
    }
}

val debugCoverageReport =
    layout.buildDirectory.file("reports/kover/reportDebug.xml")
val verifyCoverageGate =
    tasks.register("verifyCoverageGate") {
        group = "verification"
        description = "Checks coverage threshold and a non-empty report."
        dependsOn("koverVerifyDebug", "koverXmlReportDebug")
        inputs.file(debugCoverageReport)
        doLast {
            val lineCounter =
                Regex(
                    """<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""",
                ).findAll(debugCoverageReport.get().asFile.readText())
                    .lastOrNull()
            check(lineCounter != null) {
                "Kover report has no line counter."
            }
            val totalLines =
                lineCounter.groupValues[1].toInt() +
                    lineCounter.groupValues[2].toInt()
            check(totalLines > 0) {
                "Kover coverage target is empty."
            }
        }
    }

dokka {
    dokkaPublications.html {
        moduleName.set("WiroKit")
        includes.from("MODULE.md")
    }
}

val hasSigningKey =
    providers
        .gradleProperty("signingInMemoryKey")
        .map(String::isNotBlank)
        .getOrElse(false)
val hasMavenCentralCredentials =
    providers
        .gradleProperty("mavenCentralUsername")
        .zip(
            providers.gradleProperty("mavenCentralPassword"),
        ) { username, password ->
            username.isNotBlank() && password.isNotBlank()
        }.getOrElse(false)

mavenPublishing {
    publishToMavenCentral()
    if (hasSigningKey) {
        signAllPublications()
    }
    coordinates(
        groupId = "ai.wiro",
        artifactId = "wirokit",
        version = project.version.toString(),
    )
    pom {
        name.set("WiroKit")
        description.set(
            "Official Android Kotlin SDK for the Wiro AI API.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/wiroai/Kotlin-QuickStart")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("wiro")
                name.set("Wiro")
                email.set("hello@wiro.ai")
                organization.set("Wiro")
                organizationUrl.set("https://wiro.ai")
            }
        }
        scm {
            connection.set(
                "scm:git:https://github.com/" +
                    "wiroai/Kotlin-QuickStart.git",
            )
            developerConnection.set(
                "scm:git:ssh://git@github.com/" +
                    "wiroai/Kotlin-QuickStart.git",
            )
            url.set(
                "https://github.com/wiroai/Kotlin-QuickStart",
            )
        }
        issueManagement {
            system.set("GitHub")
            url.set(
                "https://github.com/wiroai/" +
                    "Kotlin-QuickStart/issues",
            )
        }
        ciManagement {
            system.set("GitHub Actions")
            url.set(
                "https://github.com/wiroai/" +
                    "Kotlin-QuickStart/actions",
            )
        }
    }
}

tasks
    .matching {
        it.name.startsWith("publish") &&
            it.name.contains("MavenCentral")
    }.configureEach {
        doFirst {
            check(hasMavenCentralCredentials) {
                "Maven Central credentials are required."
            }
            check(hasSigningKey) {
                "An in-memory signing key is required."
            }
        }
    }

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java")
    buildUponDefaultConfig = true
    parallel = true
    ignoreFailures = false
    basePath.set(rootProject.projectDir)
}

val versionSource =
    layout.projectDirectory.file(
        "src/main/java/ai/wiro/wirokit/WiroKitInfo.kt",
    )
val sdkVersion = project.version.toString()
val verifyVersionConsistency =
    tasks.register("verifyVersionConsistency") {
        group = "verification"
        description = "Checks the SDK version against the version catalog."
        inputs.file(versionSource)
        inputs.property("wirokitVersion", sdkVersion)
        doLast {
            val expected =
                "public const val VERSION: String = \"$sdkVersion\""
            check(versionSource.asFile.readText().contains(expected)) {
                "WiroKitInfo.VERSION must match project version " +
                    sdkVersion
            }
        }
    }

tasks.named("check").configure {
    dependsOn(verifyVersionConsistency)
}

tasks
    .withType<
        org.gradle.api.publish.maven.tasks.AbstractPublishToMaven,
    >()
    .configureEach {
        outputs.cacheIf { false }
    }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)

    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("org.jetbrains.dokka") version "1.6.10"
    `java-library`
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}


application {
    mainClassName = "org.key_project.core.doc.AppKt"
}


val plugin by configurations.creating
configurations {
    implementation.get().extendsFrom(plugin)
}


repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven("https://git.key-project.org/api/v4/projects/35/packages/maven")
}

dependencies {
    val implementation by configurations

    implementation("org.key_project:key.core:2.10.0")
    implementation("org.key_project:key.util:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
    implementation("com.atlassian.commonmark:commonmark:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-tables:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-autolink:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-ins:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-heading-anchor:0.17.0")

    plugin(platform("org.jetbrains.kotlin:kotlin-bom"))
    plugin("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    plugin("com.github.ajalt:clikt:2.8.0")
    plugin("org.jetbrains:annotations:23.0.0")
    plugin("org.slf4j:slf4j-api:1.7.33")

    //    implementation("org.key_project:key.core")

    val testImplementation by configurations
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("org.slf4j:slf4j-simple:1.7.33")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<JavaCompile> {
    options.release.set(11)
}

tasks.withType<Test> {
    useJUnitPlatform()
    reports.html.required.set(false)
    reports.junitXml.required.set(true)
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc>() {
    isFailOnError = false
}

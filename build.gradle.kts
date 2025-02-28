import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.dokka") version "2.0.0"
    `java-library`
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.52.0"
}

version = "1.0"
description = "Tool for the generation of taclet documentation"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")}
}

application {
    mainClass = "io.github.wadoon.tadoc.Tadoc"
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.ajalt:clikt:2.8.0")
    implementation("org.jetbrains:annotations:26.0.2")
    implementation("org.slf4j:slf4j-api:2.0.16")

    val testImplementation by configurations
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")

    implementation("org.key-project:key.core:2.12.3")
    implementation("org.key-project:key.util:2.12.3")

    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
    implementation("com.atlassian.commonmark:commonmark:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-tables:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-autolink:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-ins:0.17.0")
    implementation("com.atlassian.commonmark:commonmark-ext-heading-anchor:0.17.0")

}
kotlin {
    jvmToolchain(21)
}


tasks.withType<Test> {
    useJUnitPlatform()
    reports.html.required.set(true)
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

tasks.register<JavaExec>("generate") {
    dependsOn("compileKotlin")
    mainClass = application.mainClass
    classpath= sourceSets["main"].runtimeClasspath
    args("--output", "out", "--use-default-classpath")
}


dokka {
    moduleName.set("Generator for KeY Logic Documentation")
    dokkaSourceSets.main {
        includes.from("README.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://www.javadoc.io/doc/org.key-project/key.core")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        //customStyleSheets.from("styles.css")
        //customAssets.from("logo.png")
        //footerMessage.set("(c) Your Company")
    }
}

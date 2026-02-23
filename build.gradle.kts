plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.dokka") version "2.1.0"
    `java-library`
    application
    id("com.gradleup.shadow") version "9.3.1"
    id("com.github.ben-manes.versions") version "0.53.0"
}

version = "1.0"
description = "Tool for the generation of taclet documentation"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven("https://git.key-project.org/api/v4/projects/35/packages/maven")
}

application {
    mainClass = "io.github.wadoon.tadoc.Tadoc"
}

dependencies {
    //implementation("org.key-project:key.core:2.12.3")
    //implementation("org.key-project:key.util:2.12.3")
    val keyVersion = "2.13.0-SNAPSHOT"
    implementation("org.key-project:key.core:$keyVersion")
    implementation("org.key-project:key.util:$keyVersion")
    implementation("org.key-project:key.core.wd:$keyVersion")
    implementation("org.key-project:key.core.infflow:${keyVersion}")

    implementation("io.github.wadoon:kotlin-prettyprinting:1.0")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("org.slf4j:slf4j-api:2.0.17")

    //val testImplementation by configurations
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")

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
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
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

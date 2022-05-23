plugins {
    kotlin("jvm")
    jacoco
    maven
    `maven-publish`
}

description = "Rivet Specific Customizations for Smithy code generation"
extra["displayName"] = "Smithy :: Rust :: Rivet Codegen"
extra["moduleName"] = "software.amazon.smithy.rustsdk"

group = "software.amazon.software.amazon.smithy.rust.codegen.smithy"
version = "0.1.0"

val smithyVersion: String by project

dependencies {
    implementation(project(":codegen"))
    implementation("org.jsoup:jsoup:1.14.3")
}

val generateRivetSdkVersion by tasks.registering {
    // generate the version of the runtime to use as a resource.
    // this keeps us from having to manually change version numbers in multiple places
    val resourcesDir = "$buildDir/resources/main/software/amazon/smithy/rustsdk"
    val versionFile = file("$resourcesDir/sdk-crate-version.txt")
    outputs.file(versionFile)
    val crateVersion = "0.0.1"
    inputs.property("crateVersion", crateVersion)
    sourceSets.main.get().output.dir(resourcesDir)
    doLast {
        versionFile.writeText(crateVersion)
    }
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
    dependsOn(generateRivetSdkVersion)
}

// Reusable license copySpec
val licenseSpec = copySpec {
    from("${project.rootDir}/LICENSE")
    from("${project.rootDir}/NOTICE")
}

// Configure jars to include license related info
tasks.jar {
    metaInf.with(licenseSpec)
    inputs.property("moduleName", project.name)
    manifest {
        attributes["Automatic-Module-Name"] = project.name
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    group = "publishing"
    description = "Assembles Kotlin sources jar"
    classifier = "sources"
    from(sourceSets.getByName("main").allSource)
}

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories { maven { url = uri("$buildDir/repository") } }
}

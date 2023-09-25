// ADDITIONALLY THIS DEMO USES THE OLD STYLE GRADLE DEP apply plugin

// expect failure from this build script because hamcrest-all contains hamcrest-core
// so there are lots of dupe"d resources

repositories {
    mavenLocal()
    mavenCentral()
}

buildscript {

    repositories {
        mavenLocal()
        mavenCentral()

        // include plugin repository where classpath(hell lives from 1.8 onwards)
        maven {
            url = uri("https://plugins.gradle.org/m2")
        }
    }

    dependencies {
        classpath("com.portingle:classpath-hell:1.9")
    }
}

apply(plugin = "com.portingle.classpath-hell")

plugins {
    `java-library`
}

// works if I comment this out....
classpathHell {

    // Demonstrate replacing the default set of exclusions
    // leaving only things like license files and manifests in violation
    resourceExclusions = listOf(
        ".*class",
        ".*/",
    )
}

dependencies {
    implementation("org.hamcrest:hamcrest-all:1.3")
    implementation("org.hamcrest:hamcrest-core:1.3")
}

tasks.named("build") {
    dependsOn(":checkClasspath")
}

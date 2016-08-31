# classpathHell

Gradle plugin that breaks the build if there are classpath collisions

## Getting started

```gradle

// expect failure from this particularbuild script because hamcrest-all contains hamcrest-core
// so there are lots of dupe'd resources

repositories {
    mavenCentral()
}

buildscript {

    repositories {
        mavenCentral()
    }

    dependencies {
        // check maven central for the latest release
        classpath "com.portingle:classpath-hell:1.0"
    }
}

apply plugin: 'com.portingle.classpathHell'
apply plugin: 'java'

// some optional configuration
classpathHell {

    // Demonstrate replacing the default set of exclusions
    // leaving only things like license files and manifests in violation
    resourceExclusions = [
            ".*class",
            ".*/",
    ]
}

// introduce some deliberate duplication
dependencies {
    compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
    compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
}

// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])

```

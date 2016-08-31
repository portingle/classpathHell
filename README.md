# classpathHell - Classpath Mayhem Detector

Gradle plugin that breaks the build if there are classpath collisions.

Think your build is stable and repeatable? Think again.

It's far too easy to end up with multiple copies of a class or resource on your classpath leading to difficult to trace runtime errors that due to classpath ordering instability might not show up until late in your release cycle, or possibly even production. 

Don't let that happen - just use this detector.

An excellent example is that Dropwizard and Codahale metrics have the same fully qualified class names, but with different interfaces and different implementations.

Other problems include where we have _-all_ jars included in addition to discrete jars (see the example below). In such cases the dependency resolution in Gradle (or maven) won't help.

What you need to to spot those cases where there are dupes and then eliminate the dupes or if you deem it safe then suppress the specific violation. This plugin provides that functionality.

## Getting started

```gradle

// expect a deliberate failure from this build script because hamcrest-all contains hamcrest-core
// causing lots of dupe'd resources

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
    
    // Uncomment this line to make the build succeed.
    // Of course the correct solution is to elimiate the dependency on the hamcrest-all jar
    //artifactExclusions = [ ".*hamcrest-all.*" ]
}

// introduce some deliberate duplication
dependencies {
    compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
    compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
}

// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])

```

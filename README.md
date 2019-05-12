# classpathHell - Classpath Mayhem Detector

__Think your build is stable and repeatable? Think again.__

It's far too easy to end up with multiple copies of a class or resource on your classpath leading to 
difficult to trace runtime errors that, due to classpath ordering instability, might not show up until 
late in your release cycle, or possibly even production. 

Don't let that happen - just use this detector.

classpathHell is a gradle plugin that breaks the build if there are classpath collisions.

An excellent example is that Dropwizard and Codahale metrics have the same fully qualified class names, but with different interfaces and different implementations.

Other problems include where we have _-all_ jars included in addition to discrete jars (see the Hamcrest examples below). 
In such cases the dependency resolution in Gradle (or maven) won't help.

What you need to to spot those cases where there are dupes and then eliminate the dupes or if you deem it safe then suppress the specific violation. 
This plugin provides that functionality.

Note: 
I discovered after creating classpathHell that there are two similar plugins out there:
- https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Duplicate-Classes-Rule
- https://plugins.gradle.org/plugin/net.idlestate.gradle-duplicate-classes-check

I haven't looked at these yet and it is conceivable that these will be a better fit for you, but classpathHell seems to be more configurable, allowing suppressions etc.

## Getting started

### Short example

In this example we will include two conflicting jars from Hamcrest; the core jar and the uber jar. 
Given the uber jar contains the core classes this combination will report many dupes.

```groovy

repositories {
    mavenCentral()
}

buildscript {

    repositories {
        mavenCentral()
    }

    dependencies {
        // check maven central for the latest release
        classpath "com.portingle:classpath-hell:1.2"
    }
}

apply plugin: 'com.portingle.classpathHell'
apply plugin: 'java'

// introduce some deliberate duplication
dependencies {
    compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
    compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
}

// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])
```

### Resource supressions 

By default the plugin reports all dupes that it finds, however, the plugin allows one configure a property `resourceExclusions`
to specify a list of regular expressions matching resources to exclude from the check.

```groovy
classpathHell { 
    resourceExclusions = [ "abc.*", "de.*f" ] // list of regexes for resources to exclude 
}
```

As a convenience the plugin provides a constant `CommonResourceExclusions()` that can be used to suppress 
a set of common dupes that aren't very interesting, for example _^about.html\$_.

```groovy
classpathHell {
    // resourceExclusions is a list of regex strings that exclude any matching resources from the report 
    resourceExclusions = CommonResourceExclusions()
}
```

However, if you wish to have more control over the exclusions then take a look at the next section.  

### Restricting the gradle configuration scan

Optionally specify "configurationsToScan" to restrict the scanning to defined configurations.
By default all configurations are scanned.

```groovy
classpathHell {

    configurationsToScan = [ configurations.testRuntime, configurations.implementation  ]
}

```

### Configuring resource suppressions

The previous example will produce a report with many duplicates that have not been suppressed by default.

We can configure the plugin to exclude further resources from the report. 

```groovy

repositories {
    mavenCentral()
}

buildscript {

    repositories {
        mavenCentral()
    }

    dependencies {
        // check maven central for the latest release
        classpath "com.portingle:classpath-hell:1.2"
    }
}

apply plugin: 'com.portingle.classpathHell'
apply plugin: 'java'

// some demo configuration
classpathHell {

    // use the convenience value ..
    resourceExclusions = CommonResourceExclusions()

    /* Alternatively, we will exclude all classes and a particular directory.
     */
    resourceExclusions = [
            // these are pattern matches on the resource path
            "somePath/",
            ".*class"
    ]

    /* optionally specify "configurationsToScan" to restrict the scanning to defined configurations.
     * by default all configurations are scanned.
     */
    configurationsToScan = [ configurations.testRuntime, configurations.implementation  ]

    /* Since `resourceExclusions` is a List we can append to it.
     */
    resourceExclusions.addAll([
        ".*/", 
        "anotherPath/.*"
     ])
    
    /* or use other List functions to remove entries */
    resourceExclusions.remove("somePath")
}

// introduce some deliberate duplication
dependencies {
    compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
    compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
}

// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])

```

### Excluding artefacts from the report

As well as suppressing reports about certain resources being duplicated we can suppress reports relating to entire artefacts; 
this is achieved be configuring `artifactExclusions`.

```groovy
classpathHell {
    artifactExclusions = [
        // this is a pattern match on the file system path of the resource once it's been downloaded locally
        ".*hamcrest-core.*"
    ]
}
```

Of course, ideally you should resolve the conflicting dependencies however sometimes you need a way out.

In this next example we'll see how to exclude the Hamcrest core jar from the report.
This will cause the report to run cleanly because we'll have suppressed all the dupes.

```groovy

repositories {
    mavenCentral()
}

buildscript {

    repositories {
        mavenCentral()
    }

    dependencies {
        // check maven central for the latest release
        classpath "com.portingle:classpath-hell:1.2"
    }
}

apply plugin: 'com.portingle.classpathHell'
apply plugin: 'java'

classpathHell {
    artifactExclusions = [
        // this is a pattern match on the file system path of the resource once it's been downloaded locally
        ".*hamcrest-core.*"
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

## Gradle task

To run the check use: 

```
./gradlew checkClasspath
```

But don't forget to wire this plugin into your build so it runs automatically.

```groovy
// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])
```

# Troubleshooting

## Error "Resolving configuration 'apiElements' directly is not allowed"

You are probably running Gradle 4.x with an old version of classpathHell - upgrade to 1.2 or later.

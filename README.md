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

### Short demo

In this example we will deliberately include two conflicting jars from Hamcrest; the core jar and the "-all" jar. 
Given the 'all' jar also contains all the core classes this combination will introduce many duplicate resources to the classpath.
The example below if run will demonstrate the reporting.

```groovy

repositories {
    mavenCentral()
}

buildscript {

    repositories {
        mavenCentral()
    }

    // OLD STYLE DEPENDENCY
    // dependencies {
    //    // check maven central for the latest release
    //    classpath "com.portingle:classpath-hell:1.8"
    //}
}


plugins {
    // NEW STYLE PLUGIN
    id('com.portingle.classpath-hell').version("1.8")
}

apply plugin: 'java'

// OLD STYLE DEPENDENCY
// apply plugin: 'com.portingle.classpathHell'

// introduce some deliberate BAD deps with dupes

dependencies {
    implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
    implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
}

// link this plugin into the build cycle
build.dependsOn(['checkClasspath'])
```

### Restricting the gradle configuration scan

It is *mandatory* to configure "configurationsToScan" to restrict the scanning to a defined set of configurations.

Without this setting then all configurations are scanned and resolved, but later versions of gradle object to this as certain configurations cannot be enumerated.
Gradle configurations have a property 'canBeResolved' that identifies those that we can scan safely.
  
At the time of writing those configurations can NOT be resolved include:   
 

So make sure to fill this setting in as needed.

```groovy
classpathHell {
    configurationsToScan = [ configurations.runtimeClasspath ]
}

```

### Suppressing benign duplication 

By default the plugin reports all dupes that it finds, however, the plugin allows one to suppress dupes
where the duplicate resource has exacly the same bytes in each case.

```groovy
classpathHell {
    suppressExactDupes = true
}
```

### Resource exclusion

By default the plugin reports all dupes that it finds, however, the plugin allows one specify a list of resources to exclude from the check.
This is done by configuring the property `resourceExclusions` with a list of regular expressions matching the resource paths to suppress. 

```groovy
classpathHell { 
    resourceExclusions = [ "abc.*", "de.*f" ] // list of regexes for resources to exclude 
}
```

NOTE: As a convenience the plugin also provides a constant `CommonResourceExclusions()` that can be used to suppress 
a set of common dupes that aren't very interesting, for example _^about.html\$_.

```groovy
classpathHell {
    // resourceExclusions is a list of regex strings that exclude any matching resources from the report 
    resourceExclusions = CommonResourceExclusions()
}
```

However, if you wish to have more control over the exclusions then take a look at the next section.  

### Further resource exclusion patterns 

We can configure the plugin to exclude further resources from the report. 

```groovy

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

    /* Since `resourceExclusions` is a List we can append to it.
     */
    resourceExclusions.addAll([
        ".*/", 
        "anotherPath/.*"
     ])
    
    /* or use other List functions to remove entries */
    resourceExclusions.remove("somePath")
}

```

### Excluding artifacts from the report

As well as suppressing reports about certain resources being duplicated we can suppress reports relating to entire artifacts; 
this is achieved be configuring `artifactExclusions`.

Of course, ideally you should resolve the conflicting dependencies however sometimes you need a way out and entirely excluding
an artifact from the scan may be necessary.

```groovy
classpathHell {
    artifactExclusions = [
        // this is a pattern match on the file system path of the build once the dependency has been downloaded locally
        ".*hamcrest-core.*"
    ]
}
```


### Extra info logging

To get more detailed info level logging there are three options
- set the "trace" property in the gradle config to true.

```groovy
classpathHell {
    // some additional logging; note one must also run with "--info" if you want to see this as the logging comes out with "INFO" level
    trace= true
}
```
FYI you must also use "--info" on gradle or you will see nothing.
Note: we don't use debug level for this because that turns on far too much tracing.

- set by gradle property

```bash
./gradlew -PclasspathHell.trace=true --info build
``` 
FYI you must also use "--info" on gradle or you will see nothing.

- turn on debug 

```bash
./gradlew --debug build
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

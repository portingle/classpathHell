# RELEASE NOTES

- 1.9

  - Changed the log level of _logger.warn("classpathHell: trace=" + doTrace)_ to info so it doesn't clutter the console.


- 1.8

  - Enabled use of the newer gradle "plugin id" syntax - see the README
  - Upgrade to gradle 8


- 1.7.0

  - Upgrade to gradle 7
  - Better error reporting for misconfiguration of this plugin
  - Tested with Java 18 and Gradle 7 
  - Fixed   ./gradlew   publishToMavenLocal


- 1.6.0

  - Upgrade to gradle 6

- 1.5

  - like 1.4 but using a different hashed
  
- 1.4

  - allow convenient suppression of 'benign' exact matches where there are dupes but they have the same impl
  
```groovy
classpathHell {
    suppressExactDupes = true
}
```
 

- 1.3

  - added configurationsToScan
  - suppressed a lot of debug logging using a trace flag

- 1.2

  - add support for Gradle 4

- 1.1

- 1.0

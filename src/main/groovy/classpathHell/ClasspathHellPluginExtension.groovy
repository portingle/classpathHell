package classpathHell

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact

class ClasspathHellPluginExtension {

    /* set to true to get additional logging */
    Boolean trace = false

    /* utility */
    static boolean excludeArtifactPaths(List<String> excludedPatterns, ResolvedArtifact f) {
        boolean inc = true
        excludedPatterns.each { String ex ->
            if (f.file.getAbsolutePath().matches(ex))
                inc = false
        }

        return inc
    }

    /* override to supply a list of artifacts to exclude from the check (assuming includeArtifact has not been overridden).
    */
    List<String> artifactExclusions = []

    /* optional list of configurations to limit the scan to */
    List<Configuration> configurationsToScan = []

    /* if true then instances of a resource that have the same hash will be considered equivalent and not be reported */
    Boolean suppressExactDupes = false

    /*
    override to provide an alternative inclusion strategy to the default.
     */
    Closure<Boolean> includeArtifact = { ResolvedArtifact f ->
        // default strategy is to exclude artifacts according to a black list
        excludeArtifactPaths(artifactExclusions.toList(), f)
    }

    /* utility */
    static boolean excludeMatches(List<String> excludedPatterns, String f) {

        boolean inc = true
        excludedPatterns.each { ex ->
            if (f.matches(ex))
                inc = false
        }

        return inc
    }


    /* A convenience constant defining a set of common defaults that are not very interesting.
    */
    static List<String> CommonResourceExclusions() {
        return [
            "^rootdoc.txt\$",
            "^about.html\$",
            "^NOTICE\$",
            "^LICENSE\$",
            "^LICENSE.*.txt\$",
            "^META-INF/.*",
            ".*/\$",
            ".*com/sun/.*",
            ".*javax/annotation/.*"
    ] }

    /** Optionally override or modify to provide an alternative list of resources to exclude from the check.
     */
    List<String> resourceExclusions = []

    /*
    override to provide an alternative inclusion strategy to the default.
     */
    Closure<Boolean> includeResource = { String f ->
        excludeMatches(resourceExclusions, f)
    }

    @Override
    String toString() {
        return "ClasspathHellPluginExtension{" +
                "artifactExclusions=" + artifactExclusions +
                ", configurationsToScan=" + configurationsToScan +
                ", includeArtifact=" + includeArtifact +
                ", resourceExclusions=" + resourceExclusions +
                ", includeResource=" + includeResource +
                '}'
    }
}
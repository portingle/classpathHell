package classpathcollisionchecker

import org.gradle.api.artifacts.ResolvedArtifact

class ClasspathCollisionCheckerPluginExtension {

    static boolean includeArtefact(List<String> excludedPatterns, ResolvedArtifact f) {
        boolean inc = true
        excludedPatterns.each { String ex ->
            if (f.file.getAbsolutePath().matches(ex))
                inc = false
        }

        return inc
    }

    Set<String> basicExcludes = ["^rootdoc.txt\$",
                         "^about.html\$",
                         "^NOTICE\$",
                         "^LICENSE\$",
                         "^LICENSE.*.txt\$",
                         "^META-INP/.*",
                         ".*/\$",
                         ".*com/sun/.*",
                         ".*javax/annotation/.*"
    ]

    /** override to implement alternate exclusions*/
    boolean includeArtefact(ResolvedArtifact f) {}

    boolean includeResource(String path) { true }

}



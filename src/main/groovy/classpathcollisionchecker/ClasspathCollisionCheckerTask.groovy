package classpathcollisionchecker

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ClasspathCollisionCheckerTask extends DefaultTask {

    static Collection<String> getResourcesFromJarFile(final File file, final Pattern pattern) {
        //println("resources from "+  file)
        final ArrayList<String> retval = new ArrayList<String>();

        ZipFile zf;

        try {
            zf = new ZipFile(file);
        } catch (final ZipException ex) {
            throw new Error(ex)
        } catch (final IOException ex) {
            throw new Error(ex)
        }
        final Enumeration e = zf.entries()

        while (e.hasMoreElements()) {
            final ZipEntry ze = (ZipEntry) e.nextElement()
            final String fileName = ze.getName()
            final boolean accept = pattern.matcher(fileName).matches()
            if (accept) {
                retval.add(fileName)
            }
        }
        try {
            zf.close()
        } catch (final IOException ex) {
            throw new Error(ex)
        }
        return retval;
    }

    static boolean includeResource(String f) {
        //println("checking inclusion"+ f)
        def exclusions = ['^rootdoc.txt$',
                          '^about.html$',
                          '^NOTICE$',
                          '^LICENSE$',
                          '^LICENSE.*.txt$',
                          '^META-INF/.*',
                          '.*/$',
                          '.*com/sun/.*',
                          '.*javax/annotation/.*'
        ]

        boolean inc = true
        exclusions.each { ex ->
            if (f.matches(ex))
                inc = false
        }

        //println("result " + inc)
        return inc
    }

    static String gav(ResolvedDependency d) {
        def f = d.module.id
        f.group + ":" + f.name + ":" + f.version
    }

    Set<String> findDep(List pathAccumulator, ResolvedDependency dep, ResolvedArtifact source) {
        if (dep.module.id == source.moduleVersion.id) {
            List<String> path = pathAccumulator.reverse().collect { it.toString() }

            def s = [] as Set
            s.add path.join(" <- " )
            return s
        } else {
            Set<ResolvedDependency> children = dep.children
            return findDepC(pathAccumulator, children, source)
        }
    }


    Set<String> findDepC(List pathAccumulator, Set<ResolvedDependency> children, ResolvedArtifact source) {
        def found = [] as Set

        children.each { child ->
            def newAccum = new ArrayList(pathAccumulator)
            newAccum.add(gav(child))
            def f = findDep(newAccum, child, source)
            found.addAll(f)
        }
        found
    }

    Set<String> findRoute(Configuration conf, ResolvedArtifact source) {
        Set<ResolvedDependency> deps = conf.getResolvedConfiguration().firstLevelModuleDependencies
        findDepC([], deps, source)
    }

    static Set<ResolvedArtifact> suppressPermittedCombinations(Set<ResolvedArtifact> dupes) {
        return dupes
    }

    List<String> getResources(File f) {
        ArrayList<String> files = new ArrayList<String>()
        if (f.isFile()) {
            Collection<String> r = getResourcesFromJarFile(f, Pattern.compile(".*"))
            files.addAll(r)
        } else {
            f.listFiles().each { File dir ->
                Collection<String> r = this.getResources(dir)

                files.addAll(r)
            }
        }
        return files
    }

    @TaskAction
    void classpathCollisionCheck() {
        ClasspathCollisionCheckerPluginExtension ext = project.classpathCollisionChecker

        boolean hadDupes = false

        ConfigurationContainer configurations = this.project.configurations

        configurations.iterator().each { conf ->
            System.out.println("checking " + conf.toString())
            System.out.flush()

            Map<String, Set<ResolvedArtifact>> counts = new HashMap()
            conf.getResolvedConfiguration().getResolvedArtifacts().each {
                ResolvedArtifact at ->
                    System.out.println("checking " + at)

                    if (ext.includeArtefact(at)) {
                        File file = at.file
                        Collection<String> r = this.getResources(file).findAll { res -> includeResource(res) }

                        r.each { res ->
                            Set<ResolvedArtifact> sources = counts.get(res)
                            if (!counts.containsKey(res)) {
                                sources = new HashSet()
                                counts.put(res, sources)
                            }
                            sources.add(at)
                        }
                    }
            }

            counts.entrySet().each { e ->
                if (e.value.size() > 1) {
                    Set<ResolvedArtifact> dupes = suppressPermittedCombinations(e.value)

                    hadDupes = true

                    System.err.println("classpath: " + conf.name + " contains duplicate resource: " + e.key)

                    dupes.toList().sort().each { source ->
                        System.err.println(" found within dependency: " + source.moduleVersion.id)
                        findRoute(conf, source).toList().sort().each { route ->
                            System.err.println("  imported via: " + route)
                        }
                    }
                }
            }
        }

        if (hadDupes)
            throw new GradleException("Duplicate resources detected")
    }
}

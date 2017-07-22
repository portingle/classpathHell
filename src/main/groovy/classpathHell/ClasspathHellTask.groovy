package classpathHell

import groovy.transform.PackageScope
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

@PackageScope
class ClasspathHellTask extends DefaultTask {

    def log(String s) {
        logger.debug("classpathHell: " + s)
    }

    static Collection<String> getResourcesFromJarFile(final File file, final Pattern pattern) {

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

    static String gav(ResolvedDependency d) {
        def f = d.module.id
        f.group + ":" + f.name + ":" + f.version
    }

    static Set<String> findDep(List pathAccumulator, ResolvedDependency dep, ResolvedArtifact source) {
        if (dep.module.id == source.moduleVersion.id) {
            List<String> path = pathAccumulator.reverse().collect { it.toString() }

            def s = [] as Set
            s.add path.join(" <- ")
            return s
        } else {
            Set<ResolvedDependency> children = dep.children
            return findDepC(pathAccumulator, children, source)
        }
    }


    static Set<String> findDepC(List pathAccumulator, Set<ResolvedDependency> children, ResolvedArtifact source) {
        def found = [] as Set

        children.each { child ->
            def newAccum = new ArrayList(pathAccumulator)
            newAccum.add(gav(child))
            def f = findDep(newAccum, child, source)
            found.addAll(f)
        }
        found
    }

    static Set<String> findRoute(Configuration conf, ResolvedArtifact source) {
        Set<ResolvedDependency> deps = conf.getResolvedConfiguration().firstLevelModuleDependencies
        findDepC([], deps, source)
    }

    static Set<ResolvedArtifact> suppressPermittedCombinations(Set<ResolvedArtifact> dupes) {
        // unimplemented !!!
        return dupes
    }

    static List<String> getResources(File f) {
        ArrayList<String> files = new ArrayList<String>()
        if (f.isFile()) {
            Collection<String> r = getResourcesFromJarFile(f, Pattern.compile(".*"))
            files.addAll(r)
        } else {
            f.listFiles().each { File dir ->
                Collection<String> r = getResources(dir)

                files.addAll(r)
            }
        }
        return files
    }

    @TaskAction
    void action() {
        ClasspathHellPluginExtension ext = project.classpathHell

        boolean hadDupes = false

        this.project.getConfigurations().findAll {
            canBeResolved(it)
        }.each { conf ->
            log("checking " + conf.toString())

            Map<String, Set<ResolvedArtifact>> counts = new HashMap()
            conf.getResolvedConfiguration().getResolvedArtifacts().each {
                ResolvedArtifact at ->

                    if (ext.includeArtifact.call(at)) {
                        log("including artifact <" + at.moduleVersion.id + ">")

                        File file = at.file
                        Collection<String> r = getResources(file).findAll {
                            String res ->
                                Boolean inc = ext.includeResource.call(res)

                                if (inc) log(" including resource <" + res + ">")
                                else log(" excluding resource <" + res + ">")
                                inc
                        }

                        r.each { res ->
                            Set<ResolvedArtifact> sources = counts.get(res)
                            if (!counts.containsKey(res)) {
                                sources = new HashSet()
                                counts.put(res, sources)
                            }
                            sources.add(at)
                        }
                    } else
                        log("excluding artifact <" + at.moduleVersion.id + ">")

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

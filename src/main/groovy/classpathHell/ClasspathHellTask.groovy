package classpathHell

import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.apache.commons.codec.digest.DigestUtils

import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@PackageScope
class ClasspathHellTask extends DefaultTask {

    def log(boolean trace, String s) {
        if (trace) logger.debug("classpathHell: " + s)
    }

    static Collection<String> getResourcesFromJarFile(final File jarFile, final Pattern pattern) {

        final ArrayList<String> resourceFiles = new ArrayList<String>()

        ZipFile zf = new ZipFile(jarFile)
        final Enumeration e = zf.entries()

        while (e.hasMoreElements()) {
            final ZipEntry ze = (ZipEntry) e.nextElement()

            final String resourceFileName = ze.getName()
            final boolean accept = pattern.matcher(resourceFileName).matches()
            if (accept) {
                resourceFiles.add(resourceFileName)
            }
        }
        zf.close()

        return resourceFiles
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

    static Set<ResolvedArtifact> suppressPermittedCombinations(boolean suppressByHash, String resourcePath, Set<ResolvedArtifact> dupes) {
        if (!suppressByHash) return dupes

        Set<String> hashes = new HashSet()
        Set<String> ids = new HashSet()
        dupes.each { file ->
            ZipFile zf = new ZipFile(file.file)
            ZipEntry ze = zf.getEntry(resourcePath)
            InputStream zi = zf.getInputStream(ze)

            def md5 = DigestUtils.md5Hex(zi)
            hashes.add(md5)

           // logger.warn("classpathHell: " + resourcePath + " #md5 " + md5 + " @ " + file.id.componentIdentifier)
            ids.add(file.id.componentIdentifier.toString())

            zi.close()
        }

        if (hashes.size() == 1) {
          //  logger.warn("classpathHell: " + resourcePath + " has been automatically suppressed across : " + ids)

            return new HashSet()
        }

        return dupes
    }

    static List<String> getResources(File jarOrDir) {
        ArrayList<String> files = new ArrayList<String>()
        if (jarOrDir.isFile()) {
            Collection<String> resourcesInJar = getResourcesFromJarFile(jarOrDir, Pattern.compile(".*"))
            files.addAll(resourcesInJar)
        } else {
            // dir
            jarOrDir.listFiles().each { File fileOrDir ->
                Collection<String> resourcesInJar = getResources(fileOrDir)

                files.addAll(resourcesInJar)
            }
        }
        return files
    }

    static boolean canBeResolved(configuration) {
        // Configuration.isCanBeResolved() has been introduced with Gradle 3.3,
        // thus we need to check for the method's existence first
        configuration.metaClass.respondsTo(configuration, "isCanBeResolved") ?
                configuration.isCanBeResolved() : true
    }

    @TaskAction
    void action() {
        ClasspathHellPluginExtension ext = project.classpathHell

        boolean hadDupes = false

        List<Configuration> configurations = ext.configurationsToScan
        if (!configurations) {
            configurations = this.project.getConfigurations().toList()
        }

        configurations.findAll {
            canBeResolved(it)
        }.each { Configuration conf ->
            logger.debug("classpathHell: checking " + conf.toString())

            Map<String, Set<ResolvedArtifact>> counts = new HashMap()
            conf.getResolvedConfiguration().getResolvedArtifacts().each {
                ResolvedArtifact resolvedArtifact ->

                    if (ext.includeArtifact.call(resolvedArtifact)) {
                        log(ext.trace,"including artifact <" + resolvedArtifact.moduleVersion.id + ">")

                        File file = resolvedArtifact.file
                        def resourcesInFile = getResources(file)
                        Collection<String> includedResources = resourcesInFile.findAll {
                            String res ->
                                Boolean inc = ext.includeResource.call(res)

                                if (inc) log(ext.trace, " including resource <" + res + ">")
                                else log(ext.trace, " excluding resource <" + res + ">")
                                inc
                        }

                        // organise found resources by resource name
                        includedResources.each { res ->
                            Set<ResolvedArtifact> sources = counts.get(res)
                            if (!counts.containsKey(res)) {
                                sources = new HashSet()
                                counts.put(res, sources)
                            }
                            sources.add(resolvedArtifact)
                        }
                    } else
                        log(ext.trace, "excluding artifact <" + resolvedArtifact.moduleVersion.id + ">")

            }

            counts.entrySet().each { Map.Entry<String, Set<ResolvedArtifact>> e ->
                Set<ResolvedArtifact> similarResolvedArtifacts = e.value
                String resourcePath = e.key
                if (similarResolvedArtifacts.size() > 1) {
                    Set<ResolvedArtifact> dupes = suppressPermittedCombinations(ext.suppressExactDupes, resourcePath, similarResolvedArtifacts)

                    boolean thisHasDupes = !dupes.isEmpty()

                    if (thisHasDupes) {
                        System.err.println("classpath: " + conf.name + " contains duplicate resource: " + resourcePath)

                        dupes.toList().sort().each { source ->
                            System.err.println(" found within dependency: " + source.moduleVersion.id)
                            findRoute(conf, source).toList().sort().each { route ->
                                System.err.println("  imported via: " + route)
                            }
                        }
                    }

                    if (thisHasDupes) hadDupes = true
                }
            }
        }

        if (hadDupes)
            throw new GradleException("Duplicate resources detected")
    }
}

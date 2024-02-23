package classpathHell

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runners.MethodSorters

import java.nio.file.Paths

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import static org.junit.Assert.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ClasspathHellPluginTests {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()

    private File buildFile
    private File propertiesFile

    private List pluginClasspath

    @Before
    void setup() throws IOException {

        buildFile = testProjectDir.newFile('build.gradle')
        propertiesFile = testProjectDir.newFile('gradle.properties')
        URL classpathResource = getClass().classLoader.getResource('plugin-classpath.txt')
        if (classpathResource == null)
            throw new RuntimeException('Cannot find plugin-classpath.txt - did the gradle build correctly create it - run the "createPluginClasspath" task?')
        pluginClasspath = classpathResource.readLines().collect { new File(it) }
    }

    @Test
    void testCanApplySettings() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'com.portingle.classpathHell'

        assertTrue(project.tasks.checkClasspath instanceof ClasspathHellTask)

        ClasspathHellTask task = (ClasspathHellTask) project.tasks.checkClasspath
        task.action()
    }

    @Test
    void testScansConfigurations() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }

            configurations {
                 config1
                 config2
            }
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--info', 'checkClasspath', '--stacktrace', '--refresh-dependencies')
                .withPluginClasspath(pluginClasspath)
        BuildResult result = runner.build()

        assertTrue(result.getOutput().contains("checking configuration : 'config1'"))
        assertTrue(result.getOutput().contains("checking configuration : 'config2'"))
        assertEquals(result.task(":checkClasspath").getOutcome(), SUCCESS)
    }

    @Test
    void testScansSelectiveConfigurations() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }

            configurations {
                 config1
                 config2
            }

            classpathHell {
            
                artifactExclusions = [ ".*hamcrest-all.*" ]

                configurationsToScan = [ configurations.config2 ]
                
            }

            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--info', 'checkClasspath', '--stacktrace', '--refresh-dependencies')
                .withPluginClasspath(pluginClasspath)
        BuildResult result = runner.build()

        assertFalse(result.getOutput().contains("checking configuration : 'config1'"))
        assertTrue(result.getOutput().contains("checking configuration : 'config2'"))
        assertEquals(result.task(":checkClasspath").getOutcome(), SUCCESS)
    }

    @Test
    void testReportsDupes() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            dependencies {
                implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            
            classpathHell {
                trace = true
                configurationsToScan = [ configurations.compileClasspath ]
            }
   
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.buildAndFail()

        def output = result.getOutput()
        assertTrue(output.contains("configuration 'compileClasspath' contains duplicate resource: org/hamcrest/core/CombinableMatcher"))
        assertEquals(result.task(":checkClasspath").getOutcome(), FAILED)
    }

    @Test
    void testReportsNonResolvableProfile() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            dependencies {
                implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            
            classpathHell {
                trace = true
                configurationsToScan = [ configurations.implementation ]
            }
   
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.buildAndFail()

        def output = result.getOutput()
        assertTrue(output.contains("configuration 'implementation' is not resolvable"))
        assertEquals(result.task(":checkClasspath").getOutcome(), FAILED)
    }

    @Test
    void testASuppressionOfExactDupes() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                flatDir {
                    dirs "${project.rootDir}/tmpRepo"
                }
                mavenCentral()
            }
            
            dependencies {
                implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            
            classpathHell {
                // some additional logging; note one must also run with "--info" or "--debug" if you want to see this detail.
                // specify the property as shown below or set the gradle property -PclasspathHell.trace=true
                trace = true

                // only scan one configuration as this makes the test's verification easier                
                //configurationsToScan = [ configurations.compileClasspath]
                 
                resourceExclusions = [ "META-INF/MANIFEST.MF" ]
                
                // configure automatic resolution of "benign" dupes 
                suppressExactDupes = true 
            }
   
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--info', 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        File pd = runner.getProjectDir()

        new java.io.File(pd.getAbsolutePath() + "/tmpRepo").mkdirs()
        new java.io.File(pd.getAbsolutePath() + "/tmpRepo/ping-999.pong").write("NOT A JAR")

        BuildResult result = runner.build() // expect success

        def output = result.getOutput()

        // check suppression trace
        assertTrue(output.contains("org/hamcrest/core/CombinableMatcher\$CombinableBothMatcher.class has been automatically suppressed across : [org.hamcrest:hamcrest-all:1.3, org.hamcrest:hamcrest-core:1.3]"))
        assertTrue(output.contains('org/hamcrest/core/CombinableMatcher\$CombinableBothMatcher.class #md5'))
    }

    @Test
    void testDontUnzipNonZipFiles() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                flatDir {
                    dirs "${project.rootDir}/tmpRepo"
                }
                mavenCentral()
            }
            
            dependencies {
                implementation('jl:ping:999') {
                    artifact {
                        name = 'ping'
                        extension = 'pong'
                        type  = 'ttt'
                    }
                }
            }
            
            classpathHell {
                // some additional logging; note one must also run with "--info" or "--debug" if you want to see this detail.
                // specify the property as shown below or set the gradle property -PclasspathHell.trace=true
                trace = true

                // only scan one configuration as this makes the test's verification easier                
                //configurationsToScan = [ configurations.compileClasspath]
                 
                resourceExclusions = [ "META-INF/MANIFEST.MF" ]
                
                // configure automatic resolution of "benign" dupes 
                suppressExactDupes = true 
            }
   
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--info', 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        File pd = runner.getProjectDir()

        Paths.get(pd.getAbsolutePath(), "tmpRepo").toFile().mkdirs()
        Paths.get(pd.getAbsolutePath(), "tmpRepo", "ping-999.pong").write("NOT A JAR")

        BuildResult result = runner.build() // expect success

        def output = result.getOutput()

        // check suppression trace
        assertTrue(output.contains("classpathHell: including artifact <jl:ping:999>"))
        assertTrue(output.matches("(?s).*classpathHell:.*including resource <.*tmpRepo.*ping-999.pong>.*"))
    }

    @Test
    void testOverrideExtensions() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }

            classpathHell {
                trace = true

                artifactExclusions = [ ".*hamcrest-all.*" ]

                // Demonstrate replacing the default implementation of the rule
                includeArtifact = {
                    artifact ->
                        println("OVERRIDE includeArtifact CHECK OF " + artifact)

                        excludeArtifactPaths(artifactExclusions, artifact)
                }

                // Demonstrate replacing the default set of exclusions
                resourceExclusions = [ ".*/BaseMatcher.class" ]
   
                // scan a only one config so that test logs less
                configurationsToScan = [ configurations.compileClasspath]
               }

            dependencies {
                implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            '''

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--info', 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        // should pass as we've removed the dup artifact
        BuildResult result = runner.build()

        assertTrue(result.getOutput().contains('including artifact <org.hamcrest:hamcrest-core:1.3>'))
        assertTrue(result.getOutput().contains('excluding artifact <org.hamcrest:hamcrest-all:1.3>'))
        assertTrue(result.getOutput().contains('excluding resource <org/hamcrest/BaseMatcher.class>'))
        assertTrue(result.getOutput().contains('OVERRIDE includeArtifact CHECK OF'))
    }

    /**
     * Tests the usage of the configured configurations as in- and outputs of the task.
     * Unchanged dependencies producing no error should result in a skipped / up-to-date task.
     */
    @Test
    void testTaskIsSkippedIfDependenciesDoNotChange() {
        writeBuildFile("1.3", false)

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.build()
        def output = result.getOutput()
        assertEquals("First run should result in SUCCESS", SUCCESS, result.task(":checkClasspath").getOutcome())

        // second run: Task should be skipped since dependencies did not change:
        result = runner.build()
        output = result.getOutput()
        assertEquals("Second run should result in UP_TO_DATE", UP_TO_DATE, result.task(":checkClasspath").getOutcome())

    }

    /**
     * Tests the usage of the configured configurations as in- and outputs of the task.
     * Changed dependencies should result in a second complete check.
     */
    @Test
    void testTaskIsNotSkippedIfDependenciesChange() {
        writeBuildFile("1.1", false)

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.build()
        assertEquals("First run should result in SUCCESS", SUCCESS, result.task(":checkClasspath").getOutcome())

        // Change dependencies for second run (upgrade hamcrest dependency)
        writeBuildFile("1.3", false)

        // second run: Task should be skipped since dependencies did not change:
        result = runner.build()
        assertEquals("Second run should result in SUCCESS", SUCCESS, result.task(":checkClasspath").getOutcome())
    }

    /**
     * Tests the usage of the configured configurations as in- and outputs of the task.
     * If the previous run encountered duplicates the task should not be skipped, even if
     * nothing changed.
     */
    @Test
    void testTaskIsNotSkippedIfDuplicatesExist() {
        writeBuildFile("1.3", true)

        GradleRunner runner = GradleRunner.create()
                .forwardOutput()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments("--info", 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.buildAndFail()
        assertEquals("First run should result in FAILED", FAILED, result.task(":checkClasspath").getOutcome())

        // second run: task should not be skipped since the last run failed!:
        result = runner.buildAndFail()
        assertEquals("Second run should result in FAILED", FAILED, result.task(":checkClasspath").getOutcome())

    }

    /**
     * Overwrites the existing build file with a file including a single hamcrest-all dependency, using
     * the provided version from the parameter.
     *
     * @param hamcrestVersion the version of hamcrest-all to write to the build file.
     */
    private void writeBuildFile(String hamcrestVersion, boolean includeHamcrestCore) {
        buildFile.withWriter { writer ->
            writer << """
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            dependencies {
                implementation group: 'org.hamcrest', name: 'hamcrest-all', version: '$hamcrestVersion'
                ${if (includeHamcrestCore) {
                    "                implementation group: 'org.hamcrest', name: 'hamcrest-core', version: '$hamcrestVersion'"
                }}

            }
            
            classpathHell {
                trace = true
                configurationsToScan = [ configurations.compileClasspath ]
            }
   
            """
        }
    }
}

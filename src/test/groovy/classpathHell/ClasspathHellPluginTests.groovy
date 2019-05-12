package classpathHell

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class ClasspathHellPluginTests {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    private File buildFile;
    private File propertiesFile

    private List pluginClasspath

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile('build.gradle')
        propertiesFile = testProjectDir.newFile('gradle.properties')
        URL classpathResource = getClass().classLoader.getResource('plugin-classpath.txt')
        if (classpathResource==null)
            throw new RuntimeException('Cannot find plugin-classpath.txt - did the gradle build correctly create it?')
        pluginClasspath = classpathResource.readLines().collect { new File(it) }
    }

    @Test
    public void testCanApply() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'com.portingle.classpathHell'

        assertTrue(project.tasks.checkClasspath instanceof ClasspathHellTask)

        ClasspathHellTask task = project.tasks.checkClasspath
        task.action()
    }

    @Test
    public void testScansConfigurations() {
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
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--debug', 'checkClasspath', '--stacktrace', '--refresh-dependencies')
                .withPluginClasspath(pluginClasspath)
        BuildResult result = runner.build()

        assertTrue(result.getOutput().contains("checking configuration ':config1'"));
        assertTrue(result.getOutput().contains("checking configuration ':config2'"));
        assertEquals(result.task(":checkClasspath").getOutcome(), SUCCESS);
    }

    @Test
    public void testScansSelectiveConfigurations() {
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
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--debug', 'checkClasspath', '--stacktrace', '--refresh-dependencies')
                .withPluginClasspath(pluginClasspath)
        BuildResult result = runner.build()

        assertFalse(result.getOutput().contains("checking configuration ':config1'"));
        assertTrue(result.getOutput().contains("checking configuration ':config2'"));
        assertEquals(result.task(":checkClasspath").getOutcome(), SUCCESS);
    }

    @Test
    public void testReportsDupes() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }
            dependencies {
                compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            '''

        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.buildAndFail()

        assertTrue(result.getOutput().contains('classpath: compile contains duplicate resource: org/hamcrest/core/CombinableMatcher$CombinableBothMatcher.class'));
        assertEquals(result.task(":checkClasspath").getOutcome(), FAILED);

    }

    @Test
    public void testOverrideExtensions() {
        buildFile << '''
            plugins {
                id 'com.portingle.classpathHell'
            }
            apply plugin: 'java'

            repositories {
                mavenCentral()
            }

            classpathHell {

                artifactExclusions = [ ".*hamcrest-all.*" ]

                // Demonstrate replacing the default implementation of the rule
                includeArtifact = {
                    artifact ->
                        println("OVERRIDE includeArtifact CHECK OF " + artifact)

                        excludeArtifactPaths(artifactExclusions, artifact)
                }

                // Demonstrate replacing the default set of exclusions
                resourceExclusions = [ ".*/BaseMatcher.class" ]
            }

            dependencies {
                compile group: 'org.hamcrest', name: 'hamcrest-all', version: '1.3'
                compile group: 'org.hamcrest', name: 'hamcrest-core', version: '1.3'
            }
            '''

        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('--debug', 'checkClasspath')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.build()

        assertTrue(result.getOutput().contains('including artifact <org.hamcrest:hamcrest-core:1.3>'))
        assertTrue(result.getOutput().contains('excluding artifact <org.hamcrest:hamcrest-all:1.3>'))
        assertTrue(result.getOutput().contains('excluding resource <org/hamcrest/BaseMatcher.class>'))
        assertTrue(result.getOutput().contains('OVERRIDE includeArtifact CHECK OF'))
    }
}

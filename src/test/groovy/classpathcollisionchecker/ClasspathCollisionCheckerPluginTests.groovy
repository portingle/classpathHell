package classpathcollisionchecker

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.gradle.testkit.runner.TaskOutcome.*;

class ClasspathCollisionCheckerPluginTests {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    private File buildFile;
    File propertiesFile

    List pluginClasspath

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile('build.gradle')
        propertiesFile = testProjectDir.newFile('gradle.properties')
        pluginClasspath = getClass().classLoader.findResource('plugin-classpath.txt').readLines().collect {
            new File(it)
        }
    }

    @Test
    public void testCanApply() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'org.portingle.classpathCollisionChecker'

        assertTrue(project.tasks.classpathCollisionCheck instanceof ClasspathCollisionCheckerTask)

        ClasspathCollisionCheckerTask task = project.tasks.classpathCollisionCheck
        task.classpathCollisionCheck()
    }

    @Test
    public void testScansConfigurations() {
        buildFile << '''
            plugins {
                id 'org.portingle.classpathCollisionChecker'
            }

            configurations {
                 config1
                 config2
            }
            '''

        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withArguments('classpathCollisionCheck', '--stacktrace', '--refresh-dependencies')
                .withPluginClasspath(pluginClasspath)
        BuildResult result = runner.build()

        assertTrue(result.getOutput().contains("checking configuration ':config1'"));
        assertTrue(result.getOutput().contains("checking configuration ':config2'"));
        assertEquals(result.task(":classpathCollisionCheck").getOutcome(), SUCCESS);
    }

    @Test
    public void testReportsDupes() {
        buildFile << '''
            plugins {
                id 'org.portingle.classpathCollisionChecker'
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
                .withArguments('classpathCollisionCheck')
                .withPluginClasspath(pluginClasspath)

        BuildResult result = runner.buildAndFail()

        assertTrue(result.getOutput().contains('classpath: compile contains duplicate resource: org/hamcrest/core/CombinableMatcher$CombinableBothMatcher.class'));
        assertEquals(result.task(":classpathCollisionCheck").getOutcome(), FAILED);
    }

}

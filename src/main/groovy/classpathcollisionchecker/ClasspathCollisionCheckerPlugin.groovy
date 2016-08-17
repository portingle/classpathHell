package classpathcollisionchecker

import org.gradle.api.Plugin
import org.gradle.api.Project

class ClasspathCollisionCheckerPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create("classpathCollisionChecker", ClasspathCollisionCheckerPluginExtension)

        project.task('classpathCollisionCheck', type: ClasspathCollisionCheckerTask)
    }
}

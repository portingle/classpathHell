package classpathHell

import org.gradle.api.Plugin
import org.gradle.api.Project

class ClasspathHellPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create("classpathHell", ClasspathHellPluginExtension)

        project.task('checkClasspath', type: ClasspathHellTask)
    }
}

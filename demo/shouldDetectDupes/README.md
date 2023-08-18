1. First do a `.\gradlew publishToMavenLocal` of the main project to put the plugin jar into the local maven repo.

2. Then run `..\..\gradlew clean build -i` here.

The build should fail due to duplicate licence files and manifests only, as the build.gradle suppresses checks on classes and directories.
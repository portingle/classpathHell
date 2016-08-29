1. First do a `gradle install` of the main project to put the plugin jar into the local maven repo.

2. Then run `gradle build` here.

The build should fail due to duplicate licence files and manifests only, as the build.gradle suppresses checks on classes and directories.
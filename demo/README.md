First do a `gradle install` of the main project to put the plugin jar into the local maven repo.

The run `gradle build` here.

The build should fail dur to duplicate licence files and manifests only, as the build.gradle suppresses checks on classes and directories.
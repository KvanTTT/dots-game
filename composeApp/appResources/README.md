# The files the app is shipped with

The desktop installer carries the KataGoDots engine, its config and its model, so that the app plays
out of the box, see `KataGoDotsEngine.defaultSettings`. Compose merges `common` and the directory of
the platform being packaged into a single directory of the package, which the app is told by the
`compose.application.resources.dir` property, see `appResourcesRootDir` in `build.gradle.kts`.

```
common/KataGoDots/analysis_dots.cfg    the config of the analysis engine, from the KataGoDots repository
common/KataGoDots/model.bin.gz         the model the engine plays with
<platform>/KataGoDots/katago[.exe]     the engine built for that platform
```

The platform directories are the ones Compose names: `macos-arm64`, `macos-x64`, `windows-x64`,
`linux-x64`, `linux-arm64`.

None of the three is kept in this repository: the CI puts them here before packaging, taking the engine
and the config from the very same build of KataGoDots, and a local build of an installer needs them
copied in by hand. An app that is packaged without them is
built all the same, with the engine simply switched off until the settings point it at one.

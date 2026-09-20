# The files the app is shipped with

The desktop installer carries the KataGoDots engine, its config and its model, so that the app plays
out of the box, see `KataGoDotsEngine.defaultSettings`. Compose merges `common` and the directory of
the platform being packaged into a single directory of the package, which the app is told by the
`compose.application.resources.dir` property, see `appResourcesRootDir` in `build.gradle.kts`.

```
common/KataGoDots/analysis_dots.cfg    the config of the analysis engine, from the KataGoDots repository
common/KataGoDots/model.bin.gz         the model the engine plays with
<platform>/KataGoDots/katago[.exe]     the engine built for that platform
<platform>/KataGoDots/*.dll, libs/     the libraries the engine is linked against, where it needs any
```

The platform directories are the ones Compose names: `macos-arm64`, `macos-x64`, `windows-x64`,
`linux-x64`, `linux-arm64`.

None of them is kept in this repository: `publish.yml` puts them here before packaging, and a local
build of an installer needs them copied in by hand.

- The engine is built from the revision of KataGoDots that `ENGINE_REF` names, with the backend that
  plays well on the platform: Metal on macOS, which is a part of the system itself, and OpenCL on
  Windows and Linux, which runs on the graphics of the machine wherever a driver for it is installed
  and needs one to be. It is built without libzip, which the
  engine only needs for writing training data, so that it is linked against as little as possible; the
  libraries it is still linked against are shipped next to it, by `bundle-macos-libraries.sh` on macOS
  and by copying the DLLs of vcpkg on Windows.
- The config is taken from the very same revision of the engine.
- The model is downloaded from the `KATA_GO_DOTS_MODEL_URL` variable of the repository. It's too large
  to keep here comfortably, but a model committed to this directory is used as it is when the variable
  names none - `git add -f composeApp/appResources/common/KataGoDots/model.bin.gz`. An app that is packaged without them is
built all the same, with the engine simply switched off until the settings point it at one.

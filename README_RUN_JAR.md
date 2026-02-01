# OpenDS Single-JAR Build

This repo already builds a cross-platform "fat" JAR using Maven's
`jar-with-dependencies` packaging. You can run that JAR on Windows,
macOS, or Linux as long as Java 8+ is installed.

## Build

```bash
./scripts/build_multi_os_jar.sh
```

Force a clean Docker rebuild:

```bash
./scripts/build_multi_os_jar.sh --rebuild
```

This produces:

```
dist/OpenDS.jar
dist/run.sh
dist/run.bat
dist/README.txt
```

## Run

- macOS/Linux: `./dist/run.sh`
- Windows: `dist\run.bat`

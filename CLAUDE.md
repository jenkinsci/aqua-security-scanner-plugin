# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jenkins plugin for scanning Docker/Podman container images using the Aqua Security platform. Packaged as an HPI (Jenkins plugin archive). Supports freestyle jobs and declarative/scripted pipelines via the `@Symbol("aqua")` annotation.

## Build Commands

```bash
mvn package              # Build plugin → target/aqua-security-scanner.hpi
mvn test                 # Run tests (Jenkins test harness + CasC integration tests)
mvn spotbugs:spotbugs    # Static analysis
```

Requires JDK 11+ and Maven 3+. Build targets Jenkins 2.440.3 baseline (parent POM 4.88). Can also build inside Docker without local JDK/Maven:

```bash
cd test/jenkins-docker && docker compose up --build -d   # Builds plugin + starts Jenkins
```

## Architecture

Four classes in `src/main/java/org/jenkinsci/plugins/aquadockerscannerbuildstep/`:

- **AquaDockerScannerBuilder** — Main build step (`extends Builder implements SimpleBuildStep`). Contains the `DescriptorImpl` inner class (`@Extension`) that manages global config (API URL, credentials, scanner image) and per-job config (image name, location type, policies). Entry point is `perform()`. The `DescriptorImpl` has `@DataBoundSetter` methods for all global config fields, enabling CasC support.

- **ScannerExecuter** — Static `execute()` method that builds and runs the `docker run`/`podman run` command for the Aqua scanner CLI. Handles three image location types: `LOCAL`, `HOSTED`, `DOCKERARCHIVE`. Captures HTML report output, copies artifacts to workspace.

- **AquaScannerRegistryLogin** — Handles Docker/Podman registry authentication for pulling the scanner image. Parses registry names from image URIs, passes passwords via stdin.

- **AquaScannerAction** — Build action that adds a sidebar link to scan results. Embeds the HTML report in an iframe. Uses UUID suffixes for parallel build support.

## Configuration as Code (CasC)

The plugin supports JCasC. Global config fields are exposed under `unclassified.aqua`. Auth simplification: leave `user` empty and set `password` to use it as a token — the routing happens in `perform()`. Registry login triggers automatically when `registryUsername` is set (no need to set `registryAuthType`).

CasC integration tests are in `src/test/java/.../CasCTest.java` with YAML fixtures in `src/test/resources/.../casc.yaml`.

## Execution Flow

`perform()` → validates config → routes password-as-token if user is empty → `ScannerExecuter.execute()` → builds docker/podman command → launches scanner → captures HTML output → archives `scanout-{UUID}.html` + `styles.css` → adds `AquaScannerAction` to build → returns exit code (0=pass, 4=policy failure).

## Authentication Priority

Local token (per-job) > Global token > Global password-as-token (when user is empty) > Global username/password. All credentials use `hudson.util.Secret` for encryption.

## Jenkins UI Configuration

- **Global config**: `src/main/resources/.../AquaDockerScannerBuilder/global.jelly`
- **Job config**: `src/main/resources/.../AquaDockerScannerBuilder/config.jelly`
- **Help files**: `help-*.html` in resources and webapp directories
- **Results view**: `AquaScannerAction/index.jelly` (iframe-based)

Uses Stapler data binding (`@DataBoundConstructor`, `@DataBoundSetter`).

## Local Development

`test/jenkins-docker/` contains a Docker Compose environment that builds the plugin and starts Jenkins LTS (JDK 21) with CasC pre-configured. Copy `.env.example` to `.env` with real Aqua credentials. The `jenkins.yaml` uses `${ENV_VAR}` substitution for secrets.

## Key Details

- Default scanner image: `registry.aquasec.com/scanner:2022.4`
- Environment variable expansion (`$VAR`, `${VAR}`) is supported in image names and paths via `ScannerExecuter`
- Podman support includes rootless mode via `XDG_RUNTIME_DIR` and `--security-opt label=disable`
- Docker archive scanning uses `--fs-scan /` with tar file volume mount
- Sensitive values are masked in build logs via `ArgumentListBuilder.addMasked()`
- Plugin installation: copy `.hpi` to `$JENKINS_HOME/plugins/` and restart
- Dependencies are managed via Jenkins BOM (`bom-2.440.x`); avoid pinning versions manually

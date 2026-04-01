# Aqua Security Scanner Jenkins Plugin #

This is a Jenkins plugin for calling the Aqua API to scan a Docker image.

## Requirements ##

- **Jenkins**: 2.440.3 or later (tested up to 2.541.3 LTS)
- **Java**: 11 or later (tested with JDK 17 and JDK 21)
- **Docker** or **Podman** installed on the Jenkins machine
- The *jenkins* user must be in the *docker* group:
  ```
  sudo usermod -aG docker jenkins
  ```

## Configuration as Code (CasC) ##

The plugin supports [Jenkins Configuration as Code](https://plugins.jenkins.io/configuration-as-code/). Add the following to your `jenkins.yaml`:

```yaml
unclassified:
  aqua:
    aquaScannerImage: "registry.aquasec.com/scanner:2022.4"
    apiURL: "https://your-aqua-server.com"
    # Leave user empty to use password as a token
    user: ""
    password: "your-token-or-password"
    timeout: 300
    runOptions: ""
    caCertificates: false
    registryUsername: "registry-user"
    registryPassword: "registry-password"
```

Environment variable substitution is supported: `"${AQUA_API_URL}"`.

**Authentication**: Set `user` and `password` for username/password auth. Leave `user` empty to use `password` as an API token.

**Registry credentials**: If `registryUsername` is set, the plugin will automatically log in to the scanner image registry before pulling.

## Manual Configuration ##

In the global configuration page ("Manage Jenkins" / "Configure System"), in the "Aqua Security" section, enter the Aqua API URL, credentials, scanner image, and timeout.

In the configuration page for your project, add an "Aqua Security" step from the "Add build step" dropdown. Choose between a local image, hosted image, or docker archive. Enter the image path including tag. Values support `$VARIABLE` syntax for environment variables.

### Pipeline Usage ###

```groovy
aqua customFlags: '',
     hideBase: false,
     hostedImage: '',
     localImage: 'my-app:latest',
     locationType: 'local',
     notCompliesCmd: '',
     onDisallowed: 'ignore',
     policies: '',
     register: false,
     registry: '',
     showNegligible: true
```

## Building the plugin ##

Requires JDK 11+ and Maven 3+:

```
mvn package
```

The plugin HPI is produced at `target/aqua-security-scanner.hpi`.

## Local Development with Docker ##

A Docker-based test environment is provided in `test/jenkins-docker/`:

```bash
# Create a .env file with your Aqua credentials (see .env.example)
cp test/jenkins-docker/.env.example test/jenkins-docker/.env
# Edit .env with your values

# Build and start Jenkins with the plugin pre-installed
cd test/jenkins-docker
docker compose up --build -d
```

Jenkins will be available at http://localhost:8080 (admin/admin). The plugin is built inside Docker (no local Maven/JDK needed) and configured via CasC from `jenkins.yaml`.

## Installing manually ##

Copy `target/aqua-security-scanner.hpi` to `$JENKINS_HOME/plugins/` and restart Jenkins.

## Releasing ##

See [Hosting Plugins](https://www.jenkins.io/doc/developer/publishing/releasing/). Execute:
```
mvn release:prepare release:perform
```

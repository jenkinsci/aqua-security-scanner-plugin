# Description

Adds a Build Step for scanning Docker images, local or hosted on
registries, for security vulnerabilities, using the API provided by
[Aqua Security](https://www.aquasec.com)

## Changelog:

#### **Version 3.2.10 (August 6, 2025)**

### Security Fixes
- **Correction for SECURITY-3542 / CVE-2025-53653**: This release refines the implementation for local scanner token encryption to fully align with Jenkins security best practices.
- **Rationale**:
  - The previous fix introduced the `Secret` type but could still expose the encrypted token value in the `config.xml` under certain circumstances.
  - This change ensures the token is handled more securely by the Jenkins framework, preventing this potential information disclosure and strengthening the migration path for legacy plaintext tokens.

### Technical Changes
- Removed the custom `getLocalTokenForXml()` method in favor of a standard `getLocalToken()` getter that returns a `Secret` object.
- Simplified and corrected the `readResolve()` method to ensure proper lazy migration of legacy plaintext tokens.
- Updated `config.jelly` to remove the unnecessary hidden field and bind directly to the `Secret` field.


#### **Version 3.2.9 (July 16, 2025)**

### Security Fixes
- **SECURITY-3542 / CVE-2025-53653**: Fixed tokens stored in plain text vulnerability
  - Local scanner tokens are now properly encrypted using Jenkins' Secret class
  - Tokens are no longer stored in plain text in job config.xml files
  - Added backward compatibility for existing configurations
  - Users with Item/Extended Read permission can no longer view tokens in plain text
  - Implemented migration approach that automatically encrypts tokens when jobs are loaded or executed
  - Added XML serialization control to ensure tokens are always saved in encrypted format

### Bug Fixes
- Fixed an issue where the custom scanner path was not respected during local image scans

### Dependency Security Updates
- Updated `org.codehaus.plexus:plexus-utils` from 3.0.10 to 3.0.24 to fix:
  - **CVE-2017-1000487** (CRITICAL): Fixed command injection vulnerability in Commandline class
  - **CVE-2022-4244** (HIGH): Fixed directory traversal vulnerability
  - **CVE-2022-4245** (MEDIUM): Fixed XML External Entity (XXE) injection vulnerability
- Updated `org.jenkins-ci.plugins:structs` to version 1.24 for improved compatibility

### Technical Changes
- Changed `localToken` field from `String` to `Secret` type
- Added `readResolve()` method for configuration migration
- Added custom XML serialization method to ensure tokens are always encrypted
- Enhanced job execution to detect and migrate plaintext tokens
- Updated token handling in `ScannerExecuter` class
- Improved build process with containerized build support

### Migration Notes
- Existing configurations will be automatically migrated when jobs are loaded or executed
- No manual intervention required for existing installations
- Tokens will be re-encrypted on first job execution or configuration save
- Migration is logged in the Jenkins console for transparency

#### **Version 3.2.8 (Apr 1, 2025)**

The image scanning now includes two options for Aqua registry authentication to pull the scanner image:
- **Manual authentication to the registry hosting scanner image:** Perform manual authentication during the build process 
- **Username/Password for authentication to the registry hosting scanner image:** Enter the username and password of the registry hosting scanner image

#### **Version 3.2.7 (Dec 11, 2024)**

- Added rootless podman support to scan images using podman.
- Added `Podman socket directory (applicable to non-root users)` in build job configurations which accepts runtime directory.

#### **Version 3.2.6 (Sep 30, 2024)**

- Added `Hide base image vulnerabilties` flag for all scan types in build job configurations.

#### **Version 3.2.5 (Sep 14, 2023)**

- Fix [SLK-69159] StringIndexOutOfBoundsException occuring inconsistently when scanning images.

Happened because of we are not handling `String.substring()` properly, Now its handled.

#### **Version 3.2.4 (Jun 14, 2023)**

- Fix [JENKINS-71287](https://issues.jenkins.io/browse/JENKINS-71287) plugin is overriding   reports when several builds\jobs are running in parallel
- Update the Action Menu items added by the plugin to contain the scanned image name.

#### **Version 3.2.2 (Feb 02, 2023)**

-   For each assurance policy failure, show the name of the specific controls that failed.

#### **Version 3.2.1 (Feb 25, 2022)**

-   Made localToken an optional field which accepts string value in pipeline syntax. 

#### **Version 3.2 (Jan 19, 2022)**

-   Added support of aqua scanner token for authentication at the global and job level settings.

#### **Version 3.1.2 (Sept 16, 2021)**

-   Added custom container runtime scanning support with following optional fields:
    1. containerRuntime
    2. scannerPath

#### **Version 3.1.1 (Sept 15, 2021)**

-   Reverted the container runtime support added in 3.1.0 due to backward compatibility support.

#### **Version 3.1.0 (Sept 15, 2021)**

-   Added custom container runtime scanning support

>Note: Please add empty values("") for following parameters in pipeline 
>1. containerRuntime 
>2. scannerPath

#### **Version 3.0.25 (May 31, 2021)**

-   Updates for cloudbees
-   Fix issue with css for cloudbees

#### **Version 3.0.24 (Jan 21, 2021)**

-   Update css static file
-   Fix issue with stappler logging error

#### **Version 3.0.23 (Nov 17, 2020)**

-   Adding docker archive support for scanning tar files.
-   Fix issue with scanner report has random string

#### **Version 3.0.22 (May 26, 2020)**

-   Adding support for DTA scan results.

#### **Version 3.0.21 (Oct 29 19, 2019)**

-   Migrate to GitHub docs

#### **Version 3.0.19 (Oct 29 19, 2019)**

-   Remove scanner default image

#### **Version 3.0.18 (Sep 19, 2019)**

-   Jenkins global configuration improvements

#### **Version 3.0.17 (June 17, 2019)**

-   Update scanner default version to 4.2 and changing global settings
    checkbox text

#### **Version 3.0.16 (April 5, 2019)**

-   Adding encryption in the persisted in forms Url/User/Pass**  
    **

#### **Version 3.0.15 (March 14, 2019)**

-   Adding support for custom flags.

#### **Version 3.0.14 (November 25, 2018)**

-   Change default registry.

#### **Version 3.0.13 (November 25, 2018)**

-   Allow "Register" checkbox on local and hosted images.**  
    **

#### **Version 3.0.12 (October 22, 2018)**

-   Bug fix: Fixing error when job name have space.**  
    **

#### **Version 3.0.11 (September 20, 2018)**

-   Adding support for --policies force use of provided image assurance
    policies (local scans only)**  
    **

#### **Version 3.0.10 (September 13, 2018)**

-   Report build ID,build URL,build name from the running Jenkins Job to
    Aqua Console.**  
    **

#### **Version 3.0.9 (August 28, 2018)**

-   Support html output without lower jenkins security in the script
    console.
-   Change default version to 3.x

#### **Version 3.0.8 (August 6, 2018)**

-   Adding support for k8s jenkins plugin.

#### **Version 3.0.7 (June 18, 2018)**

-   Adding support for --no-verify. (Do not verify TLS certificates)

#### **Version 3.0.6 (May 13, 2018)**

-   Adding multiple images artifact archive support.**  
    **

#### **Version 3.0.5 (April 30, 2018)**

-   Bug fix: Fixing policy not saved on UI.
-   Bug fix: Fixing password masking when runOptions is set.
-   Adding support to register remote images.

#### **Version 3.0.3 (April 9, 2018)**

-   Bug fix: plugin archive the entire working directory.

#### **Version 3.0 (March 19, 2018)**

-   Support for Jenkins pipeline. 

#### **Version 2.0 (February 6, 2017)**

-   Two new checkboxes in the step definition control whether base image
    vulnerabilities are hidden (for hosted images only) and whether
    negligible vulnerabilities are shown.
-   Additional options for the "docker run" command running the scanner
    can be specified in the "Configure System" page.
-   If the plugin has not been configured in the "Configure System"
    page, a message is displayed directing the user to do so.
-   Multiple Aqua Scanner steps in a build are now supported, each
    resulting in its own output.

#### **Version 1.3.3 (October 15, 2016)**

-   A shell command to be run when the scanned image does not comply
    with Aqua policy, can be specified.

#### **Version 1.3.2 (September 11, 2016)**

-   Bug fix:. could not run steps from 1.3 without re-saving
    configuration.

#### **Version 1.3.1 (August 22, 2016)**

-   In the build page, there are now icons display the scan results.
-   The artifacts are now archived automatically and there is no need
    for the "Archive the artifacts" post-build step.
-   In the build step, you can decide whether the build fails or not,
    when the scanned image does not comply with Aqua policy.

#### **Version 1.3 (July 29, 2016)**

-   Aqua's scanner image can be set in the global configuration.
-   Artifact is now an HTML report.

#### Version 1.1 (June 19, 2016)

-   First release.

package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.Launcher;
import hudson.EnvVars;
import hudson.Launcher.ProcStarter;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.PrintStream;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class does the actual execution..
 *
 * @author Oran Moshai
 */
public class ScannerExecuter {

	public static int execute(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, String artifactName,
			String aquaScannerImage, String apiURL, String user, Secret password, String version, int timeout,
			String runOptions, String locationType, String localImage, String registry, boolean register, String hostedImage,
			boolean hideBase, boolean showNegligible, boolean checkonly, String notCompliesCmd, boolean caCertificates,
			String policies, String customFlags, String tarFilePath) {

		PrintStream print_stream = null;
		try {
			// Form input might be in $VARIABLE or ${VARIABLE} form, expand.
			// expand() is a noop for strings not in the above form.
			final EnvVars env = build.getEnvironment(listener);
			localImage = env.expand(localImage);
			registry = env.expand(registry);
			hostedImage = env.expand(hostedImage);
			tarFilePath = env.expand(tarFilePath);

			// extract file name from path for scan tagging
			Path path = Paths.get(tarFilePath);
			Path fileName = path.getFileName();
			String imgName = fileName.toString().split("\\.")[0];

			ArgumentListBuilder args = new ArgumentListBuilder();
			args.add("docker", "run");
			String buildJobName = env.get("JOB_NAME").trim();
			buildJobName = buildJobName.replaceAll("\\s+", "");
			String buildUrl = env.get("BUILD_URL");
			String buildNumber = env.get("BUILD_NUMBER");
			args.addTokenized("-e BUILD_JOB_NAME="+buildJobName+" -e BUILD_URL="+buildUrl+" -e BUILD_NUMBER="+buildNumber);
			switch (locationType) {
			case "hosted":
				args.addTokenized(runOptions);
				if (version.trim().equals("2.x")) {
					args.add("--rm", aquaScannerImage, "--host", apiURL,
							"--registry", registry, "--image", hostedImage);
					if (timeout > 0) { // 0 means use default
						args.add("--timeout", String.valueOf(timeout));
					}

				} else if (version.trim().equals("3.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan",
					    "--host", apiURL, "--registry", registry,
							hostedImage);
				}

				if (hideBase) {
					args.add("--hide-base");
				}
				if (register) {
					args.add("--register");
				}
				break;
			case "local":
				args.addTokenized(runOptions);
				if (version.trim().equals("2.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "--host", apiURL, "--local", "--image", localImage);
				} else if (version.trim().equals("3.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan", "--host", apiURL, "--local", localImage);
				}
				if (register) {
					args.add("--registry", registry);
					args.add("--register");
				}
				break;
			case "dockerarchive":
				args.addTokenized(runOptions);
				if (version.trim().equals("3.x")) {
					args.add("--rm", "-v", tarFilePath+":"+tarFilePath, aquaScannerImage, "scan", imgName+":tar", "--host", apiURL, "--docker-archive", tarFilePath);
				}
				break;
			default:
				return -1;
			}

			if (showNegligible) {
				args.add("--show-negligible");
			}
			if (checkonly) {
				args.add("--checkonly");
			}
			if (caCertificates) {
				args.add("--no-verify");
			}
			if (policies != null && !policies.equals("")) {
				args.add("--policies", policies);
			}
			if(customFlags != null && !customFlags.equals("")) {
				args.addTokenized(customFlags);
			}

			args.add("--html", "--user", user, "--password");
			args.addMasked(password);

			File outFile = new File(build.getRootDir(), "out");
			Launcher.ProcStarter ps = launcher.launch();
			ps.cmds(args);
			ps.stdin(null);
			print_stream = new PrintStream(outFile, "UTF-8");
			ps.stderr(print_stream);
			ps.stdout(print_stream);
			ps.quiet(true);
			listener.getLogger().println(args.toString());
			int exitCode = ps.join(); // RUN !

			// Copy local file to workspace FilePath object (which might be on remote
			// machine)
			//FilePath workspace = build.getWorkspace();
			FilePath target = new FilePath(workspace, artifactName);
			FilePath outFilePath = new FilePath(outFile);
			outFilePath.copyTo(target);

			//css
			FilePath targetCss = new FilePath(workspace, "styles.css");
			File cssFile = new File(env.get("JENKINS_HOME") + "/plugins/aqua-security-scanner/css/", "styles.css");
			FilePath cssFilePath = new FilePath(cssFile);
			cssFilePath.copyTo(targetCss);

			String scanOutput = target.readToString();
			cleanBuildOutput(scanOutput, target, listener);
			// Possibly run a shell command on non compliance
			if (exitCode == AquaDockerScannerBuilder.DISALLOWED_CODE && !notCompliesCmd.trim().isEmpty()) {
				ps = launcher.launch();
				args = new ArgumentListBuilder();
				args.add("bash", "-c", notCompliesCmd);
				ps.cmds(args);
				ps.stdin(null);
				ps.stderr(listener.getLogger());
				ps.stdout(listener.getLogger());
				ps.join(); // RUN !

			}

			return exitCode;

		} catch (RuntimeException e) {
			listener.getLogger().println("RuntimeException:" + e.toString());
			return -1;
		} catch (Exception e) {
			listener.getLogger().println("Exception:" + e.toString());
			return -1;
		} finally {
			if (print_stream != null) {
				print_stream.close();
			}
		}
	}
	//Read output save HTML and print stderr
	private static boolean cleanBuildOutput(String scanOutput, FilePath target, TaskListener listener) {

		int htmlStart = scanOutput.indexOf("<!DOCTYPE html>");
		if (htmlStart == -1)
		{
			listener.getLogger().println(scanOutput);
			return false;
		}
		listener.getLogger().println(scanOutput.substring(0,htmlStart));
		int htmlEnd = scanOutput.lastIndexOf("</html>") + 7;
		scanOutput = scanOutput.substring(htmlStart,htmlEnd);
		try
		{
			target.write(scanOutput, "UTF-8");
		}
		catch (Exception e)
		{
			listener.getLogger().println("Failed to save HTML report.");
		}

		return true;
	}
}
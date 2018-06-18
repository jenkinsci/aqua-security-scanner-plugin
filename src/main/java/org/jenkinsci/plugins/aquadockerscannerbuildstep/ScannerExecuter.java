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

/**
 * This class does the actual execution..
 *
 * @author Moshe Cohen
 */
public class ScannerExecuter {

	public static int execute(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, String artifactName,
			String aquaScannerImage, String apiURL, String user, String password, String version, int timeout,
			String runOptions, String locationType, String localImage, String registry, boolean register, String hostedImage,
			boolean hideBase, boolean showNegligible, boolean checkonly, String notCompliesCmd, boolean caCertificates) {

		PrintStream print_stream = null;
		try {
			// Form input might be in $VARIABLE or ${VARIABLE} form, expand.
			// expand() is a noop for strings not in the above form.
			final EnvVars env = build.getEnvironment(listener);
			localImage = env.expand(localImage);
			registry = env.expand(registry);
			hostedImage = env.expand(hostedImage);

			int passwordIndex = -1;
			int runOptionsCount = 0;
			if(runOptions != null && !runOptions.isEmpty()){
				runOptionsCount = countRunOptions(runOptions);
			}

			ArgumentListBuilder args = new ArgumentListBuilder();
			switch (locationType) {
			case "hosted":
				args.add("docker", "run");
				args.addTokenized(runOptions);
				if (version.trim().equals("2.x")) {
					args.add("--rm", aquaScannerImage, "--user", user, "--password", password, "--host", apiURL,
							"--registry", registry, "--image", hostedImage, "--html");
					if (timeout > 0) { // 0 means use default
						args.add("--timeout", String.valueOf(timeout));
					}
					passwordIndex = 7;

				} else if (version.trim().equals("3.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan",
							"--user", user, "--password", password, "--host", apiURL, "--registry", registry,
							hostedImage, "--html");
					passwordIndex = 10;
				}

				if (hideBase) {
					args.add("--hide-base");
				}
				if (register) {
					args.add("--register");
				}
				break;
			case "local":
				args.add("docker", "run");
				args.addTokenized(runOptions);
				if (version.trim().equals("2.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "--user",
							user, "--password", password, "--host", apiURL, "--local", "--image", localImage, "--html");
					passwordIndex = 9;
				} else if (version.trim().equals("3.x")) {
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan",
							"--user", user, "--password", password, "--host", apiURL, "--local", localImage, "--html");
					passwordIndex = 10;
				}
				if (register) {
					args.add("--registry", registry);
					args.add("--register");
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

			File outFile = new File(build.getRootDir(), "out");
			Launcher.ProcStarter ps = launcher.launch();
			ps.cmds(args);
			ps.stdin(null);
			ps.stderr(listener.getLogger());
			print_stream = new PrintStream(outFile, "UTF-8");
			ps.stdout(print_stream);
			boolean[] masks = new boolean[ps.cmds().size()];
			passwordIndex = passwordIndex + runOptionsCount;
			masks[passwordIndex] = true; // Mask out password

			ps.masks(masks);
			int exitCode = ps.join(); // RUN !

			// Copy local file to workspace FilePath object (which might be on remote
			// machine)
			//FilePath workspace = build.getWorkspace();
			FilePath target = new FilePath(workspace, artifactName);
			FilePath outFilePath = new FilePath(outFile);
			outFilePath.copyTo(target);

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
	private static int countRunOptions(String runOptions) {
		int runOptionsLen = runOptions.length();
		int runOptionsCount = 1;
		char checkSpace;
		for(int i=0;i<runOptionsLen;i++)
		{
			checkSpace = runOptions.charAt(i);
			if (checkSpace == ' ')
			runOptionsCount++;
		}
		return runOptionsCount;
	}
}

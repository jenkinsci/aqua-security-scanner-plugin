package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.PrintStream;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import jenkins.model.Jenkins;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class does the actual execution..
 *
 * @author Oran Moshai
 */
public class ScannerExecuter {

	public static int execute(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, String artifactName,
			String aquaScannerImage, String apiURL, String user, Secret password, Secret token, int timeout,
			String runOptions, String locationType, String localImage, String registry, boolean register, String hostedImage,
			boolean hideBase, boolean showNegligible, boolean checkonly, String notCompliesCmd, boolean caCertificates,
			String policies, Secret localTokenSecret, String customFlags, String tarFilePath, String containerRuntime, String scannerPath) {

		PrintStream print_stream = null;
		try {
			// Form input might be in $VARIABLE or ${VARIABLE} form, expand.
			// expand() is a noop for strings not in the above form.
			final EnvVars env = build.getEnvironment(listener);
			localImage = env.expand(localImage);
			registry = env.expand(registry);
			hostedImage = env.expand(hostedImage);
			tarFilePath = env.expand(tarFilePath);

			ArgumentListBuilder args = new ArgumentListBuilder();

			if(containerRuntime == null){
				containerRuntime = "";
			}
			if(scannerPath == null){
				scannerPath = "";
			}
			if(localTokenSecret == null){
				localTokenSecret = Secret.fromString("");
			}

			boolean isDocker = false;
			if("".equals(containerRuntime) || "docker".equals(containerRuntime)) {
				containerRuntime = "docker";
				isDocker = true;
			}
			args.add(containerRuntime);
			args.add("run");

			String buildJobName = env.get("JOB_NAME").trim();
			buildJobName = buildJobName.replaceAll("\\s+", "");
			String buildUrl = env.get("BUILD_URL");
			String buildNumber = env.get("BUILD_NUMBER");
			args.addTokenized("-e BUILD_JOB_NAME="+buildJobName+" -e BUILD_URL="+buildUrl+" -e BUILD_NUMBER="+buildNumber);
			switch (locationType) {
			case "hosted":
				args.addTokenized(runOptions);

				args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan",
					"--host", apiURL, "--registry", registry,
						hostedImage);				

				if (hideBase) {
					args.add("--hide-base");
				}
				if (register) {
					args.add("--register");
				}
				break;
			case "local":
				if(!"".equals(scannerPath) && !isDocker) {
					args.add("-v", scannerPath+":/aquasec/scannercli:Z", "--entrypoint=/aquasec/scannercli");
				}
				args.addTokenized(runOptions);
				if(isDocker){
					args.add("--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", aquaScannerImage, "scan", "--host", apiURL, "--local", localImage);	
				} else {
					args.add("--rm", "-u", "root", localImage, "scan", "--host", apiURL);
				}	
				
				if (register) {
					args.add("--registry", registry);
					args.add("--register");
				}
				break;
			case "dockerarchive":
				args.addTokenized(runOptions);
				
				// extract file name from path for scan tagging
				Path path = Paths.get(tarFilePath);
				Path fileName = path.getFileName();
				if (fileName == null)
					throw new AbortException("can not extract the file name \n");
				String imgName = fileName.toString().split("\\.")[0];

				args.add("--rm", "-v", tarFilePath+":"+tarFilePath, aquaScannerImage, "scan", imgName+":tar", "--host", apiURL, "--docker-archive", tarFilePath);
				
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

			if (localTokenSecret != null && !Secret.toString(localTokenSecret).equals("")){
				listener.getLogger().println("Received local token, will override global auth");
				args.add("--token");
				args.addMasked(localTokenSecret);
			}else{
				// Authentication, local token is priority
				if(!Secret.toString(token).equals("")) {
					listener.getLogger().println("Received global token");
					args.add("--token");
					args.addMasked(token);
				} else {
					listener.getLogger().println("Received global username password auth");
					args.add("--user", user, "--password");
					args.addMasked(password);
				}
			}
			if(customFlags != null && !customFlags.equals("")) {				
				args.addTokenized(customFlags);
			}

			args.add("--html");

			
			if (!isDocker){
				args.add("--image-name", localImage);
				args.add("--fs-scan", "/");
			}

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
			File cssFile;
			FilePath targetCss = new FilePath(workspace, "styles.css");
			if(Jenkins.get().getPluginManager().getWorkDir() != null)
				cssFile = new File(Jenkins.get().getPluginManager().getWorkDir() + "/aqua-security-scanner/css/", "styles.css");
			else
				cssFile = new File(env.get("JENKINS_HOME") + "/plugins/aqua-security-scanner/css/", "styles.css");
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

		if (htmlEnd+1 < scanOutput.length()){
			listener.getLogger().println(scanOutput.substring(htmlEnd+1, scanOutput.length()));
		}
		if (htmlStart < htmlEnd){
			scanOutput = scanOutput.substring(htmlStart,htmlEnd);
		} 
		
		String scanRegex = "(?m)\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01]).*";
		scanOutput = scanOutput.replaceAll(scanRegex, "");
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
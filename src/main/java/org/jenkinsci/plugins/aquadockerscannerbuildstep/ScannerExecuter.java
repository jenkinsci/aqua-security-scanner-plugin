package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder ;
import java.io.File;
import java.io.PrintStream;

/**
 * This class does the actual execution..
 *
 * @author Moshe Cohen
 */
public class ScannerExecuter {
    
    private static final String SCANNER_DOCKER_IMAGE = "scalock/scanner-cli";

    public static int execute(AbstractBuild build,
			      Launcher launcher,
			      BuildListener listener,
			      String apiURL,
			      String user,
			      String password,
			      int timeout,
			      String registry,
			      String image) {
	
	Process p;
	try {
	    String apiUrlEnvVar = "HOST=" + apiURL;
	    String userEnvVar = "USER=" + user;
	    String passwordEnvVar = "PASSWORD=" + password;
	    String timeoutEnvVar;
	    timeoutEnvVar = "SCAN_TIMEOUT=" + timeout;

	    ArgumentListBuilder args = new ArgumentListBuilder();
	    args.add("docker", "run",  "--rm", "-e", userEnvVar, "-e", passwordEnvVar, "-e", apiUrlEnvVar);
	    if (timeout > 0) {  // 0 means use default
		args.add("-e", timeoutEnvVar);
	    }
	    args.add(SCANNER_DOCKER_IMAGE, registry, image);

	    File outFile = new File(build.getRootDir(), "out");
	    Launcher.ProcStarter ps = launcher.launch();
	    ps.cmds(args);
	    ps.stdin(null);
	    ps.stderr(listener.getLogger());
	    ps.stdout(new PrintStream(outFile, "UTF-8")); 
	    boolean[] masks = new boolean[ps.cmds().size()];
	    masks[6] = true;  // Mask out password
	    ps.masks(masks);
	    int exitCode = ps.join();  // RUN !

	    // Copy local file to workspace FilePath object (which might be on remote machine)
	    FilePath workspace = build.getWorkspace();
	    FilePath target = new FilePath(workspace, "scanout.txt");
	    FilePath outFilePath = new FilePath(outFile);
	    outFilePath.copyTo(target);   

	    return exitCode;

	} catch (RuntimeException e) {
	    listener.getLogger().println("RuntimeException:" + e.toString());
	    return -1;
	} catch (Exception e) {
	    listener.getLogger().println("Exception:" + e.toString());
	    return -1;
	}
    }
}

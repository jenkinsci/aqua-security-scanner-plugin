package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
//import hudson.model.BuildListener;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import hudson.util.Secret;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Paths;



/**
 * This is the builder class.
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked.
 *
 * @author Oran Moshai
 */
public class AquaDockerScannerBuilder extends Builder implements SimpleBuildStep{

	public static final int OK_CODE = 0;
	public static final int DISALLOWED_CODE = 4;
	private final String locationType;
	private final String registry;
	private final boolean register;
	private final String localImage;
	private final String hostedImage;
	private final String onDisallowed;
	private final String notCompliesCmd;
	private final boolean hideBase;
	private final boolean showNegligible;
	private final String policies;
	private final String customFlags;
	private String runtimeDirectory;

	@CheckForNull
	private String containerRuntime;

	@CheckForNull
	private String scannerPath;

	@CheckForNull
	private String tarFilePath;

	@CheckForNull
	private String localToken;

	private Secret localTokenSecret;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public AquaDockerScannerBuilder(String locationType, String registry, boolean register, String localImage, String hostedImage,
			String onDisallowed, String notCompliesCmd,  boolean hideBase, boolean showNegligible, String policies, String localToken,
			String customFlags,	String tarFilePath, String containerRuntime, String scannerPath, String runtimeDirectory) {
		this.locationType = locationType;
		this.registry = registry;
		this.register = register;
		this.localImage = localImage;
		this.hostedImage = hostedImage;
		this.onDisallowed = onDisallowed;
		this.notCompliesCmd = notCompliesCmd;
		this.hideBase = hideBase;
		this.showNegligible = showNegligible;
		this.policies = policies;
		this.localToken = localToken;
		this.customFlags = customFlags;
		this.tarFilePath = tarFilePath;
		this.containerRuntime = containerRuntime;
		this.scannerPath = scannerPath;
		this.localTokenSecret = hudson.util.Secret.fromString(localToken);
		this.runtimeDirectory = runtimeDirectory;
	}

	/**
	 * Public access required by config.jelly to display current values in
	 * configuration screen.
	 */
	public String getLocationType() {
		return locationType;
	}

	@CheckForNull
	public String getContainerRuntime() {
		return containerRuntime;
	}

	@CheckForNull
	public String getScannerPath() {
		return scannerPath;
	}

	public String getRegistry() {
		return registry;
	}

	public boolean getRegister() {
		return register;
	}

	public String getLocalImage() {
		return localImage;
	}

	public String getHostedImage() {
		return hostedImage;
	}

	public String getOnDisallowed() {
		return onDisallowed;
	}

	public String getNotCompliesCmd() {
		return notCompliesCmd;
	}

	public String getPolicies() {
		return policies;
	}

	@CheckForNull
	public String getLocalToken() {
		return localToken;
	}

	public String getCustomFlags() {
		return customFlags;
	}

	public boolean getHideBase() {
		return hideBase;
	}

	public boolean getShowNegligible() {
		return showNegligible;
	}

	public String getRuntimeDirectory() {
		return runtimeDirectory;
	}

	@CheckForNull
	public String getTarFilePath() { return tarFilePath; }

	// Returns the 'checked' state of the radio button for the step GUI
	public String isLocationType(String type) {
		if (this.locationType == null) {
			// default for new step GUI
			return "local".equals(type) ? "true" : "false";
		} else {
			return this.locationType.equals(type) ? "true" : "false";
		}
	}

	// Returns the 'checked' state of the radio button for the step GUI
	public String isOnDisallowed(String state) {
		if (this.onDisallowed == null) {
			// default for new step GUI
			return "ignore".equals(state) ? "true" : "false";
		} else {
			return this.onDisallowed.equals(state) ? "true" : "false";
		}
	}

	@DataBoundSetter
	public void setContainerRuntime(@CheckForNull String containerRuntime) {
		this.containerRuntime = Util.fixNull(containerRuntime, "docker");
	}

	@DataBoundSetter
	public void setScannerPath(@CheckForNull String scannerPath) {
		this.scannerPath = Util.fixNull(scannerPath);
	}

	@DataBoundSetter
	public void setTarFilePath(@CheckForNull String tarFilePath) {
		this.tarFilePath = Util.fixNull(tarFilePath);
	}

	@DataBoundSetter
	public void setLocalToken(@CheckForNull String localToken) {
		this.localToken = Util.fixNull(localToken);
	}

	@DataBoundSetter
	public void setRuntimeDirectory(@CheckForNull String runtimeDirectory) {
		this.runtimeDirectory = Util.fixNull(runtimeDirectory);
	}

	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws AbortException, java.lang.InterruptedException {
		// This is where you 'build' the project.

		String aquaScannerImage = getDescriptor().getAquaScannerImage();
		String apiURL = getDescriptor().getApiURL();
		String user = getDescriptor().getUser();
		Secret password = getDescriptor().getPassword();
		Secret token = getDescriptor().getToken();

		// If user and password is empty, check if token is provided as global or local value
		if(("").equals(user) && Secret.toString(password).equals("") && 
			Secret.toString(token).equals("") && Secret.toString(localTokenSecret).equals("")){
				throw new AbortException("Either Username/Password or Token should be provided in Global Settings, or"+
				" valid token provided with in Token field in the build configuration");
			
		}

		int timeout = getDescriptor().getTimeout();
		String runOptions = getDescriptor().getRunOptions();
		boolean caCertificates = getDescriptor().getCaCertificates();

		boolean userAuth = (user == null || user.trim().equals("") || password == null
		|| Secret.toString(password).trim().equals(""));
		boolean tokenAuth = (token == null || Secret.toString(token).trim().equals(""));
		
		if (apiURL == null || apiURL.trim().equals("") || (userAuth && tokenAuth) ) {
			throw new AbortException("Missing configuration. Please set the global configuration parameters in The \"Aqua Security\" section under  \"Manage Jenkins/Configure System\", before continuing.\n");
		}

		// Allow API urls without the protocol part, add the "https://" in this case
		if (apiURL.indexOf("://") == -1) {
			apiURL = "https://" + apiURL;
		}

		// Support unique names for artifacts when there are multiple steps in the same build
		String artifactSuffix = UUID.randomUUID().toString().replaceAll("-", "");
		String artifactName = "scanout-" + artifactSuffix + ".html";;
		String displayImageName = "";
		
		switch (locationType) {
			case "hosted":
				displayImageName = hostedImage;
				break;
			case "local":
				displayImageName = localImage;
				break;
			case "dockerarchive":
				// extract file name from path for scan tagging
				Path path = Paths.get(tarFilePath);
				Path fileName = path.getFileName();
				if (fileName == null)
					throw new AbortException("can not extract the file name \n");
				displayImageName = fileName.toString().split("\\.")[0];
				break;
			default:
				displayImageName = "";	
		}	
		
		int exitCode = ScannerExecuter.execute(build, workspace,launcher, listener, artifactName, aquaScannerImage, apiURL, user,
				password, token, timeout, runOptions, locationType, localImage, registry, register, hostedImage, hideBase,
				showNegligible, onDisallowed == null || !onDisallowed.equals("fail"), notCompliesCmd, caCertificates,
				policies, localTokenSecret, customFlags, tarFilePath, containerRuntime, scannerPath, runtimeDirectory);
		build.addAction(new AquaScannerAction(build, artifactSuffix, artifactName, displayImageName));

		archiveArtifacts(build, workspace, launcher, listener);

		listener.getLogger().println("exitCode: " + exitCode);
		String failedMessage = "Scanning failed.";
		switch (exitCode) {
		case OK_CODE:
				listener.getLogger().println("Scanning success.");
				break;
		case DISALLOWED_CODE:
				throw new AbortException(failedMessage);
		default:
			// This exception causes the message to appear in the Jenkins console
			throw new AbortException(failedMessage);
		}
	}

	// Archive scanout artifact
	@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // No idea why this is needed
	private void archiveArtifacts(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws java.lang.InterruptedException {
		try {
			ArtifactArchiver artifactArchiver = new ArtifactArchiver("scanout*");
			artifactArchiver.perform(build, workspace, launcher, listener);
		} catch (Exception e) {
			throw new InterruptedException(
					"Failed to setup build results due to an unexpected error. Please refer to above logs for more information");
		}
		try {
			ArtifactArchiver styleArtifactArchiver = new ArtifactArchiver("styles.css");
			styleArtifactArchiver.perform(build, workspace, launcher, listener);
		} catch (Exception e) {
			throw new InterruptedException(
					"Failed to setup build results due to an unexpected error. Please refer to above logs for more information");
		}
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link AquaDockerScannerBuilder}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */
	@Symbol("aqua")
	@Extension // This indicates to Jenkins that this is an implementation of an extension
				// point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a field and
		 * call save().
		 */
		private String aquaScannerImage;
		private Secret apiURL;
		private String authval;
		private Secret user;
		private Secret password;
		private Secret token;
		private int timeout;
		private String runOptions;
		private boolean caCertificates;

		/**
		 * In order to load the persisted global configuration, you have to call load()
		 * in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckTimeout(@QueryParameter String value) throws IOException, ServletException {
			try {
				Integer.parseInt(value);
				return FormValidation.ok();
			} catch (NumberFormatException e) {
				return FormValidation.error("Must be a number");
			}
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Aqua Security";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().

			// boolean flagNoAuthSet = false;
			aquaScannerImage = formData.getString("aquaScannerImage");
			apiURL = Secret.fromString(formData.getString("apiURL"));
			JSONObject authForm = formData.getJSONObject("auth");
			authval = authForm.getString("value") ;

			try{		
				if (authval.equals("token")){
					token = Secret.fromString(authForm.getString("token"));
					user = Secret.fromString("");
					password = Secret.fromString("");
				}else{
					user = Secret.fromString(authForm.getString("user"));
					password = Secret.fromString(authForm.getString("password"));
					token = Secret.fromString("");
				}
				
			}catch (net.sf.json.JSONException te){
				throw new FormException("Either Username/PWD or token must be set. Error is "+ te.getMessage(), "auth");
			}
			
			try {
				timeout = formData.getInt("timeout");
			} catch (net.sf.json.JSONException e) {
				throw new FormException("Timeout value must be a number.", "timeout");
			}
			runOptions = formData.getString("runOptions");
			caCertificates = formData.getBoolean("caCertificates");
			save();
			return super.configure(req, formData);
		}

		public String getAquaScannerImage() {
			return aquaScannerImage;
		}

		public String getApiURL() {
			return Secret.toString(apiURL);
		}

		public String getAuthVal() {
			return authval;
		}

		public String getUser() {
			return Secret.toString(user);
		}

		public Secret getPassword() {
			return password;
		}

		public Secret getToken() {
			return token;
		}

		public int getTimeout() {
			return timeout;
		}

		public String getRunOptions() {
			return runOptions;
		}

		public String isAuthType(String auth) {
			Logger.getLogger("").log(Level.INFO, "Checking auth type:" + auth);
			Logger.getLogger("").log(Level.INFO, "Saved auth type:" + getAuthVal());
			if (getAuthVal() == null) {
				// default auth type for any customer is username/pwd
				return "uname".equals(auth) ? "true" : "false";
			} else {
				return getAuthVal().equals(auth) ? "true" : "false";
			}
		}

		public boolean getCaCertificates() {
			return caCertificates;
		}
	}
}
package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
//import hudson.model.BuildListener;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import hudson.util.Secret;


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

	private static int count;
	private static int buildId = 0;

	public synchronized static void setCount(int count) {
		AquaDockerScannerBuilder.count = count;
	}

	public synchronized static void setBuildId(int buildId) {
		AquaDockerScannerBuilder.buildId = buildId;
	}

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public AquaDockerScannerBuilder(String locationType, String registry, boolean register, String localImage, String hostedImage,
			String onDisallowed, String notCompliesCmd,  boolean hideBase, boolean showNegligible, String policies, String customFlags) {
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
		this.customFlags = customFlags;
	}

	/**
	 * Public access required by config.jelly to display current values in
	 * configuration screen.
	 */
	public String getLocationType() {
		return locationType;
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

	public String getVersion() {
		return getDescriptor().getVersion();
	}

	public String getNotCompliesCmd() {
		return notCompliesCmd;
	}

	public String getPolicies() {
		return policies;
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


	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws AbortException, java.lang.InterruptedException {
		// This is where you 'build' the project.

		String aquaScannerImage = getDescriptor().getAquaScannerImage();
		String apiURL = getDescriptor().getApiURL();
		String user = getDescriptor().getUser();
		Secret password = getDescriptor().getPassword();
		String version = getDescriptor().getVersion();
		int timeout = getDescriptor().getTimeout();
		String runOptions = getDescriptor().getRunOptions();
		boolean caCertificates = getDescriptor().getCaCertificates();
		if (apiURL == null || apiURL.trim().equals("") || user == null || user.trim().equals("") || password == null
				|| Secret.toString(password).trim().equals("")) {
				throw new AbortException("Missing configuration. Please set the global configuration parameters in The \"Aqua Security\" section under  \"Manage Jenkins/Configure System\", before continuing.\n");
		}

		// Allow API urls without the protocol part, add the "https://" in this case
		if (apiURL.indexOf("://") == -1) {
			apiURL = "https://" + apiURL;
		}

		// Support unique names for artifacts when there are multiple steps in the same
		// build
		String artifactSuffix, artifactName;
		String randomFileString = UUID.randomUUID().toString().replaceAll("-", "");
		if (build.hashCode() != buildId) {
			// New build
			setBuildId(build.hashCode());
			setCount(1);
			artifactSuffix = null; // When there is only one step, there should be no suffix at all
			artifactName = String.format("scanout-%s.html", randomFileString);
		} else {
			setCount(count + 1);
			artifactSuffix = Integer.toString(count);
			artifactName = String.format("scanout-%s-%s.html", artifactSuffix, randomFileString);
		}

		int exitCode = ScannerExecuter.execute(build, workspace,launcher, listener, artifactName, aquaScannerImage, apiURL, user,
				password, version, timeout, runOptions, locationType, localImage, registry, register, hostedImage, hideBase,
				showNegligible, onDisallowed == null || !onDisallowed.equals("fail"), notCompliesCmd, caCertificates, policies, customFlags);
		build.addAction(new AquaScannerAction(build, artifactSuffix, artifactName));

		archiveArtifacts(build, workspace, launcher, listener);

		System.out.println("exitCode: " + exitCode);
		String failedMessage = "Scanning failed.";
		switch (exitCode) {
		case OK_CODE:
				System.out.println("Scanning success.");
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
		ArtifactArchiver artifactArchiver = new ArtifactArchiver("scanout*");
		artifactArchiver.perform(build, workspace, launcher, listener);
		ArtifactArchiver styleArtifactArchiver = new ArtifactArchiver("styles.css");
		styleArtifactArchiver.perform(build, workspace, launcher, listener);
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
		private Secret user;
		private Secret password;
		private String version;
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
			aquaScannerImage = formData.getString("aquaScannerImage");
			apiURL = Secret.fromString(formData.getString("apiURL"));
			user = Secret.fromString(formData.getString("user"));
			password = Secret.fromString(formData.getString("password"));
			version = formData.getString("version");
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

		public String getUser() {
			return Secret.toString(user);
		}

		public Secret getPassword() {
			return password;
		}

		public String getVersion() {
			return version;
		}

		public int getTimeout() {
			return timeout;
		}

		public String getRunOptions() {
			return runOptions;
		}

		public String isVersion(String ver) {
			System.out.println("Checking version:" + ver);
			System.out.println("Saved version:" + getVersion());
			if (getVersion() == null) {
				// default for new step GUI
				return "3.x".equals(ver) ? "true" : "false";
			} else {
				return getVersion().equals(ver) ? "true" : "false";
			}
		}
		public boolean getCaCertificates() {
			return caCertificates;
		}
	}
}

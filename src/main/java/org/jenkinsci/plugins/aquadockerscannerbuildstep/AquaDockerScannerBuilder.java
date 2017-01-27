package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.model.BuildListener;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * This is the builder class.
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Moshe Cohen
 */
public class AquaDockerScannerBuilder extends Builder {

    public static final int OK_CODE = 0;
    public static final int DISALLOWED_CODE = 4;
    private final String locationType;
    private final String registry;
    private final String localImage;
    private final String hostedImage;
    private final String onDisallowed;
    private final String notCompliesCmd;
    private final boolean hideBase;
    private final boolean showNegligible;

    private static int count;
    private static int buildId = 0;

    public synchronized static void setCount(int count) {
	AquaDockerScannerBuilder.count = count;
    }

    public synchronized static void setBuildId(int buildId) {
	AquaDockerScannerBuilder.buildId = buildId;
    }

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public AquaDockerScannerBuilder(String locationType,
				    String registry,
				    String localImage,
				    String hostedImage,
				    String onDisallowed,
				    String notCompliesCmd,
				    boolean hideBase,
				    boolean showNegligible) {
	this.locationType = locationType;
        this.registry = registry;
        this.localImage = localImage;
        this.hostedImage = hostedImage;
        this.onDisallowed = onDisallowed;
        this.notCompliesCmd = notCompliesCmd;
	this.hideBase = hideBase;
	this.showNegligible = showNegligible;
    }

    /**
     * Public access required by config.jelly to display current values in configuration screen.
     */
    public String getLocationType() {
        return locationType;
    }
    public String getRegistry() {
        return registry;
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
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
	throws AbortException, java.lang.InterruptedException {
	// This is where you 'build' the project.

	String aquaScannerImage = getDescriptor().getAquaScannerImage();
	String apiURL = getDescriptor().getApiURL();
	String user = getDescriptor().getUser();
	String password = getDescriptor().getPassword();
	int timeout = getDescriptor().getTimeout();

	// Allow API urls without the protocol part, add the "https://" in this case
	if (apiURL.indexOf("://") == -1) {
	    apiURL = "https://" + apiURL;
	}
	
	// Support unique names for artifacts when there are multiple steps in the same build
	String artifactSuffix, artifactName;
	if (build.hashCode() != buildId ) {
	    // New build
	    setBuildId(build.hashCode());
	    setCount(1);
	    artifactSuffix = null; // When ther is only one step, there should be no suffix at all
	    artifactName = "scanout.html";
	} else {
	    setCount(count + 1);
	    artifactSuffix = Integer.toString(count);
	    artifactName = "scanout-" + artifactSuffix + ".html";
	}

	int exitCode = ScannerExecuter.execute(build, launcher, listener, artifactName,
					       aquaScannerImage, apiURL, user, password, timeout,
					       locationType, localImage, registry, hostedImage,
					       hideBase, showNegligible,
					       onDisallowed == null || ! onDisallowed.equals("fail"),
					       notCompliesCmd);
	build.addAction(new AquaScannerAction(build, artifactSuffix, artifactName));

	archiveArtifacts(build, launcher, listener);

	switch (exitCode) {
	case OK_CODE:
	    return true;
	case DISALLOWED_CODE:
	    return false;
	default:
	    // This exception causes the message to appear in the Jenkins console
	    throw new AbortException("Scanning failed.");
	}
    }

    // Archive all artifacts
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // No idea why this is needed
    private void archiveArtifacts(AbstractBuild build, Launcher launcher, BuildListener listener) 
	throws java.lang.InterruptedException {
	ArtifactArchiver artifactArchiver = new ArtifactArchiver("*");
	artifactArchiver.perform(build, build.getWorkspace(), launcher, listener);
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link AquaDockerScannerBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         */
        private String aquaScannerImage = "aquasec/scanner-cli:2.0"; // Default value
        private String apiURL;
        private String user;
        private String password;
	private int timeout;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckTimeout(@QueryParameter String value)
	    throws IOException, ServletException {
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
            apiURL = formData.getString("apiURL");
            user = formData.getString("user");
            password = formData.getString("password");
	    try {
		timeout = formData.getInt("timeout");
	    } catch (net.sf.json.JSONException e) {
		throw new FormException("Timeout value must be a number.", "timeout");
	    }	
            save();
	    return super.configure(req, formData);
        }

        public String getAquaScannerImage() {
            return aquaScannerImage;
        }
        public String getApiURL() {
            return apiURL;
        }
        public String getUser() {
            return user;
        }
        public String getPassword() {
            return password;
        }
        public int getTimeout() {
            return timeout;
        }
    }
}


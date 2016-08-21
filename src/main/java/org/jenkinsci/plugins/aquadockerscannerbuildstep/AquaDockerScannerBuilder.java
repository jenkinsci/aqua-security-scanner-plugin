package org.jenkinsci.plugins.aquadockerscannerbuildstep;

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

    private static final int OK_CODE = 0;
    private static final int DISALLOWED_CODE = 4;
    private final String locationType;
    private final String registry;
    private final String localImage;
    private final String hostedImage;
    private final String onDisallowed;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public AquaDockerScannerBuilder(String locationType, String registry, String localImage, String hostedImage, String onDisallowed) {
	this.locationType = locationType;
        this.registry = registry;
        this.localImage = localImage;
        this.hostedImage = hostedImage;
        this.onDisallowed = onDisallowed;
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

    // Returns the 'checked' state of the radio button got the GUI in the config screen
    public String isLocationType(String type) {
        return this.locationType.equals(type) ? "true" : "false";
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
	
	int exitCode = ScannerExecuter.execute(build, launcher, listener,
					       aquaScannerImage, apiURL, user, password, timeout,
					       locationType, localImage, registry, hostedImage,
					       ! onDisallowed.equals("fail"));
	build.addAction(new AquaScannerAction(build));

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
    private void archiveArtifacts(AbstractBuild build, Launcher launcher, BuildListener listener) 
	throws java.lang.InterruptedException {
	ArtifactArchiver artifactArchiver = new ArtifactArchiver("*");
	assert build != null; // Make Findbugs happy
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
        private String aquaScannerImage = "aquasec/scanner-cli:1.2"; // Default value
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


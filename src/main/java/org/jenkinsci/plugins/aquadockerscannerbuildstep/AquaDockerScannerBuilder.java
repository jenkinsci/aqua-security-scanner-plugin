package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.AbortException;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.model.BuildListener;
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

    private final String registry;
    private final String image;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public AquaDockerScannerBuilder(String registry, String image) {
        this.registry = registry;
        this.image = image;
    }

    /**
     * Public access required by config.jelly to display current values in configuration screen.
     */
    public String getRegistry() {
        return registry;
    }
    public String getImage() {
        return image;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
	throws AbortException {
	// This is where you 'build' the project.

	String apiURL = getDescriptor().getApiURL();
	String user = getDescriptor().getUser();
	String password = getDescriptor().getPassword();
	int timeout = getDescriptor().getTimeout();

	// Allow API urls without the protocol part, add the "https://" in this case
	if (apiURL.indexOf("://") == -1) {
	    apiURL = "https://" + apiURL;
	}
	
	boolean success = ScannerExecuter.execute(build, launcher, listener,
						  apiURL, user, password, timeout,
						  registry, image);
	if (!success) {
	    throw new AbortException("Scanning failed.");
	}
	return true;
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
            return "Aqua Docker Scanner";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
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


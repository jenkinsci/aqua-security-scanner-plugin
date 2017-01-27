package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.model.Action;
import hudson.model.AbstractBuild;

public class AquaScannerAction implements Action {

    private String resultsUrl;
    private AbstractBuild<?,?> build;
    private String artifactSuffix;

    public AquaScannerAction(AbstractBuild<?,?> build, String artifactSuffix, String artifactName) {
        this.build = build;
        this.artifactSuffix = artifactSuffix;
        this.resultsUrl = "../artifact/" + artifactName;
    }

    @Override
    public String getIconFileName() {
        // return the path to the icon file
        return "/plugin/aqua-security-scanner/images/aqua.png";
    }

    @Override
    public String getDisplayName() {
        // return the label for your link
	if (artifactSuffix == null) {
	    return "Aqua Security Scanner";
	} else {
	    return "Aqua Security Scanner " + artifactSuffix;
	}
    }

    @Override
    public String getUrlName() {
        // defines the suburl, which is appended to ...jenkins/job/jobname
	if (artifactSuffix == null) {
	    return "aqua-results";
	} else {
	    return "aqua-results-" + artifactSuffix;
	}
    }

    public AbstractBuild<?,?> getBuild() {
        return this.build;
    }

    public String getResultsUrl() {
        return this.resultsUrl;
    }
}

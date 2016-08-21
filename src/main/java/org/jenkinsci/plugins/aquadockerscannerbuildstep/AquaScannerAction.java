package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.model.Action;
import hudson.model.AbstractBuild;

public class AquaScannerAction implements Action {

    private String resultsUrl;
    private AbstractBuild<?,?> build;

    public AquaScannerAction(AbstractBuild<?,?> build) {
        this.build = build;
        this.resultsUrl = "../artifact/scanout.html";
    }

    @Override
    public String getIconFileName() {
        // return the path to the icon file
        return "/plugin/aqua-security-scanner/images/aqua.png";
    }

    @Override
    public String getDisplayName() {
        // return the label for your link
        return "Aqua Security Scanner";
    }

    @Override
    public String getUrlName() {
        // defines the suburl, which is appended to ...jenkins/job/jobname
        return "aqua-results";
    }

    public AbstractBuild<?,?> getBuild() {
        return this.build;
    }

    public String getResultsUrl() {
        return this.resultsUrl;
    }
}

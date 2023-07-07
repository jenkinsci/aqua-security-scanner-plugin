package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.model.Action;
import hudson.model.Run;

public class AquaScannerAction implements Action {

    private String resultsUrl;
    private Run<?, ?> build;
    private String artifactSuffix;
    private String displayName;

    public AquaScannerAction(Run<?, ?> build, String artifactSuffix, String artifactName, String displayName) {
        this.build = build;
        this.artifactSuffix = artifactSuffix;
        this.resultsUrl = "../artifact/" + artifactName;
        this.displayName = displayName;
    }

    @Override
    public String getIconFileName() {
        // return the path to the icon file
        return "/plugin/aqua-security-scanner/images/aqua.png";
    }

    @Override
    public String getDisplayName() {
        // return the label for your link
        return "Aqua Scan - " + displayName;
    }

    @Override
    public String getUrlName() {
        // defines the suburl, which is appended to ...jenkins/job/jobname
        return "aqua-results-" + artifactSuffix;
    }

    public Run<?, ?> getBuild() {
        return this.build;
    }

    public String getResultsUrl() {
        return this.resultsUrl;
    }
}
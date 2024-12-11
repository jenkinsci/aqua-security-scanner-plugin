package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class AquaScannerRegistryLogin {
    private Launcher launcher;
    private TaskListener listener;
    private int loginAttempts;

    public AquaScannerRegistryLogin(Launcher launcher, TaskListener listener) {
        this.launcher = launcher;
        this.listener = listener;
    }


    public boolean checkAndPerformRegistryLogin(String containerRuntime, String imageName, String username, Secret password) {
        Launcher.ProcStarter ps = launcher.launch();
        ArgumentListBuilder args = new ArgumentListBuilder();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        args.add(containerRuntime != null && containerRuntime.equals("podman") ? "podman" : "docker");
        args.add("inspect", "--format='{{.RepoTags}}'", imageName);
        ps.cmds(args).stdin(null).stderr(outputStream).stdout(outputStream);
        try {
            int exitCode = ps.join(); // RUN !
            String result = outputStream.toString(StandardCharsets.UTF_8.name()).trim();

            if (exitCode == 0 && !result.isEmpty()) {
                // result is non-empty and exitCode is 0 indicates that image inspect worked fine, so should return loginStatus:true
                return true;
            } else if (!username.isEmpty()) {
                // non-empty username indicates that credentials are configured for scanner-registry at jenkins-system-config.
                // action: perform docker-login with credentials and return as loginStatus.
                return registryLogin(containerRuntime, getRegistryName(imageName), username, password, 1);
            } else {
                // If none of above conditions match, return loginStatus: true which will not block existing behaviour
                // As backward compatibility, we should allow this case.
                return true;
            }
        } catch (Exception e) {
            listener.getLogger().println(e.toString());
            return false;
        }
    }

    private boolean registryLogin(String containerRuntime, String registryName, String userName, Secret password, int retries) {
        loginAttempts += 1;
        if (loginAttempts > retries) {
            return false;
        }
        Launcher.ProcStarter ps = launcher.launch();
        ArgumentListBuilder args = new ArgumentListBuilder();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        args.add(containerRuntime != null && containerRuntime.equals("podman") ? "podman" : "docker");
        args.add("login", registryName);
        args.add("-u").addMasked(userName);
        args.add("--password-stdin");
        ps.cmds(args).stdin(new ByteArrayInputStream((password.getPlainText() + "\n").getBytes(StandardCharsets.UTF_8)))
                        .stderr(outputStream).stdout(outputStream);
        try {
            int exitCode = ps.join(); // RUN !
            String result = outputStream.toString(StandardCharsets.UTF_8.name()).trim();
            if (exitCode != 0) {
                listener.getLogger().println("Authentication failed: incorrect credentials provided. Please provide valid registry credentials.");
                listener.getLogger().println(result);
                return false;
            } else {
                listener.getLogger().println("Authenticated with registry successfully.");
                return true;
            }
        } catch (Exception e) {
            listener.getLogger().println("Failed registry login: " + e.toString());
            return registryLogin(containerRuntime, registryName, userName, password, retries);
        }
    }

    private static String getRegistryName(String imageName) {
        // Check if the image name contains a registry (identified by the presence of '/')
        if (imageName.contains("/")) {
            String[] parts = imageName.split("/", 2); // Split the image name into registry/repository
            // If the first part contains a dot (.) or a colon (:), it's a registry name
            if (parts[0].contains(".") || parts[0].contains(":")) {
                return parts[0];
            }
        }
        return "docker.io";
    }
}

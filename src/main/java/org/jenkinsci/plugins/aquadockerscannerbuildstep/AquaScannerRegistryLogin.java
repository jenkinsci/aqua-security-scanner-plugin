package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AquaScannerRegistryLogin {
    private Launcher launcher;
    private TaskListener listener;
    private int retryAttempts;

    public AquaScannerRegistryLogin(Launcher launcher, TaskListener listener) {
        this.launcher = launcher;
        this.listener = listener;
    }


    public boolean checkAndPerformRegistryLogin(String containerRuntime, String imageName, String username, Secret password) {
        String runtime = resolveContainerRuntime(containerRuntime);
        if (imageExists(runtime, imageName)) {
            return true;
        } else if (!username.isEmpty()) {
            // non-empty username indicates that credentials are configured for scanner-registry at jenkins-system-config.
            // action: perform docker-login with credentials and return as loginStatus.
            return registryLogin(runtime, getRegistryName(imageName), username, password, 1);
        } else {
            // If none of above conditions match, return loginStatus: true which will not block existing behaviour
            // As backward compatibility, we should allow this case.
            return true;
        }
    }

    private boolean registryLogin(String containerRuntime, String registryName, String userName, Secret password, int maxRetries) {
        if (retryAttempts > maxRetries) {
            return false;
        }
        Launcher.ProcStarter ps = launcher.launch();
        ArgumentListBuilder args = new ArgumentListBuilder();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        args.add(containerRuntime, "login", registryName);
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
            listener.getLogger().println("Registry login failed: " + e.getMessage());
            retryAttempts += 1;
            return registryLogin(containerRuntime, registryName, userName, password, maxRetries);
        }
    }

    private static final Pattern SHARegexp = Pattern.compile("^(?:([^/]+)/)([^@]+)(@sha256:[0-9a-f]+)$");
    private static final Pattern SplitImageNameRegexp = Pattern.compile("^(?:([^/]+)/)?([^:]+)(?::(.*))?$");
    private static final Pattern PortRegexp = Pattern.compile(":\\d+$");

    public String getRegistryName(String imageName) {
        String registry = "";

        if (imageName == null || imageName.isEmpty()) {
            return registry;
        }

        Matcher shaMatcher = SHARegexp.matcher(imageName);
        if (shaMatcher.matches() && shaMatcher.groupCount() == 3) {
            registry = shaMatcher.group(1);
        } else {
            Matcher nameMatcher = SplitImageNameRegexp.matcher(imageName);
            if (nameMatcher.matches() && nameMatcher.groupCount() >= 3) {
                registry = nameMatcher.group(1);
            }
        }

        // DNS/IP validation of registry
        if (!registry.isEmpty() && !PortRegexp.matcher(registry).matches()) {
            try {
                InetAddress.getByName(registry);
            } catch (UnknownHostException e) {
                listener.getLogger().println("Warning: unable to validate DNS/IP of registry: " + registry);
            }
        }

        return registry;
    }

    private boolean imageExists(String containerRuntime, String imageName) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(containerRuntime, "inspect", "--format={{.RepoTags}}", imageName);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int exitCode = launcher.launch()
                    .cmds(args)
                    .stdin(null)
                    .stderr(outputStream)
                    .stdout(outputStream)
                    .join();

            // result is non-empty and exitCode is 0 indicates that image inspect worked fine, so should return loginStatus:true
            return exitCode == 0 && !outputStream.toString(StandardCharsets.UTF_8.name()).trim().isEmpty();
        } catch (Exception e) {
            listener.getLogger().println("Error checking image existence: " + e.getMessage());
            return false;
        }
    }

    private String resolveContainerRuntime(String containerRuntime) {
        return "podman".equals(containerRuntime) ? "podman" : "docker";
    }
}

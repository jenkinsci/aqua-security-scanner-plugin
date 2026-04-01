package org.jenkinsci.plugins.aquadockerscannerbuildstep;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class CasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("casc.yaml")
    public void shouldImportTokenViaEmptyUser() {
        // When user is empty, password holds the token value.
        // The routing to the --token flag happens at build time in perform().
        AquaDockerScannerBuilder.DescriptorImpl descriptor =
                Jenkins.get().getDescriptorByType(AquaDockerScannerBuilder.DescriptorImpl.class);

        assertEquals("registry.example.com/aquasec/scanner:latest", descriptor.getAquaScannerImage());
        assertEquals("https://aqua.example.com", descriptor.getApiURL());
        assertEquals("", descriptor.getUser());
        assertEquals("test-token-123", descriptor.getPassword().getPlainText());
        assertEquals(600, descriptor.getTimeout());
        assertEquals("--net=host", descriptor.getRunOptions());
        assertTrue(descriptor.getCaCertificates());
        assertEquals("pipelineAuth", descriptor.getRegistryAuthType());
        assertEquals("reg-user", descriptor.getRegistryUsername());
        assertEquals("reg-pass", descriptor.getRegistryPassword().getPlainText());
    }

    @Test
    @ConfiguredWithCode("casc-userpass.yaml")
    public void shouldImportUserPasswordAuth() {
        AquaDockerScannerBuilder.DescriptorImpl descriptor =
                Jenkins.get().getDescriptorByType(AquaDockerScannerBuilder.DescriptorImpl.class);

        assertEquals("myuser", descriptor.getUser());
        assertEquals("mypassword", descriptor.getPassword().getPlainText());
    }

    @Test
    @ConfiguredWithCode("casc.yaml")
    public void shouldExportConfiguration() throws Exception {
        AquaDockerScannerBuilder.DescriptorImpl descriptor =
                Jenkins.get().getDescriptorByType(AquaDockerScannerBuilder.DescriptorImpl.class);
        assertNotNull(descriptor);
        assertEquals("registry.example.com/aquasec/scanner:latest", descriptor.getAquaScannerImage());
    }
}

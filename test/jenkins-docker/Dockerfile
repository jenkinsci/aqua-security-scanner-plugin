# Stage 1: Build the plugin
# Build with JDK 11 — the parent POM's maven-license-plugin:1.8 is incompatible with JDK 17
FROM maven:3.9-eclipse-temurin-11 AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Jenkins with the plugin installed
FROM jenkins/jenkins:lts-jdk21

# Skip the initial setup wizard
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Install plugins (including CasC to test compatibility later)
COPY test/jenkins-docker/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Pre-install the Aqua plugin from the build stage
COPY --from=builder /build/target/aqua-security-scanner.hpi /usr/share/jenkins/ref/plugins/aqua-security-scanner.hpi

# CasC configuration
COPY test/jenkins-docker/jenkins.yaml /var/jenkins_home/jenkins.yaml
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/jenkins.yaml

USER root
# Docker CLI so the plugin can invoke docker commands
RUN apt-get update && apt-get install -y docker.io && rm -rf /var/lib/apt/lists/*
# Add jenkins user to docker group
RUN usermod -aG docker jenkins
USER jenkins

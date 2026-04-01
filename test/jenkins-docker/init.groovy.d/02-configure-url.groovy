import jenkins.model.*

def config = JenkinsLocationConfiguration.get()
config.setUrl("http://localhost:8080/")
config.save()

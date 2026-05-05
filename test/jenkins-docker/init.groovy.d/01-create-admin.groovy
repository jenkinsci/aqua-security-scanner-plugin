import jenkins.model.*
import hudson.security.*

def env = System.getenv()
def adminId = env['JENKINS_ADMIN_ID'] ?: 'admin'
def adminPassword = env['JENKINS_ADMIN_PASSWORD'] ?: 'admin'

def instance = Jenkins.getInstance()
def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(adminId, adminPassword)
instance.setSecurityRealm(hudsonRealm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)
instance.save()

// Constants
def pluggableGitURL = "https://github.com/Accenture/adop-pluggable-scm"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def setupPluggable = freeStyleJob(platformManagementFolderName + "/Setup_Pluggable_Library")
 
// Setup setup_cartridge
setupPluggable.with{
    environmentVariables {
        keepBuildVariables(true)
        keepSystemVariables(true)
    }
    wrappers {
        preBuildCleanup()
    }
    steps {
        shell('''#!/bin/bash -ex

echo "Extracting Pluggable library to additonal classpath location: ${PLUGGABLE_SCM_PROVIDER_PATH}"
cp -r src/main/groovy/pluggable/ ${PLUGGABLE_SCM_PROVIDER_PATH}
echo "******************"

echo "Library contents: "
ls ${PLUGGABLE_SCM_PROVIDER_PATH}pluggable/scm/
''')
        systemGroovy {
            source {
                stringSystemScriptSource {
                    script {
                        script('''
import hudson.scm.SCM
import jenkins.model.Jenkins
import jenkins.plugins.git.GitSCMSource
import org.jenkinsci.plugins.workflow.libs.*
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever

def globalLibrariesParameters = [
  branch:               "master",
  credentialId:         "",
  implicit:             false,
  name:                 "adop-pluggable-scm-jenkinsfile",
  repository:           "https://github.com/Accenture/adop-pluggable-scm-jenkinsfile.git"
]

GitSCMSource gitSCMSource = new GitSCMSource(
  "global-shared-library",
  globalLibrariesParameters.repository,
  globalLibrariesParameters.credentialId,
  "*",
  "",
  false
)

SCMSourceRetriever sCMSourceRetriever = new SCMSourceRetriever(gitSCMSource)

Jenkins jenkins = Jenkins.getInstance()

def globalLibraries = jenkins.getDescriptor("org.jenkinsci.plugins.workflow.libs.GlobalLibraries")

LibraryConfiguration libraryConfiguration = new LibraryConfiguration(globalLibrariesParameters.name, sCMSourceRetriever)
libraryConfiguration.setDefaultVersion(globalLibrariesParameters.branch)
libraryConfiguration.setImplicit(globalLibrariesParameters.implicit)

globalLibraries.get().setLibraries([libraryConfiguration])

jenkins.save()
''')
                        sandbox(true)
                    }
                }
            }
        }
    }
    scm {
        git {
            remote {
                name("origin")
                url("${pluggableGitURL}")
            }
            branch("*/master")
        }
    }
} 

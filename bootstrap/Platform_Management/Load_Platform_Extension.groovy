// Constants
def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def loadPlatformExtensionJob = freeStyleJob(platformManagementFolderName + "/Load_Platform_Extension")
 
// Setup setup_cartridge
loadPlatformExtensionJob.with{
    wrappers {
        preBuildCleanup()
        sshAgent('adop-jenkins-master')
    }
    parameters{
      stringParam("GIT_URL",'https://github.com/Accenture/sample-platform-extension.git',"The URL of the git repo for Platform Extension.")
      stringParam("GIT_REF","master","The reference to checkout from git repo of Platform Extension. It could be a branch name or a tag name. Eg : master, 0.0.1 etc")
    }
    scm{
      git{
        remote{
          url('${GIT_URL}')
        }
        branch('${GIT_REF}')
      }
    }
    steps {
        shell('''#!/bin/bash -ex
		|echo "This job loads the platform extension ${GIT_URL}"'''.stripMargin())
    }
} 

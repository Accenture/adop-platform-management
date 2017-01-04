// Constants
def pluggableGitURL = "https://github.com/Accenture/adop-pluggable-scm"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def setupPluggable = freeStyleJob(platformManagementFolderName + "/Setup_Pluggable_Library")
 
// Setup setup_cartridge
setupPluggable.with{
    environmentVariables {
        keepBuildVariables()
        keepSystemVariables()
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

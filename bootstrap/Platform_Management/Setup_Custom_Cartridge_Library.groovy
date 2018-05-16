// Constants
def libraryGitURL = "https://github.com/Accenture/adop-dsl-custom-cartridge-properties.git"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def setupCustomCartridge = freeStyleJob(platformManagementFolderName + "/Setup_Custom_Cartridge_Library")
 
// Setup setup_cartridge
setupCustomCartridge.with{
    environmentVariables {
        keepBuildVariables(true)
        keepSystemVariables(true)
    }
    wrappers {
        preBuildCleanup()
    }
    steps {
        shell('''#!/bin/bash -ex

echo "Extracting Custom_Cartridge library to additonal classpath location: /var/jenkins_home/userContent/job_dsl_additional_classpath/"
cp -r src/main/groovy/* $JENKINS_HOME/userContent/job_dsl_additional_classpath/
echo "******************"

echo "Library contents: "
ls -ltr $JENKINS_HOME/userContent/job_dsl_additional_classpath/
''')
    }
    scm {
        git {
            remote {
                name("origin")
                url("${libraryGitURL}")
            }
            branch("*/master")
        }
    }
} 

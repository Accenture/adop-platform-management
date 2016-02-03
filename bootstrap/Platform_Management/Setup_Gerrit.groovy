// Constants
def platformToolsGitURL = "ssh://jenkins@gerrit:29418/platform-management"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def setupGerritJob = freeStyleJob(platformManagementFolderName + "/Setup_Gerrit")
 
// Setup setup_gerrit
setupGerritJob.with{
    wrappers {
        preBuildCleanup()
        sshAgent('adop-jenkins-master')
        environmentVariables {
            env('DC',"${LDAP_ROOTDN}")
        }
    }
    steps {
        shell('''#!/bin/bash -ex

# Fetch All-Projects 
cd ${WORKSPACE}
git clone ssh://jenkins@gerrit:29418/All-Projects
cd ${WORKSPACE}/All-Projects
git fetch origin refs/meta/config:refs/remotes/origin/meta/config
git checkout meta/config

# Apply changes
cp ${WORKSPACE}/platform-management/gerrit/project.config .

if ! grep -q "$(cat ${WORKSPACE}/platform-management/gerrit/groups)" groups
then
    perl -p -i -e 's/###([^#]+)###/defined $ENV{$1} ? $ENV{$1} : $&/eg' < "${WORKSPACE}/platform-management/gerrit/groups" 2> /dev/null 1> "${WORKSPACE}/platform-management/gerrit/groups.tokenised"
    cat ${WORKSPACE}/platform-management/gerrit/groups.tokenised >> ${WORKSPACE}/All-Projects/groups
else
    echo "Groups already found, skipping"
fi

# Push
if [ $(git status --porcelain | wc -l) -gt 0 ]; then
    git add project.config groups
    git commit -m "Applying ADOP Gerrit ACL"
    git push origin meta/config:meta/config
else
    echo "Nothing to commit"
fi''')
    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitURL}")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
            relativeTargetDir('platform-management')
        }
    }
} 

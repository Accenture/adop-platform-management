// Constants
def platformToolsGitURL = "ssh://jenkins@gerrit:29418/platform-management"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def loadCartridgeJob = freeStyleJob(platformManagementFolderName + "/Load_Cartridge_List")
 
// Setup setup_cartridge
loadCartridgeJob.with{
    wrappers {
        preBuildCleanup()
        sshAgent('adop-jenkins-master')
    }
    steps {
        shell('''#!/bin/bash -ex

# We trust everywhere
echo -e "#!/bin/sh\nexec ssh -o StrictHostKeyChecking=no \"\\\$@\"\n" > ${WORKSPACE}/custom_ssh
chmod +x ${WORKSPACE}/custom_ssh
export GIT_SSH="${WORKSPACE}/custom_ssh"

# Create repositories
mkdir ${WORKSPACE}/tmp
cd ${WORKSPACE}/tmp
while read repo_url; do
    if [ ! -z "${repo_url}" ]; then
        repo_name=$(echo "${repo_url}" | rev | cut -d'/' -f1 | rev | sed 's#.git$##g')
        target_repo_name="cartridges/${repo_name}"

        # Check if the repository already exists or not
        repo_exists=0
        list_of_repos=$(ssh -n -o StrictHostKeyChecking=no -p 29418 gerrit gerrit ls-projects --type code)

        for repo in ${list_of_repos}
        do
            if [ ${repo} = ${target_repo_name} ]; then
                echo "Found: ${repo}"
                repo_exists=1
                break
            fi
        done

        # If not, create it
        if [ ${repo_exists} -eq 0 ]; then
            ssh -n -o StrictHostKeyChecking=no -p 29418 gerrit gerrit create-project --parent "All-Projects" "${target_repo_name}"
        else
            echo "Repository already exists, skipping: ${target_repo_name}"
        fi

        # Populate repository
        git clone ssh://jenkins@gerrit:29418/"${target_repo_name}"
        cd "${repo_name}"
        git remote add source "${repo_url}"
        git fetch source
        git push origin +refs/remotes/source/*:refs/heads/*
        cd -
    fi
done < ${WORKSPACE}/platform-management/cartridges.txt''')
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

// Constants
def gerritBaseUrl = "ssh://jenkins@gerrit:29418"
def cartridgeBaseUrl = gerritBaseUrl + "/cartridges"
def platformToolsGitUrl = gerritBaseUrl + "/platform-management"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"

def projectFolderName = workspaceFolderName + "/${PROJECT_NAME}"
def projectFolder = folder(projectFolderName)

def cartridgeManagementFolderName= projectFolderName + "/Cartridge_Management"
def cartridgeManagementFolder = folder(cartridgeManagementFolderName) { displayName('Cartridge Management') }

// Cartridge List
def cartridge_list = []
readFileFromWorkspace("${WORKSPACE}/cartridges.txt").eachLine { line ->
    cartridge_repo_name = line.tokenize("/").last()
    local_cartridge_url = cartridgeBaseUrl + "/" + cartridge_repo_name
    cartridge_list << local_cartridge_url
}


// Jobs
def loadCartridgeJob = freeStyleJob(cartridgeManagementFolderName + "/Load_Cartridge")
def loadCartridgeCollectionJob = workflowJob(cartridgeManagementFolderName + "/Load_Cartridge_Collection")


// Setup Load_Cartridge
loadCartridgeJob.with{
    parameters{
        choiceParam('CARTRIDGE_CLONE_URL', cartridge_list, 'Cartridge URL to load')
        stringParam('CARTRIDGE_FOLDER', '', 'The folder within the project namespace where your cartridge will be loaded into.')
        stringParam('FOLDER_DISPLAY_NAME', '', 'Display name of the folder where the cartridge is loaded.')
        stringParam('FOLDER_DESCRIPTION', '', 'Description of the folder where the cartridge is loaded.')
        booleanParam('ENABLE_CODE_REVIEW', false, 'Enables Gerrit Code Reviewing for the selected cartridge')
        booleanParam('OVERWRITE_REPOS', false, 'If ticked, existing code repositories (previously loaded by the cartridge) will be overwritten. For first time cartridge runs, this property is redundant and will perform the same behavior regardless.')
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    steps {
        shell('''#!/bin/bash -ex

# We trust everywhere
echo -e "#!/bin/sh\nexec ssh -o StrictHostKeyChecking=no \"\\\$@\"\n" > ${WORKSPACE}/custom_ssh
chmod +x ${WORKSPACE}/custom_ssh
export GIT_SSH="${WORKSPACE}/custom_ssh"

# Clone Cartridge
git clone ${CARTRIDGE_CLONE_URL} cartridge

# Find the cartridge
export CART_HOME=$(dirname $(find -name metadata.cartridge | head -1))

# Check if the user has enabled Gerrit Code reviewing
if [ "$ENABLE_CODE_REVIEW" == true ]; then
    permissions_repo="${PROJECT_NAME}/permissions-with-review"
else
    permissions_repo="${PROJECT_NAME}/permissions"
fi

# Check if folder was specified
if [ -z ${CARTRIDGE_FOLDER} ] ; then
    echo "Folder name not specified..."
    repo_namespace="${PROJECT_NAME}"
else
    echo "Folder name specified, changing project namespace value.."
    repo_namespace="${PROJECT_NAME}/${CARTRIDGE_FOLDER}"
fi

# Create repositories
mkdir ${WORKSPACE}/tmp
cd ${WORKSPACE}/tmp
while read repo_url; do
    if [ ! -z "${repo_url}" ]; then
        repo_name=$(echo "${repo_url}" | rev | cut -d'/' -f1 | rev | sed 's#.git$##g')
        target_repo_name="${repo_namespace}/${repo_name}"
        # Check if the repository already exists or not
        repo_exists=0
        list_of_repos=$(ssh -n -o StrictHostKeyChecking=no -p 29418 jenkins@gerrit gerrit ls-projects --type code)

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
            ssh -n -o StrictHostKeyChecking=no -p 29418 jenkins@gerrit gerrit create-project --parent "${permissions_repo}" "${target_repo_name}"
        else
            echo "Repository already exists, skipping create: ${target_repo_name}"
        fi

        # Populate repository
        git clone ssh://jenkins@gerrit:29418/"${target_repo_name}"
        cd "${repo_name}"
        git remote add source "${repo_url}"
        git fetch source
        if [ "$OVERWRITE_REPOS" == true ]; then
            git push origin +refs/remotes/source/*:refs/heads/*
        else
            set +e
            git push origin refs/remotes/source/*:refs/heads/*
            set -e
        fi
        cd -
    fi
done < ${WORKSPACE}/${CART_HOME}/src/urls.txt

# Provision one-time infrastructure
if [ -d ${WORKSPACE}/${CART_HOME}/infra ]; then
    cd ${WORKSPACE}/${CART_HOME}/infra
    if [ -f provision.sh ]; then
        source provision.sh
    else
        echo "INFO: ${CART_HOME}/infra/provision.sh not found"
    fi
fi

# Generate Jenkins Jobs
if [ -d ${WORKSPACE}/${CART_HOME}/jenkins/jobs ]; then
    cd ${WORKSPACE}/${CART_HOME}/jenkins/jobs
    if [ -f generate.sh ]; then
        source generate.sh
    else
        echo "INFO: ${CART_HOME}/jenkins/jobs/generate.sh not found"
    fi
fi
''')
        systemGroovyCommand('''
import jenkins.model.*
import groovy.io.FileType

def jenkinsInstace = Jenkins.instance
def projectName = build.getEnvironment(listener).get('PROJECT_NAME')
def mcfile = new FileNameFinder().getFileNames(build.getWorkspace().toString(), '**/metadata.cartridge')
def xmlDir = new File(mcfile[0].substring(0, mcfile[0].lastIndexOf(File.separator))  + "/jenkins/jobs/xml")

def fileList = []

xmlDir.eachFileRecurse (FileType.FILES) { file ->
    if(file.name.endsWith('.xml')) {
        fileList << file
    }
}
fileList.each {
	String configPath = it.path
  	File configFile = new File(configPath)
    String configXml = configFile.text
    ByteArrayInputStream xmlStream = new ByteArrayInputStream( configXml.getBytes() )
    String jobName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.'))

    jenkinsInstace.getItem(projectName,jenkinsInstace).createProjectFromXML(jobName, xmlStream)
}
''')
        conditionalSteps {
            condition {
                shell ('''#!/bin/bash

# Checking to see if folder is specified and project name needs to be updated

if [ -z ${CARTRIDGE_FOLDER} ] ; then
    echo "Folder name not specified, moving on..."
    exit 1
else
    echo "Folder name specified, changing project name value.."
    exit 0
fi
                ''')
            }
            runner('RunUnstable')
            steps {
                environmentVariables {
                    env('CARTRIDGE_FOLDER','${CARTRIDGE_FOLDER}')
                    env('WORKSPACE_NAME',workspaceFolderName)
                    env('PROJECT_NAME',projectFolderName + '/${CARTRIDGE_FOLDER}')
                    env('FOLDER_DISPLAY_NAME','${FOLDER_DISPLAY_NAME}')
                    env('FOLDER_DESCRIPTION','${FOLDER_DESCRIPTION}')
                }
                dsl {
                    text('''// Creating folder to house the cartridge...

def cartridgeFolderName = "${PROJECT_NAME}"
def FolderDisplayName = "${FOLDER_DISPLAY_NAME}"
if (FolderDisplayName=="") {
    println "Folder display name not specified, using folder name..."
    FolderDisplayName = "${CARTRIDGE_FOLDER}"
}
def FolderDescription = "${FOLDER_DESCRIPTION}"
println("Creating folder: " + cartridgeFolderName + "...")

def cartridgeFolder = folder(cartridgeFolderName) {
  displayName(FolderDisplayName)
  description(FolderDescription)
}
                    ''')
                }
            }
        }
        dsl {
            external("cartridge/**/jenkins/jobs/dsl/*.groovy")
        }

    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitUrl}")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
}


// Setup Load_Cartridge Collection
loadCartridgeCollectionJob.with{
    parameters{
        stringParam('COLLECTION_URL', '', 'URL to a JSON file defining your cartridge collection.')
    }
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }
    definition {
        cps {
            script('''node {

    sh("wget ${COLLECTION_URL} -O collection.json")

    println "Reading in values from file..."
    cartridges = parseJSON(readFile('collection.json'))

    println(cartridges);
    println "Obtained values locally...";

    cartridgeCount = cartridges.size
    println "Number of cartridges: ${cartridgeCount}"

    def projectWorkspace =  "''' + projectFolderName + '''"
    println "Project workspace: ${projectWorkspace}"

    // For loop iterating over the data map obtained from the provided JSON file
    for (int i = 0; i < cartridgeCount; i++) {
        def cartridge = cartridges.get(i);

        println("Loading cartridge inside folder: " + cartridge.folder)
        println("Cartridge URL: " + cartridge.url)

        build job: projectWorkspace+'/Cartridge_Management/Load_Cartridge', parameters: [[$class: 'StringParameterValue', name: 'CARTRIDGE_FOLDER', value: cartridge.folder], [$class: 'StringParameterValue', name: 'FOLDER_DISPLAY_NAME', value: cartridge.display_name], [$class: 'StringParameterValue', name: 'FOLDER_DESCRIPTION', value: cartridge.desc], [$class: 'StringParameterValue', name: 'CARTRIDGE_CLONE_URL', value: cartridge.url]]
    }

}

@NonCPS
    def parseJSON(text) {
    def slurper = new groovy.json.JsonSlurper();
    Map data = slurper.parseText(text)
    slurper = null

    def cartridges = []
    for ( i = 0 ; i < data.cartridges.size; i++ ) {
        String url = data.cartridges[i].cartridge.url
        String desc = data.cartridges[i].folder.description
        String folder = data.cartridges[i].folder.name
        String display_name = data.cartridges[i].folder.display_name

        cartridges[i] = [
            'url' : url,
            'desc' : desc,
            'folder' : folder,
            'display_name' : display_name
        ]
    }

    data = null

    return cartridges
}
            ''')
            sandbox()
        }
    }
}

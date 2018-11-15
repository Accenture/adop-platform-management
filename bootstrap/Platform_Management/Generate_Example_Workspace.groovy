// Constants
def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

def rootUrl = "${ROOT_URL}"
gitlabRootUrl = rootUrl.replaceAll("jenkins","gitlab")


// Jobs
def generateExampleWorkspaceJob = workflowJob(platformManagementFolderName + "/Generate_Example_Workspace")

generateExampleWorkspaceJob.with{
    parameters{
        stringParam("projectName","ExampleProject","")
        stringParam("projectAdmin","Admin","")
        stringParam("projectDeveloper","Developer","")
        stringParam("projectViewer","Viewer","")
        stringParam("workspaceName","ExampleWorkspace","")
        stringParam("workspaceAdmin","Admin","")
        stringParam("workspaceDeveloper","Developer","")
        stringParam("workspaceViewer","Viewer","")
        stringParam("cartridgeURL","https://github.com/Accenture/adop-cartridge-java.git","")
        stringParam("scmProvider",gitlabRootUrl + " - ssh (adop-gitlab-ssh)","")
    }
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
    definition {
        cps {
            script('''
node {
    // Setup Workspace
    def result
    try {
        result = build job: 'Workspace_Management/Generate_Workspace', parameters: [[$class: 'StringParameterValue', name: 'WORKSPACE_NAME', value: "${workspaceName}"], [$class: 'StringParameterValue', name: 'ADMIN_USERS', value: "${workspaceName}${workspaceAdmin}"], [$class: 'StringParameterValue', name: 'DEVELOPER_USERS', value: "${workspaceName}${workspaceDeveloper}"], [$class: 'StringParameterValue', name: 'VIEWER_USERS', value: "${workspaceName}${workspaceViewer}"]]
        echo "RESULT of Workspace Job:" + result.result
    } catch (Exception err) {
        if (err.toString().contains('FAILURE')){
            echo "RESULT of Workspace Job: Failure. Failing Pipeline due to failure result.."
            sh "exit 1"
        }
        else {
            // Assume UNSTABLE
            echo "RESULT of Workspace Job: UNSTABLE so will continue." + err.toString()
        }
    }

    // Setup Project
    try {
        result = build job: "${workspaceName}/Project_Management/Generate_Project", parameters: [[$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${projectName}"],[$class: 'BooleanParameterValue', name: 'CUSTOM_SCM_NAMESPACE', value: Boolean.valueOf('true')],[$class: 'StringParameterValue', name: 'ADMIN_USERS', value: "${projectName}${projectAdmin}"], [$class: 'StringParameterValue', name: 'DEVELOPER_USERS', value: "${projectName}${projectDeveloper}"], [$class: 'StringParameterValue', name: 'VIEWER_USERS', value: "${projectName}${projectViewer}"]]
        echo "RESULT of Project Job:" + result.result
    } catch (Exception err) {
        if (err.toString().contains('FAILURE')){
            echo "RESULT of Project Job: Failure. Failing Pipeline due to failure result.."
            sh "exit 1"
        }
        else {
            // Assume UNSTABLE
            echo "RESULT of Project Job: UNSTABLE so will continue." + err.toString()
        }
    }

    // Setup Load Cartridge
    try {
        retry(5) {
            result = build job: "${workspaceName}/${projectName}/Cartridge_Management/Load_Cartridge", parameters: [[$class: 'StringParameterValue', name: 'CARTRIDGE_CLONE_URL', value: "${cartridgeURL}"],[$class: 'StringParameterValue', name: 'SCM_NAMESPACE', value: "${workspaceName}"], [$class: 'StringParameterValue', name: 'SCM_PROVIDER', value: "${scmProvider}"], [$class: 'StringParameterValue', name: 'CARTRIDGE_FOLDER', value: "${workspaceName}"]]
            echo "RESULT of Load Cartridge Job:" + result.result
        }
    } catch (Exception err) {
        if (err.toString().contains('FAILURE')){
            echo "RESULT of Load Cartridge: Failure. Failing Pipeline due to failure result.."
            sh "exit 1"
        }
        else {
            // Assume UNSTABLE
            echo "RESULT of Load Cartridge Job: UNSTABLE so will continue." + err.toString()
        }
    }
}
                ''')
sandbox()
        }
    }
}

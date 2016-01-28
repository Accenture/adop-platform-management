// Constants
def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

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
        stringParam("cartridgeURL","ssh://jenkins@gerrit:29418/cartridges/adop-cartridge-java.git","")
    }
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
    definition {
        cps {
            script('''// Setup Workspace
build job: 'Workspace_Management/Generate_Workspace', parameters: [[$class: 'StringParameterValue', name: 'WORKSPACE_NAME', value: "${workspaceName}"], [$class: 'StringParameterValue', name: 'ADMIN_USERS', value: "${workspaceName}${workspaceAdmin}"], [$class: 'StringParameterValue', name: 'DEVELOPER_USERS', value: "${workspaceName}${workspaceDeveloper}"], [$class: 'StringParameterValue', name: 'VIEWER_USERS', value: "${workspaceName}${workspaceViewer}"]]

// Setup Faculty
build job: "${workspaceName}/Project_Management/Generate_Project", parameters: [[$class: 'StringParameterValue', name: 'PROJECT_NAME', value: "${projectName}"], [$class: 'StringParameterValue', name: 'ADMIN_USERS', value: "${projectName}${projectAdmin}"], [$class: 'StringParameterValue', name: 'DEVELOPER_USERS', value: "${projectName}${projectDeveloper}"], [$class: 'StringParameterValue', name: 'VIEWER_USERS', value: "${projectName}${projectViewer}"]]
retry(5) {
    build job: "${workspaceName}/${projectName}/Cartridge_Management/Load_Cartridge", parameters: [[$class: 'StringParameterValue', name: 'CARTRIDGE_CLONE_URL', value: "${cartridgeURL}"]]
}''')
sandbox()
        }
    }
} 

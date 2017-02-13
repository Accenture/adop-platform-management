// Constants

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def jobRunner = workflowJob(platformManagementFolderName + "/Job_Runner")
 
// Setup setup_cartridge
jobRunner.with{
    description("This job is responsible for executing all required jobs under the Platform_Management folder to setup your platform")
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
    definition {
        cps {
            script('''// Load the list of default cartridges
build job: 'Platform_Management/Load_Cartridge_List'

// Setup the Gerrit ACL
build job: 'Platform_Management/Setup_Gerrit'

// Setup the Pluggable Library
build job: 'Platform_Management/Setup_Pluggable_Library'
''')
sandbox()
        }
    }
} 

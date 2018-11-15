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
            script('''// Setup the Pluggable Library
build job: 'Platform_Management/Setup_Pluggable_Library'

// Setup the Custom_Cartridge Library
build job: 'Platform_Management/Setup_Custom_Cartridge_Library'
''')
sandbox()
        }
    }
}

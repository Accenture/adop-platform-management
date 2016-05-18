// Constants
def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def loadPlatformExtensionCollectionJob = workflowJob(platformManagementFolderName + "/Load_Platform_Extension_Collection")


// Setup Load_Cartridge Collection
loadPlatformExtensionCollectionJob.with{
    parameters{
        stringParam('COLLECTION_URL', '', 'URL to a JSON file defining your platform extension collection.')
        credentialsParam("AWS_CREDENTIALS"){
            type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
            description('AWS access key and secret key for your account')
        }
    }
    properties {
        rebuild {
            autoRebuild(false)
            rebuildDisabled(false)
        }
    }
	definition {
        cps {
            script('''node {

    sh("wget ${COLLECTION_URL} -O collection.json")

    println "Reading in values from file..."
    Map data = parseJSON(readFile('collection.json'))

    println(data);
    println "Obtained values locally...";

    extensionCount = data.extensions.size
    println "Number of platform extensions: ${extensionCount}"

    // For loop iterating over the data map obtained from the provided JSON file
    for ( i = 0 ; i < extensionCount ; i++ ) {
        String url = data.extensions[i].url
        println("Platform Extension URL: " + url)
        String desc = data.extensions[i].description
        build job: '/Platform_Management/Load_Platform_Extension', parameters: [[$class: 'StringParameterValue', name: 'GIT_URL', value: url], [$class: 'StringParameterValue', name: 'GIT_REF', value: 'master'], [$class: 'CredentialsParameterValue', name: 'AWS_CREDENTIALS', value: "${AWS_CREDENTIALS}"]]
    }

}

@NonCPS
    def parseJSON(text) {
    def slurper = new groovy.json.JsonSlurper();
    Map data = slurper.parseText(text)
    slurper = null
    return data
}
            ''')
            sandbox()
        }
    }
}
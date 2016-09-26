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
    extensions = parseJSON(readFile('collection.json'))

    println(extensions);
    println "Obtained values locally...";

    extensionCount = extensions.size
    println "Number of platform extensions: ${extensionCount}"

    // For loop iterating over the data map obtained from the provided JSON file
    for (int i = 0; i < extensionCount; i++) {
        def extension = extensions.get(i);
        println("Platform Extension URL: " + extension.url)
        build job: '/Platform_Management/Load_Platform_Extension', parameters: [[$class: 'StringParameterValue', name: 'GIT_URL', value: extension.url], [$class: 'StringParameterValue', name: 'GIT_REF', value: 'master'], [$class: 'CredentialsParameterValue', name: 'AWS_CREDENTIALS', value: "${AWS_CREDENTIALS}"]]
    }

}

@NonCPS
    def parseJSON(text) {
    def slurper = new groovy.json.JsonSlurper();
    Map data = slurper.parseText(text)
    slurper = null

    def extensions = []
    for ( i = 0 ; i < data.extensions.size; i++ ) {
        String url = data.extensions[i].url
        extensions[i] = ['url' : url]
    }

    data = null

    return extensions
}
            ''')
            sandbox()
        }
    }
}

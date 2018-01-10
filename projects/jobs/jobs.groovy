// Constants
def gerritBaseUrl = "ssh://jenkins@gerrit:29418"
def cartridgeBaseUrl = gerritBaseUrl + "/cartridges"
def platformToolsGitUrl = gerritBaseUrl + "/platform-management"

def adopPlatformManagementVersion = (binding.variables.containsKey("ADOP_PLATFORM_MANAGEMENT_VERSION")) ? "${ADOP_PLATFORM_MANAGEMENT_VERSION}".toString() : '';
def adopPlatformManagementVersionRef = '${ADOP_PLATFORM_MANAGEMENT_VERSION}';

if (!adopPlatformManagementVersion.matches("[a-fA-F0-9]{8,40}")) {
  out.println("[WARN] ADOP_PLATFORM_MANAGEMENT_VERSION is set to '" + adopPlatformManagementVersion + "' which is not a valid hash - defaulting to '*/master'")
  adopPlatformManagementVersionRef = '*/master';
}

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"

// Dynamic values
def customScmNamespace = "${CUSTOM_SCM_NAMESPACE}"
String namespaceValue = null
if (customScmNamespace == "true"){
  namespaceValue = '"${SCM_NAMESPACE}"'
} else {
  namespaceValue = 'null'
}

def projectFolderName = workspaceFolderName + "/${PROJECT_NAME}"
def projectFolder = folder(projectFolderName)

def cartridgeManagementFolderName= projectFolderName + "/Cartridge_Management"
def cartridgeManagementFolder = folder(cartridgeManagementFolderName) { displayName('Cartridge Management') }

// Jobs
def loadCartridgeJob = freeStyleJob(cartridgeManagementFolderName + "/Load_Cartridge")
def loadCartridgeCollectionJob = workflowJob(cartridgeManagementFolderName + "/Load_Cartridge_Collection")
// Setup Load_Cartridge
loadCartridgeJob.with{
    parameters{
        extensibleChoiceParameterDefinition {
          name('CARTRIDGE_CLONE_URL')
          choiceListProvider {
            systemGroovyChoiceListProvider {
              groovyScript {
                script('''
import jenkins.model.*

nodes = Jenkins.instance.globalNodeProperties
nodes.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)
envVars = nodes[0].envVars

def URLS = envVars['CARTRIDGE_SOURCES'];

if (URLS == null) {
  println "[ERROR] CARTRIDGE_SOURCES Jenkins environment variable has not been set";
  return ['Type the cartridge URL (or add CARTRIDGE_SOURCES as a Jenkins environment variable if you wish to see a list here)'];
}
if (URLS.length() < 11) {
  println "[ERROR] CARTRIDGE_SOURCES Jenkins environment variable does not seem to contain valid URLs";
  return ['Type the cartridge URL (the CARTRIDGE_SOURCES Jenkins environment variable does not seem valid)'];
}

def cartridge_urls = [];

URLS.split(';').each{ source_url ->

  try {
    def html = source_url.toURL().text;

    html.eachLine { line ->
      if (line.contains("url:")) {
        def url = line.substring(line.indexOf("\\"") + 1, line.lastIndexOf("\\""))
        cartridge_urls.add(url)
      }
    }
  }
  catch (UnknownHostException e) {
    cartridge_urls.add("[ERROR] Provided URL was not found: ${source_url}");
    println "[ERROR] Provided URL was not found: ${source_url}";
  }
  catch (Exception e) {
    cartridge_urls.add("[ERROR] Unknown error while processing: ${source_url}");
    println "[ERROR] Unknown error while processing: ${source_url}";
  }
}

return cartridge_urls;
''')
                sandbox(true)
              }
              defaultChoice('Top')
              usePredefinedVariables(false)
            }
          }
          editable(true)
          description('Cartridge URL to load')
        }
        // Embedded script to determine available SCM providers
                extensibleChoiceParameterDefinition {
          name('SCM_PROVIDER')
          choiceListProvider {
            systemGroovyChoiceListProvider {
              groovyScript { 
                script('''
import hudson.model.*;
import hudson.util.*;

base_path = "/var/jenkins_home/userContent/datastore/pluggable/scm"

// Initialise folder containing all SCM provider properties files
String PropertiesPath = base_path + "/ScmProviders/"
File folder = new File(PropertiesPath)
def providerList = []

// Loop through all files in properties data store and add to returned list
for (File fileEntry : folder.listFiles()) {
  if (!fileEntry.isDirectory()){
    String title = PropertiesPath +  fileEntry.getName()
    Properties scmProperties = new Properties()
    InputStream input = null
    input = new FileInputStream(title)
    scmProperties.load(input)
    String url = scmProperties.getProperty("scm.url")
    String protocol = scmProperties.getProperty("scm.protocol")
    String id = scmProperties.getProperty("scm.id")
    String output = url + " - " + protocol + " (" + id + ")"
    providerList.add(output)
  }
}

if (providerList.isEmpty()) {
    providerList.add("No SCM providers found")
}
return providerList;
''')
                sandbox(true)
              }
              defaultChoice('Top')
              usePredefinedVariables(false)
            }
          }
          editable(false)
          description('Your chosen SCM Provider and the appropriate cloning protocol')
        }
        if (customScmNamespace == "true"){
          stringParam('SCM_NAMESPACE', '', 'The namespace for your SCM provider which will prefix your created repositories')
        }
        stringParam('CARTRIDGE_FOLDER', '', 'The folder within the project namespace where your cartridge will be loaded into.')
        stringParam('FOLDER_DISPLAY_NAME', '', 'Display name of the folder where the cartridge is loaded.')
        stringParam('FOLDER_DESCRIPTION', '', 'Description of the folder where the cartridge is loaded.')
        textParam('CARTRIDGE_CUSTOM_PROPERTIES', '', 'Custom cartridge properties .e.g sonar.projectKey=adop')
        booleanParam('ENABLE_CODE_REVIEW', false, 'Enables Code Reviewing for the selected cartridge')
        booleanParam('OVERWRITE_REPOS', false, 'If ticked, existing code repositories (previously loaded by the cartridge) will be overwritten. For first time cartridge runs, this property is redundant and will perform the same behavior regardless.')
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
        keepBuildVariables(true)
        keepSystemVariables(true)
        overrideBuildParameters(true)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords {
            injectGlobalPasswords(true)
            maskPasswordParameters(true)
        }
        maskPasswords()
        credentialsBinding {
            file('SCM_SSH_KEY', 'adop-jenkins-private')
        }
        copyToSlaveBuildWrapper {
          includes("**/**")
          excludes("")
          flatten(false)
          includeAntExcludes(false)
          relativeTo('''${JENKINS_HOME}/userContent''')
          hudsonHomeRelative(false)
        }
        envInjectBuildWrapper {
          info {
            secureGroovyScript {
                script("return [SCM_KEY: org.apache.commons.lang.RandomStringUtils.randomAlphanumeric(20)]")
                sandbox(true)
            }
            propertiesFilePath(null)
            propertiesContent(null)
            scriptFilePath(null)
            scriptContent(null)
            loadFilesFromMaster(false)
           }
        }
    }
    label("!master && !windows && !ios")
    steps {
        shell('''#!/bin/bash -ex

mkdir ${WORKSPACE}/tmp

# Output SCM provider ID to a properties file
echo SCM_PROVIDER_ID=$(echo ${SCM_PROVIDER} | cut -d "(" -f2 | cut -d ")" -f1) > ${WORKSPACE}/cartridge.properties

# Check if SCM namespace is specified
if [ -z ${SCM_NAMESPACE} ] ; then
    echo "SCM_NAMESPACE not specified, setting to PROJECT_NAME..."
    if [ -z ${CARTRIDGE_FOLDER} ] ; then
      SCM_NAMESPACE="${PROJECT_NAME}"
    else
      SCM_NAMESPACE="${PROJECT_NAME}"/"${CARTRIDGE_FOLDER}"
    fi
else
    echo "SCM_NAMESPACE specified, injecting into properties file..."
fi

echo SCM_NAMESPACE=$(echo ${SCM_NAMESPACE} | cut -d "(" -f2 | cut -d ")" -f1) >> ${WORKSPACE}/cartridge.properties
''')
        environmentVariables {
            propertiesFile('${WORKSPACE}/cartridge.properties')
        }
        systemGroovyCommand('''
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.*;
import pluggable.scm.PropertiesSCMProviderDataStore;
import pluggable.scm.SCMProviderDataStore;
import pluggable.configuration.EnvVarProperty;
import pluggable.scm.helpers.PropertyUtils;
import java.util.Properties;
import hudson.FilePath;

println "[INFO] - Attempting to inject SCM provider credentials. Note: Not all SCM provider require a username/password combination."

String scmProviderId = build.getEnvironment(listener).get('SCM_PROVIDER_ID');

EnvVarProperty envVarProperty = EnvVarProperty.getInstance();
envVarProperty.setVariableBindings(
  build.getEnvironment(listener));

SCMProviderDataStore scmProviderDataStore = new PropertiesSCMProviderDataStore();
Properties scmProviderProperties = scmProviderDataStore.get(scmProviderId);

String credentialId = scmProviderProperties.get("loader.credentialId");

if(credentialId != null){

  if(credentialId.equals("")){
    println "[WARN] - load.credentialId property provided but is an empty string. SCM providers that require a username/password may not behave as expected.";
    println "[WARN] - Credential secret file not created."
  }else{
    def username_matcher = CredentialsMatchers.withId(credentialId);
    def available_credentials = CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class);

    def credential = CredentialsMatchers.firstOrNull(available_credentials, username_matcher);

    if(credential == null){
      println "[WARN] - Credential with id " + credentialId + " not found."
      println "[WARN] - SCM providers that require a username/password may not behave as expected.";
      println "[WARN] - Credential secret file not created."
    }else{
      credentialInfo = [credential.username, credential.password];

      channel = build.workspace.channel;
      filePath = new FilePath(channel, build.workspace.toString() + "@tmp/secretFiles/" + build.getEnvVars()["SCM_KEY"]);
      filePath.write("SCM_USERNAME="+credentialInfo[0]+"\\nSCM_PASSWORD="+credentialInfo[1], null);

      println "[INFO] - Credentials injected."
    }
  }
}else{
  println "[INFO] - No credential to inject. SCM provider load.credentialId property not found."
}
'''){
  classpath("${PLUGGABLE_SCM_PROVIDER_PATH}")
}
        shell('''#!/bin/bash -ex

# We trust everywhere
echo -e "#!/bin/sh
exec ssh -i ${SCM_SSH_KEY} -o StrictHostKeyChecking=no \"\\\$@\"
" > ${WORKSPACE}/custom_ssh
chmod +x ${WORKSPACE}/custom_ssh
export GIT_SSH="${WORKSPACE}/custom_ssh"

# Clone Cartridge
echo "INFO: cloning ${CARTRIDGE_CLONE_URL}"
# we do not want to show the password
set +x
if ( [ ${CARTRIDGE_CLONE_URL%://*} == "https" ] ||  [ ${CARTRIDGE_CLONE_URL%://*} == "http" ] ) && [ -f ${WORKSPACE}/${SCM_KEY} ]; then
	source ${WORKSPACE}/${SCM_KEY}
	git clone ${CARTRIDGE_CLONE_URL%://*}://${SCM_USERNAME}:${SCM_PASSWORD}@${CARTRIDGE_CLONE_URL#*://} cartridge
else
    git clone ${CARTRIDGE_CLONE_URL} cartridge
fi
set -x

# Find the cartridge
export CART_HOME=$(dirname $(find -name metadata.cartridge | head -1))
echo "CART_HOME=${CART_HOME}" > ${WORKSPACE}/carthome.properties

# Output SCM provider ID to a properties file
echo GIT_SSH="${GIT_SSH}" >> ${WORKSPACE}/scm_provider.properties

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
    environmentVariables {
      propertiesFile('${WORKSPACE}/carthome.properties')
    }
    environmentVariables {
      propertiesFile('${WORKSPACE}/scm_provider.properties')
    }

    systemGroovy {
        source {
            stringSystemScriptSource {
                script {
                script('''
import jenkins.model.*;
import groovy.io.FileType;
import hudson.FilePath;

def jenkinsInstace = Jenkins.instance;
def projectName = build.getEnvironment(listener).get('PROJECT_NAME');
def cartHome = build.getEnvironment(listener).get('CART_HOME');
def workspace = build.workspace.toString();
def cartridgeWorkspace = workspace + '/' + cartHome + '/jenkins/jobs/xml/';
def channel = build.workspace.channel;
FilePath filePath = new FilePath(channel, cartridgeWorkspace);
List<FilePath> xmlFiles = filePath.list('**/*.xml');

xmlFiles.each {
  File configFile = new File(it.toURI());

  String configXml = it.readToString();

  ByteArrayInputStream xmlStream = new ByteArrayInputStream(
    configXml.getBytes());

  String jobName = configFile.getName()
      .substring(0,
                   configFile
                   .getName()
                    .lastIndexOf('.'));

  jenkinsInstace.getItem(projectName,jenkinsInstace)
    .createProjectFromXML(jobName, xmlStream);

  println '[INFO] - Imported XML job config: ' + it.toURI();
}
''')
                    sandbox(true)
                }
            }
        }
    }
  environmentVariables {
      env('PLUGGABLE_SCM_PROVIDER_PATH','${WORKSPACE}/job_dsl_additional_classpath/')
      env('PLUGGABLE_SCM_PROVIDER_PROPERTIES_PATH','${WORKSPACE}/datastore/pluggable/scm')
  }
  groovy {
    scriptSource {
        stringScriptSource {
            command('''
import pluggable.scm.SCMProvider;
import pluggable.scm.SCMProviderHandler;
import pluggable.configuration.EnvVarProperty;

EnvVarProperty envVarProperty = EnvVarProperty.getInstance();
envVarProperty.setVariableBindings(System.getenv());

String scmProviderId = envVarProperty.getProperty('SCM_PROVIDER_ID')

SCMProvider scmProvider = SCMProviderHandler.getScmProvider(scmProviderId, System.getenv())

def workspace = envVarProperty.getProperty('WORKSPACE')
def projectFolderName = envVarProperty.getProperty('PROJECT_NAME')
def overwriteRepos = envVarProperty.getProperty('OVERWRITE_REPOS')
def codeReviewEnabled = envVarProperty.getProperty('ENABLE_CODE_REVIEW')

def cartridgeFolder = '';
def scmNamespace = '';

// Checking if the parameters have been set and they exist within the env properties
if (envVarProperty.hasProperty('CARTRIDGE_FOLDER')){
  cartridgeFolder = envVarProperty.getProperty('CARTRIDGE_FOLDER')
}else{
  cartridgeFolder = ''
}
if (envVarProperty.hasProperty('SCM_NAMESPACE')){
  scmNamespace = envVarProperty.getProperty('SCM_NAMESPACE')
}else{
  scmNamespace = ''
}

String repoNamespace = null;

if (scmNamespace != null && !scmNamespace.isEmpty()){
  println("Custom SCM namespace specified...")
  repoNamespace = scmNamespace
} else {
  println("Custom SCM namespace not specified, using default project namespace...")
  if (cartridgeFolder == ""){
    println("Folder name not specified...")
    repoNamespace = projectFolderName
  } else {
    println("Folder name specified, changing project namespace value..")
    repoNamespace = projectFolderName + "/" + cartridgeFolder
  }
}

scmProvider.createScmRepos(workspace, repoNamespace, codeReviewEnabled, overwriteRepos)
''')
                }
            }
            parameters("")
            scriptParameters("")
            properties("")
            javaOpts("")
            groovyName("ADOP Groovy")
            classPath('''${WORKSPACE}/job_dsl_additional_classpath''')
        }
        environmentVariables {
         env('PLUGGABLE_SCM_PROVIDER_PATH','${JENKINS_HOME}/userContent/job_dsl_additional_classpath/')
         env('PLUGGABLE_SCM_PROVIDER_PROPERTIES_PATH','${JENKINS_HOME}/userContent/datastore/pluggable/scm')
         env('CARTRIDGE_FOLDER','${CARTRIDGE_FOLDER}')
         env('WORKSPACE_NAME',workspaceFolderName)
         env('PROJECT_NAME',projectFolderName)
         env('FOLDER_DISPLAY_NAME','${FOLDER_DISPLAY_NAME}')
         env('FOLDER_DESCRIPTION','${FOLDER_DESCRIPTION}')
         propertiesFile('${WORKSPACE}/cartridge.properties')
       }
        conditionalSteps {
            condition {
                shell ('''#!/bin/bash

# Checking to see if folder is specified and project name needs to be updated

if [ -z ${CARTRIDGE_FOLDER} ] ; then
    echo "Folder name not specified, moving on..."
    echo PROJECT_NAME=${PROJECT_NAME} >> cartridge.properties
    exit 1
else
    echo "Folder name specified, changing project name value.."
    echo PROJECT_NAME=${PROJECT_NAME}/${CARTRIDGE_FOLDER} >> cartridge.properties
    exit 0
fi
                ''')
            }
            runner('RunUnstable')
            steps {
                environmentVariables {
                  propertiesFile('${WORKSPACE}/cartridge.properties')
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
       environmentVariables {
         propertiesFile('${WORKSPACE}/cartridge.properties')
       }
        dsl {
            external("cartridge/**/jenkins/jobs/dsl/*.groovy")
            additionalClasspath("job_dsl_additional_classpath")
        }
    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitUrl}")
                credentials("adop-jenkins-master")
            }
            branch(adopPlatformManagementVersionRef)
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

    cartridgeCount = cartridges.size()
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
    for ( i = 0 ; i < data.cartridges.size(); i++ ) {
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
            sandbox(true)
        }
    }
}

// Constants
def platformToolsGitURL = "https://github.com/Accenture/adop-platform-management.git"

def workspaceManagementFolderName= "/Workspace_Management"
def workspaceManagementFolder = folder(workspaceManagementFolderName) { displayName('Workspace Management') }

def adopPlatformManagementVersion = (binding.variables.containsKey("ADOP_PLATFORM_MANAGEMENT_VERSION")) ? "${ADOP_PLATFORM_MANAGEMENT_VERSION}".toString() : '';
def adopPlatformManagementVersionRef = '${ADOP_PLATFORM_MANAGEMENT_VERSION}';

if (!adopPlatformManagementVersion.matches("[a-fA-F0-9]{8,40}")) {
  out.println("[WARN] ADOP_PLATFORM_MANAGEMENT_VERSION is set to '" + adopPlatformManagementVersion + "' which is not a valid hash - defaulting to '*/master'")
  adopPlatformManagementVersionRef = '*/master';
}

// Jobs
def generateWorkspaceJob = freeStyleJob(workspaceManagementFolderName + "/Generate_Workspace")

// Setup generateWorkspaceJob
generateWorkspaceJob.with{
    parameters{
        stringParam("WORKSPACE_NAME","","The name of the project to be generated.")
        stringParam("ADMIN_USERS","","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project.")
        stringParam("DEVELOPER_USERS","","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project.")
        stringParam("VIEWER_USERS","","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project.")
    }
    label("ldap")
    wrappers {
        preBuildCleanup()
        injectPasswords {
            injectGlobalPasswords(true)
            maskPasswordParameters(true)
        }
        maskPasswords()
        environmentVariables {
            env('DC',"${LDAP_ROOTDN}")
            env('OU_GROUPS','ou=groups')
            env('OU_PEOPLE','ou=people')
            env('OUTPUT_FILE','output.ldif')
        }
        credentialsBinding {
            usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
        }
    }
    steps {
        shell('''#!/bin/bash

# Validate Variables
pattern=" |'"
if [[ "${WORKSPACE_NAME}" =~ ${pattern} ]]; then
    echo "WORKSPACE_NAME contains a space, please replace with an underscore - exiting..."
    exit 1
fi''')
        shell('''# LDAP
${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

set +e
${WORKSPACE}/common/ldap/load_ldif.sh -h ldap -u "${LDAP_ADMIN_USER}" -p "${LDAP_ADMIN_PASSWORD}" -b "${DC}" -f "${OUTPUT_FILE}"
set -e

ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')

''')
        dsl {
            external("workspaces/jobs/**/*.groovy")
        }
        systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_admin.groovy')
        systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_developer.groovy')
        systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_viewer.groovy')
    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitURL}")
                credentials("adop-jenkins-master")
            }
            branch(adopPlatformManagementVersionRef)
        }
    }
}

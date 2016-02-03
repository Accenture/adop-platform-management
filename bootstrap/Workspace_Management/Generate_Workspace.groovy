// Constants
def platformToolsGitURL = "ssh://jenkins@gerrit:29418/platform-management"

def workspaceManagementFolderName= "/Workspace_Management"
def workspaceManagementFolder = folder(workspaceManagementFolderName) { displayName('Workspace Management') }

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
        injectPasswords()
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

# Gerrit
for user in $ADMIN_USERS $DEVELOPER_USERS $VIEWER_USERS
do
        username=$(echo ${user} | cut -d'@' -f1)
        ${WORKSPACE}/common/gerrit/create_user.sh -g http://gerrit:8080/gerrit -u "${username}" -p "${username}"
done''')
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
            branch("*/master")
        }
    }
} 

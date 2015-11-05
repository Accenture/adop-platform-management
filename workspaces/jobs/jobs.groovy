// Constants
def platformToolsGitUrl = "ssh://jenkins@gerrit.service.adop.consul:29418/platform-management"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def workspaceFolder = folder(workspaceFolderName)

def projectManagementFolderName= workspaceFolderName + "/Project_Management"
def projectManagementFolder = folder(projectManagementFolderName) { displayName('Project Management') }

// Jobs
def generateProjectJob = freeStyleJob(projectManagementFolderName + "/Generate_Project")

// Setup Generate_Project
generateProjectJob.with{
    parameters{
        stringParam("PROJECT_NAME","","The name of the project to be generated.")
        stringParam("ADMIN_USERS","","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project.")
        stringParam("DEVELOPER_USERS","","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project.")
        stringParam("VIEWER_USERS","","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project.")
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        environmentVariables {
            env('DC','dc=adop,dc=accenture,dc=com')
            env('OU_GROUPS','ou=groups')
            env('OU_PEOPLE','ou=people')
            env('OUTPUT_FILE','output.ldif')
        }
        credentialsBinding {
            usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
        }
    }
    steps {
        shell('''#!/bin/bash -e

# Validate Variables
pattern=" |'"
if [[ "${PROJECT_NAME}" =~ ${pattern} ]]; then
	echo "PROJECT_NAME contains a space, please replace with an underscore - exiting..."
	exit 1
fi''')
        shell('''set -e
# LDAP
${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

scp -o StrictHostKeyChecking=no ${OUTPUT_FILE} ec2-user@ldap.service.adop.consul:${OUTPUT_FILE}
ssh -o StrictHostKeyChecking=no -t -t -y ec2-user@ldap.service.adop.consul "sudo mv ${OUTPUT_FILE} /data/ldap/config/${OUTPUT_FILE};export IP=\\$(hostname --ip-address); sudo docker exec ADOP-LDAP /usr/local/bin/load_ldif.sh -h \\${IP} -u ${LDAP_ADMIN_USER} -p ${LDAP_ADMIN_PASSWORD} -b ${DC} -f /etc/ldap/slapd.d/${OUTPUT_FILE}; sudo rm -f /data/ldap/config/${OUTPUT_FILE}"

ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')

# Gerrit
for user in $ADMIN_USERS $DEVELOPER_USERS $VIEWER_USERS
do
        username=$(echo ${user} | cut -d'@' -f1)
        ssh -o StrictHostKeyChecking=no -t -t -y ec2-user@gerrit.service.adop.consul "sudo docker exec ADOP-Gerrit /var/gerrit/adop_scripts/create_user.sh -u ${username} -p ${username}"
done''')
        shell('''#!/bin/bash -ex
# Gerrit
source ${WORKSPACE}/projects/gerrit/configure.sh''')
        dsl {
            external("projects/jobs/**/*.groovy")
        }
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_admin.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_developer.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_viewer.groovy')
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
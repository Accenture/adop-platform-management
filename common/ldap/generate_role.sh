#!/bin/bash
set -e
set -o pipefail

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -h"
    echo "    ${0} -r <ROLE> -n <NAMESPACE> -d <DC> -g <GROUPS_OU> -p <PEOPLE_OU> -u <USERS> -f <OUTPUT_FILE> -w <WORKING_DIR>" 1>&2
    exit 1
}

log() {
    level=${1}
    message=${2}
    if [ -z "${level}" ]; then echo "Level is empty"; exit 1; fi
    if [ -z "${message}" ]; then echo "Message is empty"; exit 1; fi

    timestamp=$(date '+%Y/%m/%d %H:%M:%S')
    echo "${timestamp}:${level} > ${message}"
}

if (($# == 0)); then
  echo "No parameters found, exiting" >&2
  usage
  exit 1
fi

# Constants
GENERATE_USER_SCRIPT="common/ldap/lib/generate_user.sh"
GENERATE_GROUP_SCRIPT="common/ldap/lib/generate_group.sh"
GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT="common/ldap/lib/generate_group_member_attribute.sh"

# Getopts
while getopts "hr:n:d:g:p:u:f:w:" opt; do
  case $opt in
    r)
      value_role=${OPTARG}
      ;;
    n)
      value_namespace=${OPTARG}
      ;;
    d)
      value_dc=${OPTARG}
      ;;
    g)
      value_groups_ou=${OPTARG}
      ;;
    p)
      value_people_ou=${OPTARG}
      ;;
    u)
      value_users=${OPTARG}
      ;;
    f)
      output_file=${OPTARG}
      ;;
    w)
      working_dir=${OPTARG}
      ;;
    h)
      echo "Tip: This script expects IFS to be set to a space"
      usage
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done

# Check values exist
if [ -z "${value_role}" ] || [ -z "${value_namespace}" ] || [ -z "${value_dc}" ] || [ -z "${value_groups_ou}" ] || [ -z "${value_people_ou}" ] || [ -z "${output_file}" ] || [ -z "${working_dir}" ]; then
    log "ERROR" "Parameter value(s) missing"
    usage
fi

# Built Variables
GENERATE_USER_SCRIPT_PATH="${working_dir}/${GENERATE_USER_SCRIPT}"
GENERATE_GROUP_SCRIPT_PATH="${working_dir}/${GENERATE_GROUP_SCRIPT}"
GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT_PATH="${working_dir}/${GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT}"

# Check Variables
if [ ! -f "${GENERATE_USER_SCRIPT_PATH}" ]; then
    log "ERROR" "Unable to find script: ${GENERATE_USER_SCRIPT_PATH}"
    usage
fi

if [ ! -f "${GENERATE_GROUP_SCRIPT_PATH}" ]; then
    log "ERROR" "Unable to find script: ${GENERATE_GROUP_SCRIPT_PATH}"
    usage
fi

if [ ! -f "${GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT_PATH}" ]; then
    log "ERROR" "Unable to find script: ${GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT_PATH}"
    usage
fi

if [ -z "${value_users}" ]; then
    log "WARN" "Provided list of users is empty"
fi

# Functions
generate() {
    log "INFO" "Generating Role: ${value_role}"
    
    group_name="${value_namespace}.${value_role}"
    tmp_ldif_file="tmp_${value_role}.ldif"
    
    IFS=',' read -ra users_array <<< "${value_users}"
    for user in "${users_array[@]}"; do
        user_cn=$(echo "${user}" | cut -d'@' -f1)
        user_displayname=$(echo ${user_cn} | sed 's#\.# #g' | sed -e "s/\b\(.\)/\u\1/g")
        user_givenname=$(echo "${user_displayname}" | cut -d' ' -f1)
        user_surname=$(echo "${user_displayname}" | rev | cut -d' ' -f1 | rev)
        user_mail="${user}"
        user_uid="${user_cn}"
        user_password="${user_cn}"
    
        "${GENERATE_USER_SCRIPT_PATH}" -c "${user_cn}" -o "${value_people_ou}" -d "${value_dc}" -n "${user_displayname}" -g "${user_givenname}" -s "${user_surname}" -m "${user_mail}" -u "${user_uid}" -p "${user_password}" -f "${output_file}" -w "${working_dir}"
        "${GENERATE_GROUP_MEMBER_ATTRIBUTE_SCRIPT_PATH}" -c "${user_cn}" -o "${value_people_ou}" -d "${value_dc}" -f "${tmp_ldif_file}" -w "${working_dir}"
    done
    
    "${GENERATE_GROUP_SCRIPT_PATH}" -c "${group_name}" -o "${value_groups_ou}" -d "${value_dc}" -f "${output_file}" -w "${working_dir}"
    
    # Add temp file to output file and remove
    if [ -f "${tmp_ldif_file}" ]; then
        cat "${tmp_ldif_file}" >> "${output_file}"
        rm "${tmp_ldif_file}"
    fi
    
    log "INFO" "Finished Generating Role: ${value_role}"
}

# "Main" method
start_epoch=$(date '+%s')
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
log "INFO" "Running Generate Role"
log "INFO" "ROLE = ${value_role}"
log "INFO" "NAMESPACE = ${value_namespace}"
log "INFO" "DC = ${value_dc}"
log "INFO" "PEOPLE_OU = ${value_people_ou}"
log "INFO" "GROUPS_OU = ${value_groups_ou}"
log "INFO" "USERS = ${value_users}"
log "INFO" "OUTPUT_FILE = ${output_file}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
generate
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
end_epoch=$(date '+%s')
script_duration=$((end_epoch - start_epoch))
log "INFO" "Generate Role elapsed script time (in seconds): ${script_duration}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
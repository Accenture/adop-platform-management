#!/bin/bash
set -e
set -o pipefail

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -h"
    echo "    ${0} -c <CN> -o <OU> -d <DC> -n <DISPLAYNAME> -g <GIVENNAME> -s <SURNAME> -m <MAIL> -u <UID> -p <PASSWORD> -f <OUTPUT_FILE> -w <WORKING_DIR>" 1>&2
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
USER_TEMPLATE_FILE="common/ldap/templates/user.ldif"

TOKEN_CN="###VALUE_CN###"
TOKEN_OU="###VALUE_OU###"
TOKEN_DC="###VALUE_DC###"
TOKEN_DISPLAYNAME="###VALUE_DISPLAYNAME###"
TOKEN_GIVENNAME="###VALUE_GIVENNAME###"
TOKEN_MAIL="###VALUE_MAIL###"
TOKEN_SN="###VALUE_SN###"
TOKEN_UID="###VALUE_UID###"
TOKEN_USERPASSWORD="###VALUE_USERPASSWORD###"

# Getopts
while getopts "hc:o:d:n:g:m:s:u:p:f:w:" opt; do
  case $opt in
    c)
      value_cn=${OPTARG}
      ;;
    o)
      value_ou=${OPTARG}
      ;;
    d)
      value_dc=${OPTARG}
      ;;
    n)
      value_displayname=${OPTARG}
      ;;
    g)
      value_givenname=${OPTARG}
      ;;
    m)
      value_mail=${OPTARG}
      ;;
    s)
      value_sn=${OPTARG}
      ;;
    u)
      value_uid=${OPTARG}
      ;;
    p)
      value_userpassword=${OPTARG}
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
if [ -z "${value_cn}" ] || [ -z "${value_ou}" ] || [ -z "${value_dc}" ] || [ -z "${value_displayname}" ] || [ -z "${value_givenname}" ] || [ -z "${value_mail}" ] || [ -z "${value_sn}" ] || [ -z "${value_uid}" ] || [ -z "${value_userpassword}" ] || [ -z "${output_file}" ] || [ -z "${working_dir}" ]; then
    log "ERROR" "Parameter value(s) missing"
    usage
fi

# Built Variables
USER_TEMPLATE_FILE_PATH="${working_dir}/${USER_TEMPLATE_FILE}"

# Check Variables
if [ ! -f "${USER_TEMPLATE_FILE_PATH}" ]; then
    log "ERROR" "Unable to find template file: ${USER_TEMPLATE_FILE_PATH}"
    usage
fi

# Functions
generate() {
    log "INFO" "Starting LDIF Generation"
    
    # Add a newline
    echo "" >> "${output_file}"
    
    cat "${USER_TEMPLATE_FILE_PATH}" \
    | sed "s/${TOKEN_CN}/${value_cn}/g" \
    | sed "s/${TOKEN_OU}/${value_ou}/g" \
    | sed "s/${TOKEN_DC}/${value_dc}/g" \
    | sed "s/${TOKEN_DISPLAYNAME}/${value_displayname}/g" \
    | sed "s/${TOKEN_GIVENNAME}/${value_givenname}/g" \
    | sed "s/${TOKEN_MAIL}/${value_mail}/g" \
    | sed "s/${TOKEN_SN}/${value_sn}/g" \
    | sed "s/${TOKEN_UID}/${value_uid}/g" \
    | sed "s/${TOKEN_USERPASSWORD}/${value_userpassword}/g" \
    >> "${output_file}"

    log "INFO" "Finished LDIF Generation"
}

# "Main" method
start_epoch=$(date '+%s')
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
log "INFO" "Running Generate User"
log "INFO" "CN = ${value_cn}"
log "INFO" "OU = ${value_ou}"
log "INFO" "DC = ${value_dc}"
log "INFO" "DISPLAYNAME = ${value_displayname}"
log "INFO" "GIVENNAME = ${value_givenname}"
log "INFO" "SURNAME = ${value_sn}"
log "INFO" "MAIL = ${value_mail}"
log "INFO" "UID = ${value_uid}"
log "INFO" "PASSWORD = ********"
log "INFO" "OUTPUT_FILE = ${output_file}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
generate
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
end_epoch=$(date '+%s')
script_duration=$((end_epoch - start_epoch))
log "INFO" "Generate User elapsed script time (in seconds): ${script_duration}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
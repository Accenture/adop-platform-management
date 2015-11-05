#!/bin/bash
set -e
set -o pipefail

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -h"
    echo "    ${0} -c <CN> -o <OU> -d <DC> -f <OUTPUT_FILE> -w <WORKING_DIR>" 1>&2
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
GROUP_MEMBER_TEMPLATE_FILE="common/ldap/templates/group_member.ldif"

TOKEN_CN="###VALUE_CN###"
TOKEN_OU="###VALUE_OU###"
TOKEN_DC="###VALUE_DC###"

# Getopts
while getopts "hc:o:d:f:w:" opt; do
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
if [ -z "${value_cn}" ] || [ -z "${value_ou}" ] || [ -z "${value_dc}" ] || [ -z "${output_file}" ] || [ -z "${working_dir}" ]; then
    log "ERROR" "Parameter value(s) missing"
    usage
fi

# Built Variables
GROUP_MEMBER_TEMPLATE_FILE_PATH="${working_dir}/${GROUP_MEMBER_TEMPLATE_FILE}"

# Check Variables
if [ ! -f "${GROUP_MEMBER_TEMPLATE_FILE_PATH}" ]; then
    log "ERROR" "Unable to find template file: ${GROUP_MEMBER_TEMPLATE_FILE_PATH}"
    usage
fi

# Functions
generate() {
    log "INFO" "Starting LDIF Generation"
    cat "${GROUP_MEMBER_TEMPLATE_FILE_PATH}" \
    | sed "s/${TOKEN_CN}/${value_cn}/g" \
    | sed "s/${TOKEN_OU}/${value_ou}/g" \
    | sed "s/${TOKEN_DC}/${value_dc}/g" \
    >> "${output_file}"
    log "INFO" "Finished LDIF Generation"
}

# "Main" method
start_epoch=$(date '+%s')
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
log "INFO" "Running Generate Group Member"
log "INFO" "CN = ${value_cn}"
log "INFO" "OU = ${value_ou}"
log "INFO" "DC = ${value_dc}"
log "INFO" "OUTPUT_FILE = ${output_file}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
generate
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
end_epoch=$(date '+%s')
script_duration=$((end_epoch - start_epoch))
log "INFO" "Generate Group Member elapsed script time (in seconds): ${script_duration}"
echo "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
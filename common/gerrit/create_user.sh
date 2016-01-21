#!/bin/bash
set -e

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -g <GERRIT_URL> -u <USER> -p <PASSWORD>"
    exit 1
}

# Constants
SLEEP_TIME=5
MAX_RETRY=2

while getopts "g:u:p:" opt; do
  case $opt in
    g)
      gerrit_url=${OPTARG}
      ;;
    u)
      username=${OPTARG}
      ;;
    p)
      password=${OPTARG}
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done

if [ -z "${gerrit_url}" ] || [ -z "${username}" ] || [ -z "${password}" ]; then
    echo "Parameters missing"
    usage
fi

echo "Testing Gerrit Connection"
until curl -sL -w "%{http_code}\\n" "${gerrit_url}/login/%23/q/status:open" -o /dev/null | grep "401" &> /dev/null
do
    echo "Gerrit unavailable, sleeping for ${SLEEP_TIME}"
    sleep "${SLEEP_TIME}"
done

echo "Creating user: ${username}"
count=1
until [ $count -ge ${MAX_RETRY} ]
do
  ret=$(curl -X POST --data "username=${username}&password=${password}" --write-out "%{http_code}" --silent --output /dev/null "${gerrit_url}/login/%23/q/status:open")
  [[ ${ret} -eq 302  ]] && break
  count=$[$count+1]
  echo "Unable to create user ${username}, response code ${ret}, retry ... ${count}"
  sleep ${SLEEP_TIME}
done

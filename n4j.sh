#!/bin/sh

GRADLE="./gradlew"
[ -f "${GRADLE}" ] || GRADLE="gradle"

N4J_CLC_IP="${N4J_CLC_IP:-$1}"
N4J_TEST="${2:-AllShortSuite}"
N4J_OPTS="${N4J_OPTS:-}"

if [ -z "${N4J_CLC_IP}" ] ; then
  echo "Usage n4j.sh CLC_IP" >&2
  exit 1
fi

echo "Running test ${N4J_TEST} against ${N4J_CLC_IP}"
sleep 5

"${GRADLE}" --no-daemon \
  -Dtest.filter="${N4J_TEST}" \
  -Dclcip="${N4J_CLC_IP}" \
  ${N4J_OPTS} \
  clean \
  test

if [ ! -z "${N4J_RESULTS}" ] && [ -d "${N4J_RESULTS}" ] ; then
  echo "Copying test ${N4J_TEST} results to ${N4J_RESULTS}"  
  [ ! -d "build/test-results/test" ] || cp -r "build/test-results" "${N4J_RESULTS}/"     
  [ ! -d "build/reports/tests/test" ] || cp -r "build/reports" "${N4J_RESULTS}/"     
  chmod --recursive go+=rwX "${N4J_RESULTS}"/*
fi

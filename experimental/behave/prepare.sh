#!/bin/bash

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   corda-master   => git clone https://github.com/corda/corda
#   r3corda-master => git clone https://github.com/corda/enterprise
VERSION=corda-master
STAGING_DIR=deps/corda/${VERSION}
DRIVERS_DIR=deps/drivers

# Set up directories
mkdir -p ${STAGING_DIR}/apps
mkdir -p ${DRIVERS_DIR}

# Copy Corda capsule into deps
cd ../..
./gradlew clean install
cp -v $(ls node/capsule/build/libs/corda-*.jar | tail -n1) ${STAGING_DIR}/corda.jar

# Copy finance library
cp -v $(ls finance/build/libs/corda-finance-*.jar | tail -n1) ${STAGING_DIR}/apps

# Copy sample Cordapps
./gradlew samples:simm-valuation-demo:jar
cp -v $(ls samples/simm-valuation-demo/build/libs/simm-valuation-demo-*.jar | tail -n1) ${STAGING_DIR}/apps

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > ${DRIVERS_DIR}/h2-1.4.196.jar
curl -L "https://github.com/Microsoft/mssql-jdbc/releases/download/v6.2.2/mssql-jdbc-6.2.2.jre8.jar" > ${DRIVERS_DIR}/mssql-jdbc-6.2.2.jre8.jar
curl -L "http://central.maven.org/maven2/org/postgresql/postgresql/42.1.4/postgresql-42.1.4.jar" > ${DRIVERS_DIR}/postgresql-42.1.4.jar

# Build Network Bootstrapper
./gradlew buildBootstrapperJar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${STAGING_DIR}/network-bootstrapper.jar

# Build rpcProxy (required for by Driver to call Corda 3.0 which continues to use Kryo for RPC)
cd experimental/behave
../../gradlew rpcProxyJar
cp -v $(ls build/libs/corda-rpcProxy*.jar | tail -n1) ${STAGING_DIR}/corda-rpcProxy.jar
rm -rf ${STAGING_DIR}/proxy

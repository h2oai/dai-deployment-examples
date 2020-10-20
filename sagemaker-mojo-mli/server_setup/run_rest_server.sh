#!/usr/bin/env bash

set -o pipefail
set -e

sleep 5 # let http server print boot status to console first
java -Dserver.port=9000 -Dmojo.path=/opt/ml/model/pipeline.mojo -jar local-rest-scorer-*.jar

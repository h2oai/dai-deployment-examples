#!/bin/bash

set -euo pipefail

if (( $# < 1 )); then
   cat << EOS
usage: $0 <dai_python_scorer.zip> [<docker_image> [<version>]]"
  <docker_image> defaults to h2o-dai-python-scorer
  <version> defaults to current date and time

  The created image will also be tagged with <docker_image>:latest .
EOS
   exit 254
fi

#$1 is the python scoring pipeline zip file created by DAI
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
dir_name=$(mktemp -dt scorer_temp)
docker_image=${2:-h2o-dai-python-scorer}
version=${3:-$(date +%FT%H-%M-%SZ)}
tag="${docker_image}:${version}"

echo "building  $tag in $dir_name"
unzip -q "$1" -d "$dir_name"

docker build -t "$docker_image:latest" -t "$tag" -f "$script_dir/scorer.Dockerfile" "$dir_name/scoring-pipeline"

echo 'cleaning up'
rm -rf "$dir_name"

cat << EOS
Build complete.
Run with

docker run -p 9090:9090 --env DRIVERLESS_AI_LICENSE_KEY=<contents of license.sig file>  $tag

EOS

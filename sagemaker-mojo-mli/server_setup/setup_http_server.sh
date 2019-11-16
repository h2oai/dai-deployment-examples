#!/usr/bin/env bash

set -o pipefail
set -ex

if [ ! -d http_server_env ]; then
    echo "Creating virtual environment..."
    virtualenv -p python3.6 http_server_env
    source http_server_env/bin/activate

    python -m pip install --upgrade --upgrade-strategy only-if-needed pip==19.1.1

    echo "Installing dependencies..."
    pip install --upgrade --upgrade-strategy only-if-needed -r requirements.txt

    echo "Installing server dependencies..."
    pip install --upgrade --upgrade-strategy only-if-needed -r http_server_requirements.txt

    deactivate
fi

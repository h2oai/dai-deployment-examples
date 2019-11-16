#!/usr/bin/env bash

set -o pipefail
set -e

http_server_env/bin/python -m pip install /opt/ml/model/scoring_*.whl

source http_server_env/bin/activate
python http_server.py
deactivate

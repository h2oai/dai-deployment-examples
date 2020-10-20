#!/bin/bash
set -x -e


# AWS EMR bootstrap script
# for installing DAIMOJO on AWS EMR with Spark
#
##############################

sudo yum -y groupinstall "Development Tools"
sudo alternatives --set gcc "/usr/bin/gcc48"

sudo pip-3.6 install -U pip
sudo ln -sf /usr/local/bin/pip3.6 /usr/bin/pip-3.6

sudo pip-3.6 install https://s3.amazonaws.com/artifacts.h2o.ai/releases/ai/h2o/daimojo/2.4.5%2Bmaster.29/x86_64-centos7/daimojo-2.4.5%2Bmaster.29-cp36-cp36m-linux_x86_64.whl
sudo pip-3.6 install pandas
sudo pip-3.6 install pyarrow


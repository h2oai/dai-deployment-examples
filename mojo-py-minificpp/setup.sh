#!/bin/bash

##
# Tested on Ubuntu 18.04 EC2 instance with traffic open on all ports on 0.0.0.0/0 with 128GB Disk
# Installs CEM (EFM + NiFi Registry + one MiNiFi C++ Agent on same machine)
# Ref: https://docs.cloudera.com/cem/latest/index.html
#
# Installs Driverless AI MOJO2 Python Runtime API
##

##
# CEM consists of three components:
# Apache MiNiFi C++: is a light-weight edge agent that implements the core features of Apache NiFi, focusing on data collection & processing at the edge
# Edge Flow Manager (EFM): is an agent management hub that supports a graphical flow-based programming model to develop, deploy & monitor edge flows on thousands of MiNiFi agents.
# NiFi Registry: EFM uses NiFi Registry buckets to save the data flows built for MiNiFi Agents
# CEM provides 3 main capabilities to edge flow lifecycle: flow authorship, flow deployment, flow monitoring
##

##
# is_in_activation waits until service starts and is running
# ref: https://stackoverflow.com/questions/21475639/wait-until-service-starts-in-bash-script
##

# set bash to match patterns in a case-insensitive fashion when performing matching with case or [[ conditional commands
shopt -s nocasematch

function is_in_activation {
    if [ $2 == "minifi" ] ; then
        activation=$(/opt/cloudera/cem/$1/bin/$2 status | grep -oi "not currently running" )
        if [[ "$activation" == "not currently running" ]] ; then
            echo "$1 $activation"
            true;
        else
            echo "$1 running"
            false;
        fi

        return $?;
    else
        activation=$(/opt/cloudera/cem/$1/bin/$2 status | grep -oi "not running" )
        if [[ "$activation" == "not running" ]] ; then
            echo "$1 $activation"
            true;
        else
            echo "$1 running"
            false;
        fi

        return $?;
    fi
    
    echo "$1 not running or even running an error occurred"
    false;
    return $?;
}

# Cloudera private repository credentials - needed for CEM's subscription-only URL below
REMOTE_REPO_USR=$1
REMOTE_REPO_PWD=$2

echo "Install CEM - EFM, NiFi Registry and a MiNiFi C++ Agent on a Single Node"
apt -y update
apt -y install unzip
apt -y install openjdk-8-jdk

echo "1: Download the CEM Software: EFM, NiFi Registry, MiNiFi C++ Agent"
# Extract CEM/ from tarball.tar.gz to cem/
mkdir -p /opt/cloudera/cem
wget https://${REMOTE_REPO_USR}:${REMOTE_REPO_PWD}@archive.cloudera.com/p/CEM/ubuntu18/1.x/updates/1.2.1.0/CEM-1.2.1.0-ubuntu18-tars-tarball.tar.gz -P /opt/cloudera/cem
tar xzf /opt/cloudera/cem/CEM-1.2.1.0-ubuntu18-tars-tarball.tar.gz -C /opt/cloudera/cem/

echo "2: Install EFM"
# Extract EFM from CEM/ to cem/
tar xzf /opt/cloudera/cem/CEM/ubuntu18/1.2.1.0-23/tars/efm/efm-1.0.0.1.2.1.0-23-bin.tar.gz -C /opt/cloudera/cem/
ln -s /opt/cloudera/cem/efm-1.0.0.1.2.1.0-23 /opt/cloudera/cem/efm
chown -R root:root /opt/cloudera/cem/efm-1.0.0.1.2.1.0-23

# Install a PostgreSQL Database and configure it with CEM to manage, control and monitor dataflows
# Ref: https://www.postgresql.org/download/linux/ubuntu/

# Create the file repository configuration:
sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
# Import the repository signing key:
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
# Update the package lists and install PostgreSQL 9.6
apt -y update
# install postgresql-9.6, postgresql-contrib-9.6, postgresql-server-dev-9.6
apt -y install postgresql-9.6
apt -y install postgresql-server-dev-9.6

# Proceed to start PostgreSQL since the database system is initialized automatically
systemctl start postgresql.service
systemctl enable postgresql.service

cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/cem/conf/pg_hba.conf /etc/postgresql/9.6/main/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/cem/conf/postgresql.conf /etc/postgresql/9.6/main/

systemctl restart postgresql.service

echo "-- Create Postgres DB for EFM to use"
sudo -u postgres psql << EOF
CREATE DATABASE efm;
CREATE USER efm WITH PASSWORD 'clouderah2oai';
GRANT ALL PRIVILEGES ON DATABASE "efm" TO efm;
EOF

# Configures efm.properties: EFM database properties, EFM Server address, connection to NiFi Registry, connection to database
# Sets the Encryption Password of at least 12 chars for efm.encryption.password property in efm.properties
# this property specifies a master passowrd used for encrypting sensitive data saved to EFM server
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/cem/conf/efm.properties /opt/cloudera/cem/efm/conf
sed -i "s/EFM_SERVER_IP/`hostname -f`/g" /opt/cloudera/cem/efm/conf/efm.properties

# Access UI: http://${EC2_EFM_PUBLIC_DNS}:10080/efm/
/opt/cloudera/cem/efm/bin/efm.sh start

while is_in_activation efm efm.sh ; do true; done

echo "-- Now EFM is started and the next step is to automate installing the NiFi Registry"

echo "3: Install NiFi Registry"
# Extract NiFi Registry from CEM/ to cem/
tar xzf /opt/cloudera/cem/CEM/ubuntu18/1.2.1.0-23/tars/nifi_registry/nifi-registry-0.7.0.1.2.1.0-23-bin.tar.gz -C /opt/cloudera/cem
ln -s /opt/cloudera/cem/nifi-registry-0.7.0.1.2.1.0-23 /opt/cloudera/cem/nifi-registry
# access UI: http://${EC2_EFM_PUBLIC_DNS}:18080/nifi-registry/
/opt/cloudera/cem/nifi-registry/bin/nifi-registry.sh start

while is_in_activation nifi-registry nifi-registry.sh ; do true; done

echo "-- Now NiFi Registry is started and the next step is to automate installing the MiNiFi C++ Agent"

echo "4: Install MiNiFi C++ Agent"
# Extract MiNiFi C++ from bin.tar.gz to cem/
apt install -y libleveldb-dev
wget http://mirror.cogentco.com/pub/apache/nifi/nifi-minifi-cpp/0.7.0/nifi-minifi-cpp-bionic-0.7.0-bin.tar.gz -P /opt/cloudera/cem
tar xzf /opt/cloudera/cem/nifi-minifi-cpp-bionic-0.7.0-bin.tar.gz -C /opt/cloudera/cem
ln -s /opt/cloudera/cem/nifi-minifi-cpp-0.7.0 /opt/cloudera/cem/nifi-minifi-cpp
echo "export MINIFI_HOME=/opt/cloudera/cem/nifi-minifi-cpp-0.7.0/" | sudo tee -a /root/.profile
source /home/ubuntu/.profile
chown -R root:root /opt/cloudera/cem/nifi-minifi-cpp-0.7.0

# Configures minifi.properties: communication to C2 (EFM server), C2 Agent Class, enable socket port 9998, path to py processors
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/cem/conf/minifi-efm.properties /opt/cloudera/cem/nifi-minifi-cpp/conf/minifi.properties
sed -i "s/EFM_SERVER_IP/`hostname -f`/g" /opt/cloudera/cem/nifi-minifi-cpp/conf/minifi.properties

echo "-- Add H2O.ai related custom MiNiFi Python Processors to MiNiFi C++ Agent"
# Add custom MiNiFi Python Processors: ConvertDsToCsv, ExecuteDaiMojoScoringPipeline, ExecuteH2oMojoScoring to MiNiFi C++ Agent
mkdir -p /opt/cloudera/cem/nifi-minifi-cpp/minifi-python/h2o/{h2o3/mojo,dai/msp/}
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/cem/minifi-python/ExecuteDaiMojoScoringPipeline.py /opt/cloudera/cem/nifi-minifi-cpp/minifi-python/h2o/dai/msp/
wget https://raw.githubusercontent.com/apache/nifi-minifi-cpp/main/extensions/pythonprocessors/h2o/ConvertDsToCsv.py -P /opt/cloudera/cem/nifi-minifi-cpp/minifi-python/h2o/
wget https://raw.githubusercontent.com/apache/nifi-minifi-cpp/main/extensions/pythonprocessors/h2o/h2o3/mojo/ExecuteH2oMojoScoring.py -P /opt/cloudera/cem/nifi-minifi-cpp/minifi-python/h2o/h2o3/mojo/

/opt/cloudera/cem/nifi-minifi-cpp/bin/minifi.sh start

while is_in_activation nifi-minifi-cpp minifi.sh ; do true; done

echo "-- Now MiNiFi C++ Agent is started"

echo "4: Install DAI MOJO Scoring Pipeline Requirements for C++ Runtime Python Wrapper API"
# Extract mojo.zip to dai-model-deployment/
unzip /home/ubuntu/mojo.zip -d /home/ubuntu/dai-model-deployment/

# Install package dependencies for MOJO2 Python Runtime using pip for Python3.6
apt -y install python3-pip
curl https://bootstrap.pypa.io/get-pip.py -o /home/ubuntu/get-pip.py
python3.6 /home/ubuntu/get-pip.py
pip install -r /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/mojo/mojo2-py-requirements.txt

# Set up input test batch data that'll be used for batch scoring
cp /home/ubuntu/dai-model-deployment/mojo-pipeline/example.csv /home/ubuntu/dai-model-deployment/testData/test-batch-data/

# Set up input test real-time data that'll be used for real-time scoring
cp /home/ubuntu/dai-model-deployment/mojo-pipeline/example.csv /home/ubuntu/dai-model-deployment/testData/test-real-time-data/
echo -e "$(sed '1d' /home/ubuntu/dai-model-deployment/testData/test-real-time-data/example.csv)\n" > /home/ubuntu/dai-model-deployment/testData/test-real-time-data/example.csv
split -dl 1 --additional-suffix=.csv /home/ubuntu/dai-model-deployment/testData/test-real-time-data/example.csv /home/ubuntu/dai-model-deployment/testData/test-real-time-data/test_
rm -rf /home/ubuntu/dai-model-deployment/testData/test-real-time-data/example.csv

echo "Installed CEM and DAI MOJO Python Runtime API on a Single Node"

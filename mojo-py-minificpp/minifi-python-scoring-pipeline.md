# Execute the Driverless AI Python Scoring Pipeline within MiNiFi Data Flow

## Cloudera Integration Point for CDF

Deploy the Driverless AI Python Scoring Pipeline to Apache MiNiFi C++ by using the Python Runtime API and a custom MiNiFi Python processor. This will be a Cloudera integration point for Cloudera Data Flow (CDF), particularly Cloudera Edge Management (CEM). CEM is powered by one or more Apache MiNiFi C++ agents.

## Video Walkthrough

The following link is a YouTube video that shows how to deploy the Driverless AI Python Scoring Pipeline to MiNiFi C++ to do batch scoring and real-time scoring on Hydraulic System data to classify for Hydraulic Cooling Condition: [MiNiFi Custom Python Processor for Running DAI Py Scorer in MiNiFi Data Flow](https://youtu.be/bmtS7GInaJA)

## Prerequisites

- Driverless AI Environment

- Recommended: Launch Ubuntu 18.04 Linux EC2 instance

- Recommended: Create Anaconda or Miniconda Environment

## Task 1: Set Up Environment

### Connect to EC2 from Local Machine

1\. Move the **EC2 Pivate Key File (Pem Key)** to the .ssh folder

~~~bash
# Move Private Key to .ssh folder
mv $HOME/Downloads/{private-key-filename}.pem $HOME/.ssh/

# Set Private Key permissions to 400 to avoid SSH permission denied
chmod 400 $HOME/.ssh/{private-key-filename}.pem
~~~

2\. Set **EC2 Public DNS** and **EC2 Pem Key** as permanent environment variables

~~~bash
# For Mac OS X, set permanent environment variables 
tee -a $HOME/.bash_profile << EOF
# Set EC2 Public DNS
export H2O_DAI_SCORING_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export H2O_DAI_SCORING_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

# For Linux, set permanent environment variables
tee -a $HOME/.profile << EOF
# Set EC2 Public DNS
export H2O_DAI_SCORING_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export H2O_DAI_SCORING_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

source $HOME/.bash_profile
~~~

3\. Connect to EC2 via SSH

~~~bash
# Connect to EC2 instance using SSH
ssh -i $H2O_DAI_SCORING_PEM ubuntu@$H2O_DAI_SCORING_INSTANCE
~~~

### Create Environment Directory Structure

1\. Run the following commands that will create the directories where you could store the input data and the Driverless AI Python Scorer.

~~~bash
# Create directory structure for DAI Python Scoring Pipeline MiNiFi CPP Projects

# Create directory where the DAI Python Scorer will be stored
mkdir -p /home/ubuntu/{dai-psp-minificpp,dai-mojo-minificpp}

# Create input directory used for batch scoring or real-time scoring
mkdir -p /home/ubuntu/dai-psp-minificpp/testData/{test-batch-data,test-real-time-data}
~~~

### Set Up Driverless AI Python Scoring Pipeline in EC2

1\. Build a **Driverless AI Experiment**

- 1a\. Upload your dataset or use the following **Data Recipe URL** to import the **UCI Hydraulic System Condition Monitoring Dataset**:

~~~bash
# Data Recipe URL
https://raw.githubusercontent.com/james94/driverlessai-recipes/master/data/hydraulic-data.py
~~~

- 1b\. Split the data **75% for training** and **25% for testing**.

- 1c\. Run predict on your **training data**.

- 1d\. Name the experiment **model_deployment**. Choose the **target column** for scoring. Choose the **test data**. Launch the experiment.

2\. Click **Download MOJO Scoring Pipeline** in Driverless AI Experiment Dashboard since we will be using the **example.csv** data that comes with it later.

- 2a\. Select **Python**, click **Download MOJO Scoring Pipeline** and send **mojo.zip** to EC2.

~~~bash
# Move Driverless AI MOJO Scoring Pipeline to EC2 instance
scp -i $H2O_DAI_SCORING_PEM $HOME/Downloads/mojo.zip ubuntu@$H2O_DAI_SCORING_INSTANCE:/home/ubuntu/dai-mojo-minificpp/
~~~

- 2b\. Unzip **mojo.zip**. We will be using the **mojo-pipeline/** folder for it's example.csv data.

~~~bash
cd /home/ubuntu/dai-mojo-minificpp/
unzip mojo.zip
~~~

3\. Click **Download Python Scoring Pipeline** in Driverless AI Experiment Dashboard

- 3a\. Send **scorer.zip** to EC2.

~~~bash
# Move Driverless AI Python Scoring Pipeline to EC2 instance
scp -i $H2O_DAI_SCORING_PEM $HOME/Downloads/scorer.zip ubuntu@$H2O_DAI_SCORING_INSTANCE:/home/ubuntu/dai-psp-minificpp/
~~~

- 3b\. Unzip **scorer.zip**.

~~~bash
cd /home/ubuntu/dai-psp-minificpp/
unzip scorer.zip
~~~

4\. Install **Python Runtime Dependencies** in EC2

- 4a\. Install the required packages:

~~~bash
# Make all packages available on EC2 instance
sudo apt-get -y update

# Install Python 3.6 and related packages
sudo apt-get -y install python3.6 python-virtualenv python3.6-dev python3-pip python3-dev python3-virtualenv

# Install OpenBLAS for linear algebra calculations
sudo apt-get -y install libopenblas-dev

# Install Unzip for access to individual files in scoring pipeline folder
sudo apt-get -y install unzip

# Install Java to include open source H2O-3 algorithms
sudo apt-get -y install openjdk-8-jdk

# Install tree for displaying environment directory structure
sudo apt-get -y install tree

# Install the thrift-compiler
sudo apt-get install automake bison flex g++ git libevent-dev \
  libssl-dev libtool make pkg-config libboost-all-dev ant

wget https://github.com/apache/thrift/archive/0.10.0.tar.gz
tar -xvf 0.10.0.tar.gz
cd thrift-0.10.0
./bootstrap.sh
./configure
make
sudo make install

# Refresh runtime after installing thrift
sudo ldconfig /usr/local/lib
~~~

- 4b\. Create **env** virtual environment and install env requirements

~~~bash
# Install Python 3.6.10
conda create -y -n env python=3.6
conda activate env

# Go to scoring-pipeline/ folder with requirements.txt
cd scoring-pipeline

# Install gitdb2 and gitdb into env
pip install --upgrade gitdb2==2.0.6 gitdb==0.6.4

# Install dependencies
pip install -r requirements.txt
~~~

5\. Set the **Driverless AI License Key** as a **temporary environment variable**

~~~bash
# Set Driverless AI License Key
export DRIVERLESS_AI_LICENSE_KEY="{license-key}"
~~~

### Set Up MiNiFi C++ in EC2

1\. Download **Driverless AI Examples** Repo for MiNiFi assets

~~~bash
cd $HOME
git clone -b mojo-py-minificpp https://github.com/james94/dai-deployment-examples/
~~~

2\. Download **MiNiFi C++**

- 2a\. Install the required packages:

~~~bash
# Make all packages available on EC2 instance
sudo apt-get -y update
# Install dependencies for MiNiFi C++
apt install -y libleveldb-dev
apt install -y libxml2
~~~

- 2b\. Download the latest MiNiFi C++:

~~~bash
# Change to Linux $HOME directory
cd /home/ubuntu/
# Download MiNiFi
wget https://downloads.apache.org/nifi/nifi-minifi-cpp/0.7.0/nifi-minifi-cpp-bionic-0.7.0-bin.tar.gz

# Extract MiNiFi
tar -xvf nifi-minifi-cpp-bionic-0.7.0-bin.tar.gz
~~~

3\. Set **MINIFI_HOME** as a **permanent environment variable**:

~~~bash
# For Linux
echo "export MINIFI_HOME=/home/ubuntu/nifi-minifi-cpp-0.7.0/" | tee -a $HOME/.profile

source $HOME/.profile
~~~

## Task 2: Deploy Python Scoring Pipeline to MiNiFi C++

### Add Custom MiNiFi Python Processors to MiNiFi in the EC2

1\. Create a `modules` folder in `nifi-minificpp-0.7.0` folder.

~~~bash
# go into nifi-minifi-cpp folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/

# create modules folder
mkdir modules
~~~

2\. Verify that the `from scoring_h2oai_experiment_{rest of python filename} import Scorer` statement in the **DaiPythonScorer.py** matches your Driverless AI Python Scorer filename that came with the `scoring-pipeline/` folder you downloaded earlier. If your Python Scorer filename doesn't match, then update **DaiPythonScorer.py** with your filename.

~~~python
#!/usr/bin/env python
# Filename: DaiPythonScorer.py

from scoring_h2oai_experiment_0abac7c4_8993_11ea_ae54_0242ac110002 import Scorer
~~~

3\. Copy over **DaiPythonScorer.py** to `modules/` folder.

~~~bash
# go to modules folder
cd modules

# copy DaiPythonScorer.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/modules/DaiPythonScorer.py .
~~~

**DaiPythonScorer.py** is needed for the MiNiFi C++ Python processors. With this Python file, we import Driverless AI Python Scorer. We don't directly import the Python Scorer in the python processors because the module name changes frequently. So, the user can update the import statement in this file when the module name changes.

4\. Create an `h2o/dai/psp` folder path in `minifi-python` directory:

~~~bash
# go back to nifi-minifi-cpp folder
cd ../

# go into nifi-minifi-cpp minifi-python/ folder
cd minifi-python/

# create h2o/dai/psp folder path
mkdir -p h2o/dai/psp/
~~~

5\. Copy over the custom Python Datatable MiNiFi processor **ConvertDsToCsv.py** to **h2o/** folder.

~~~bash
# go to h2o folder
cd h2o

# copy ConvertDsToCsv.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/ConvertDsToCsv.py .
~~~

6\. Copy over the custom Python Driverless AI Python Scoring Pipeline MiNiFi processor's **ExecuteDaiPspBatchScoring.py** and **ExecuteDaiPspRealTimeScoring.py** to `h2o/dai/psp/` folder.

~~~bash
# go into psp (mojo scoring pipeline) folder
cd dai/psp/

# copy ExecuteDaiPspBatchScoring.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspBatchScoring.py .

# copy ExecuteDaiPspRealTimeScoring.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspRealTimeScoring.py .
~~~

7\. Overwrite **minifi.properties** file to tell MiNiFi where to find the new custom MiNiFi Python processors.

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/

# copy our version of minifi.properties over to conf/ to overwrite the current one
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/conf/minifi.properties .
~~~

### Import the MiNiFi Config Template File into MiNiFi in the EC2

1\. Go to MiNiFi **conf/** directory:

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/
~~~

2\. Replace the current MiNiFi **config.yml** with one of the following MiNiFi config files that use the custom Python Driverless AI Python Scoring Pipeline MiNiFi processor to do batch scoring or interactive scoring:

~~~bash
# A: copy our minifi flow config file for batch scoring to conf/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/conf/config-psp-batch-scoring.yml .
# overwrite the pre-existing config.yml with our config that does batch scoring
mv config-psp-batch-scoring.yml config.yml

# B: copy our minifi flow config file for interactive scoring to conf/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/conf/config-psp-interactive-scoring.yml .
# overwrite the pre-existing config.yml with our config that does interactive scoring
mv config-psp-interactive-scoring.yml config.yml
~~~

3\. Make sure there is **input test data** in the directory MiNiFi will be pulling data from.

- 3a\. If you imported the MiNiFi flow for **batch scoring**, then you should make sure there is one or more files with multiple rows of csv data in the following directory:

~~~bash
# go to mojo-pipeline/ directory with batch data example.csv
cd /home/ubuntu/dai-mojo-minificpp/mojo-pipeline/

# copy this batch data to the input dir where MiNiFi pulls the batch data
cp example.csv /home/ubuntu/dai-psp-minificpp/testData/test-batch-data/
~~~

- 3b\. If you imported the MiNiFi flow for **interactive scoring**, then you should make sure there are files with a single row of csv data in the following directory:

~~~bash
# go to real-time input dir where we will store real-time data
cd /home/ubuntu/dai-psp-minificpp/testData/test-real-time-data/

# copy example.csv to the input dir where MiNiFi pulls the real-time data
cp /home/ubuntu/dai-mojo-minificpp/mojo-pipeline/example.csv .

# remove file's 1st line, the header
echo -e "$(sed '1d' example.csv)\n" > example.csv

# split example.csv into smaller real-time files each with 1 line
split -l 1 example.csv test_

# add .csv extension to all test_* files in folder
for f in test* ; do mv "$f" "${f}.csv"; done

# remove example.csv from real-time input dir
rm -rf example.csv
~~~

### Start the MiNiFi Flow

1\. Start the MiNiFi Flow to do **batch scoring** or **real-time scoring**

~~~bash
# go back to nifi-minifi-cpp base folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/

# start minifi C++
./bin/minifi.sh start

# stop minifi C++
# ./bin/minifi.sh stop
~~~

### Looking at Interactive or Batch Scoring Results

1\. Let's look at the **prediction results** in one of the following directories:

~~~bash
# The output directory where batch predictions are stored
/home/ubuntu/dai-psp-minificpp/predData/pred-batch-data

# The output directory where real-time predictions are stored
/home/ubuntu/dai-psp-minificpp/predData/pred-real-time-data
~~~

### Batch Scoring

1\. If you imported the MiNiFi config file for **batch scoring**, you should see files in the following **pred-batch-data/** directory and each file has batch scored data, such as follows:

![minifi-batch-scoring.jpg](images/minifi-batch-scoring.jpg)

### Interactive Scoring

1\. If you imported the MiNiFi config file for **real-time (interactive) scoring**, you should see files in the following **pred-real-time-data/** directory and each file has real-time scored data, such as follows:

![minifi-real-time-scoring.jpg](images/minifi-real-time-scoring.jpg)

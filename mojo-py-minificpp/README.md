# Deploy Driverless AI MOJO within a MiNiFi C++ Data Flow

## Cloudera Integration Point for CDF

Deploy the Driverless AI MOJO Scoring Pipeline to Apache MiNiFi C++ by using the MOJO2 C++ Runtime Python Wrapper API and a custom MiNiFi Python processor. This is a Cloudera integration point for Cloudera Data Flow (CDF), particularly Cloudera Edge Management (CEM). CEM is powered by one or more Apache MiNiFi C++ agents.

If you are using Driverless AI Python Scoring Pipeline, then read the following document: [Execute the Driverless AI Python Scoring Pipeline within MiNiFi Data Flow](minifi-python-scoring-pipeline.md)

## Video Walkthrough

The following link is a YouTube video that shows how to deploy the Driverless AI MOJO to MiNiFi C++ to do batch scoring and real-time scoring on Hydraulic System data to classify for Hydraulic Cooling Condition: [MiNiFi Custom Processor for Running the MOJO in MiNiFi Data Flow](https://youtu.be/jQYbZ3TrncM)

## Prerequisites

- Driverless AI Environment (Tested with Driverless AI 1.8.7.1, MOJO Scoring Pipeline 2.4.2)

- Launch Ubuntu 18.04 Linux EC2 instance
    - Instance Type: t2.2xlarge
    - Storage: 256GB
    - Open custom TCP port `10080` for `EFM Server HTTP` and source on `0.0.0.0` to listen on all network interfaces
    - Open custom TCP port `8989` for `EFM Server Constraint application protocol (CoAP)` and source on `0.0.0.0` to listen on all network interfaces

## Task 1: Set Up Environment

### Connect to EC2 from Local Machine

1\. Move the **EC2 Pivate Key File (Pem Key)** to the .ssh folder

~~~bash
mv $HOME/Downloads/{private-key-filename}.pem $HOME/.ssh/
chmod 400 $HOME/.ssh/{private-key-filename}.pem
~~~

2\. Set **EC2 Public DNS** and **EC2 Pem Key** as permanent environment variables

~~~bash
# For Mac OS X, set permanent environment variables 
tee -a $HOME/.bash_profile << EOF
# Set EC2 Public DNS
export DAI_MOJO_MINIFI_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export DAI_MOJO_MINIFI_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

# For Linux, set permanent environment variables
tee -a $HOME/.profile << EOF
# Set EC2 Public DNS
export DAI_MOJO_MINIFI_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export DAI_MOJO_MINIFI_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

source $HOME/.bash_profile
~~~

3\. Connect to EC2 via SSH

~~~bash
# Connect to EC2 instance using SSH
ssh -i $DAI_MOJO_MINIFI_PEM ubuntu@$DAI_MOJO_MINIFI_INSTANCE
~~~

### Create Environment Directory Structure

1\. Run the following commands that will create the directories where you could store the input data and the pipeline.mojo.

~~~bash
# Create directory structure for DAI MOJO MiNiFi CPP Projects

mkdir $HOME/daimojo-minificpp/

mkdir -p $HOME/daimojo-minificpp/testData/{test-batch-data,test-real-time-data}
~~~

### Set Up Driverless AI MOJO Scoring Pipeline in EC2

1\. Build a **Driverless AI Experiment**

- 1a\. Upload your dataset or use the following **Data Recipe URL** to import the **UCI Hydraulic System Condition Monitoring Dataset**:

~~~bash
# Data Recipe URL
https://raw.githubusercontent.com/james94/driverlessai-recipes/master/data/hydraulic-data.py
~~~

- 1b\. Split the data **75% for training** and **25% for testing**.

- 1c\. Run predict on your **training data**.

- 1d\. Name the experiment **model_deployment**. Choose the **target column** for scoring. Choose the **test data**. Launch the experiment.

2\. Click **Download MOJO Scoring Pipeline** in Driverless AI Experiment Dashboard

- 2a\. Select **Python**, click **Download MOJO Scoring Pipeline** and send **mojo.zip** to EC2.

~~~bash
# Move Driverless AI MOJO Scoring Pipeline to EC2 instance
scp -i $DAI_MOJO_MINIFI_PEM $HOME/Downloads/mojo.zip ubuntu@$DAI_MOJO_MINIFI_INSTANCE:/home/ubuntu/daimojo-minificpp/
~~~

- 2b\. Unzip **mojo.zip**.

~~~bash
cd /home/ubuntu/daimojo-minificpp/
unzip mojo.zip
~~~

3\. Click **Download MOJO2 Python Runtime** in Driverless AI MOJO Scoring Pipeline Instructions Python tab

- 3a\. Send **daimojo-2.2.0-*.whl** to EC2.

~~~bash
# Move Driverless AI MOJO2 Python Runtime to EC2 instance
scp -i $DAI_MOJO_MINIFI_PEM $HOME/Downloads/daimojo-2.2.0-cp36-cp36m-linux_x86_64.whl ubuntu@$DAI_MOJO_MINIFI_INSTANCE:/home/ubuntu
~~~

4\. Install **MOJO2 Python Runtime Dependencies** in EC2

- 4a\. Download and install Anaconda.

~~~bash
# Download, then install Anaconda
wget https://repo.anaconda.com/archive/Anaconda3-2020.02-Linux-x86_64.sh

bash Anaconda3-2020.02-Linux-x86_64.sh
~~~

- 4b\. Create **model-deployment** virtual environment

~~~bash
conda create -y -n model-deployment python=3.6
conda activate model-deployment
~~~

- 4c\. Install the **required packages**:

~~~bash
# Install datable 0.10.1
pip install datatable
# Install pandas
pip install pandas
# Install scipy
pip install scipy
# Install the MOJO2 Py runtime on Linux x86
pip install /home/ubuntu/daimojo-2.2.0-cp36-cp36m-linux_x86_64.whl
~~~

5\. Set the **Driverless AI License Key** as a **temporary environment variable**

~~~bash
# Set Driverless AI License Key
export DRIVERLESS_AI_LICENSE_KEY="{license-key}"
~~~

### Prepare Hydraulic Test Data For Mojo MiNiFi Scoring

Make sure there is **input test data** in the directory MiNiFi will be pulling data from.

1\. For **batch scoring**, you should make sure there is one or more files with multiple rows of csv data in the following directory:

~~~bash
# go to mojo-pipeline/ directory with batch data example.csv
cd /home/ubuntu/daimojo-minificpp/mojo-pipeline/

# copy this batch data to the input dir where MiNiFi pulls the batch data
cp example.csv /home/ubuntu/daimojo-minificpp/testData/test-batch-data/
~~~

2\. If you imported the MiNiFi flow for **interactive scoring**, then you should make sure there are files with a single row of csv data in the following directory:

~~~bash
# go to real-time input dir where we will store real-time data
cd /home/ubuntu/daimojo-minificpp/testData/test-real-time-data/

# copy example.csv to the input dir where MiNiFi pulls the real-time data
cp /home/ubuntu/daimojo-minificpp/mojo-pipeline/example.csv .

# remove file's 1st line, the header
echo -e "$(sed '1d' example.csv)\n" > example.csv

# split file into multiple files having 1 row of data with numeric suffix and .csv extension
split -dl 1 --additional-suffix=.csv example.csv test_

# remove example.csv from real-time input dir
rm -rf example.csv
~~~

### Set Up MiNiFi C++ in EC2

1\. Download **MiNiFi C++**

- 1a\. Install the required packages:

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

1\. Download **Driverless AI Examples** Repo for MiNiFi assets

~~~bash
cd $HOME
git clone -b mojo-py-minificpp https://github.com/james94/dai-deployment-examples/
~~~

### Add Custom MiNiFi Python Processors to MiNiFi in the EC2

1\. Create **h2o/dai/msp** folders inside **minifi-python**.

~~~bash
# go into nifi-minifi-cpp minifi-python/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/minifi-python/

# create h2o/dai/msp folder path
mkdir -p h2o/dai/msp/
~~~

2\. Copy over the custom Python Datatable MiNiFi processor **ConvertDsToCsv.py** to **h2o/** folder.

~~~bash
# go to h2o folder
cd h2o/

# copy ConvertDsToCsv.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/nifi-minifi-cpp/minifi-python/h2o/ConvertDsToCsv.py .
~~~

3\. Copy over custom Python MOJO MiNiFi processor **ExecuteDaiMojoScoringPipeline.py** to **msp/** folder.

~~~bash
# go into msp (mojo scoring pipeline) folder
cd dai/msp/

# copy ExecuteDaiMojoScoringPipeline.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/nifi-minifi-cpp/minifi-python/h2o/dai/msp/ExecuteDaiMojoScoringPipeline.py .
~~~

4\. Overwrite **minifi.properties** file to tell MiNiFi where to find the new custom MiNiFi Python processors.

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/

# copy our version of minifi.properties over to conf/ to overwrite the current one
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/nifi-minifi-cpp/conf/minifi.properties .
~~~

## Task 2: Deploy MOJO Scoring Pipeline to MiNiFi C++

### Import the MiNiFi Config Template File into MiNiFi in the EC2

1\. Go to MiNiFi **conf/** directory:

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/
~~~

2\. Replace the current MiNiFi **config.yml** with one of the following MiNiFi config files that use the custom Python MOJO MiNiFi processor to execute the MOJO to do batch scoring or interactive scoring:

~~~bash
# A: copy our minifi flow config file for batch scoring to conf/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/nifi-minifi-cpp/conf/config-msp-batch-scoring.yml .
# overwrite the pre-existing config.yml with our config that does batch scoring
mv config-msp-batch-scoring.yml config.yml

# B: copy our minifi flow config file for interactive scoring to conf/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/nifi-minifi-cpp/conf/config-msp-interactive-scoring.yml .
# overwrite the pre-existing config.yml with our config that does interactive scoring
mv config-msp-interactive-scoring.yml config.yml
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
/home/ubuntu/daimojo-minificpp/predData/pred-batch-data

# The output directory where real-time predictions are stored 
/home/ubuntu/daimojo-minificpp/predData/pred-real-time-data
~~~

### Batch Scoring

1\. If you imported the MiNiFi config file for **batch scoring**, you should see files in the following **pred-batch-data/** directory and each file has batch scored data, such as follows:

![minifi-batch-scoring.jpg](images/minifi-batch-scoring.jpg)

### Interactive Scoring

1\. If you imported the MiNiFi config file for **real-time (interactive) scoring**, you should see files in the following **pred-real-time-data/** directory and each file has real-time scored data, such as follows:

![minifi-real-time-scoring.jpg](images/minifi-real-time-scoring.jpg)

# Deploy Driverless AI MOJO within a Flink Data Pipeline

## Cloudera Integration Point for CDF

Deploy the Driverless AI MOJO Scoring Pipeline to Apache Flink by using the MOJO2 Java Runtime API and a custom Flink RichMapFunction. This will be a Cloudera Integration point for Cloudera Data Flow (CDF), particulary Cloudera Streaming Analytics(CSA). CSA is powered by Apache Flink.

If you need to send the MOJO predictions to a Kafka topic, then read the following document: [Send Driverless AI MOJO Predictions to Kafka using FlinkKafkaProducer](./daimojo-flink-kafka.md)

## Video Walkthrough

The following link is a YouTube video that shows how to deploy the Driverless AI MOJO to Flink to do batch and real-time scoring on Hydraulic System data to classify for Hydraulic Cooling Condition: [Flink Custom RichMapFunction for Running the Driverless AI MOJO in Flink Data Pipeline](https://youtu.be/RU6Q4UrhCEs)

## Prerequisites

- Driverless AI Environment (Tested with Driverless AI 1.9.0, MOJO Scoring Pipeline 2.4.8)

- Launch Ubuntu 18.04 Linux EC2 instance
    - Instance Type: t2.2xlarge
    - Storage: 128GB
    - Open custom TCP port 8081 and source on 0.0.0.0/0

## Task 1: Set Up Environment

### Connect to EC2 from Local Machine

1\. Move the EC2 Pivate Key File (Pem Key) to the .ssh folder

~~~bash
mv $HOME/Downloads/{private-key-filename}.pem $HOME/.ssh/
chmod 400 $HOME/.ssh/{private-key-filename}.pem
~~~

2\. Set EC2 Public DNS and EC2 Pem Key as permanent environment variables

~~~bash
# For Mac OS X, set permanent environment variables 
tee -a $HOME/.bash_profile << EOF
# Set EC2 Public DNS
export DAI_MOJO_CDF_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export DAI_MOJO_CDF_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

# For Linux, set permanent environment variables
tee -a $HOME/.profile << EOF
# Set EC2 Public DNS
export DAI_MOJO_CDF_INSTANCE={EC2 Public DNS}.compute.amazon.com
# Set EC2 Pem Key
export DAI_MOJO_CDF_PEM=$HOME/.ssh/{private-key-filename}.pem
EOF

source $HOME/.bash_profile
~~~

3. Connect to EC2 via SSH

~~~bash
# Connect to EC2 instance using SSH
ssh -i $DAI_MOJO_CDF_PEM ubuntu@$DAI_MOJO_CDF_INSTANCE
~~~

### Create Environment Directory Structure

1. Run the following commands that will create the directories where you could store the **input data**, **mojo-pipeline/** folder.

~~~bash
# Create directory structure for DAI MOJO Flink Projects
mkdir -p $HOME/dai-model-deployment/testData/{test-batch-data,test-real-time-data}
~~~

### Set Up Driverless AI MOJO Requirements in EC2

1\. Build a **Driverless AI Experiment**

- 1a. Upload your dataset or use the following **Data Recipe URL** to import the **UCI Hydraulic System Condition Monitoring Dataset**:

~~~bash
# Data Recipe URL
https://raw.githubusercontent.com/james94/driverlessai-recipes/master/data/hydraulic-data.py
~~~

- 1b. Split the data **75% for training** and **25% for testing**.

- 1c. Run predict on your **training data**.

- 1d. Name the experiment **model_deployment**. Choose the **target column** for scoring. Choose the **test data**. Launch the experiment.

2\. Click **Download MOJO Scoring Pipeline** in Driverless AI Experiment Dashboard

- 2a. Select **Java**, click **Download MOJO Scoring Pipeline** and send **mojo.zip** to EC2.

~~~bash
# Move Driverless AI MOJO Scoring Pipeline to EC2 instance
scp -i $DAI_PEM $HOME/Downloads/mojo.zip ubuntu@$DAI_MOJO_CEM_INSTANCE:/home/ubuntu/dai-model-deployment/
~~~

- 2b. Unzip **mojo.zip**.

~~~bash
cd $HOME/dai-model-deployment/
sudo apt -y install unzip
unzip mojo.zip
~~~

3\. Install **MOJO2 Java Runtime Dependencies** in EC2

- 3a. Download and install Anaconda.

~~~bash
# Download and install Anaconda
wget https://repo.anaconda.com/archive/Anaconda3-2020.02-Linux-x86_64.sh -O $HOME/anaconda.sh
bash $HOME/anaconda.sh
source ~/.bashrc
~~~

- 3b. Create **model-deployment** virtual environment and install the **required packages**

~~~bash
# create virtual environment and install openjdk and maven
conda create -y -n model-deployment python=3.6
conda activate model-deployment
conda install -y -c conda-forge openjdk=8.0.192
conda install -y -c conda-forge maven
~~~

4\. Set **temporary environment variable** for **Driverless AI License File**

~~~bash
scp -i $DAI_MOJO_CDF_PEM $HOME/Downloads/license.sig ubuntu@$DAI_MOJO_CDF_INSTANCE:/home/ubuntu/
export DRIVERLESS_AI_LICENSE_FILE="/home/ubuntu/license.sig"
~~~

### Prepare Hydraulic Test Data For Mojo Flink Scoring

Make sure there is **input test data** in the input directory Flink will be pulling data from.

1\. For **batch scoring**, you should make sure there is one or more files with multiple rows of csv data in the following directory:

~~~bash
# go to mojo-pipeline/ directory with batch data example.csv
cd $HOME/daimojo-flink/mojo-pipeline/

# copy this batch data to the input dir where Flink pulls the batch data
cp example.csv $HOME/daimojo-flink/testData/test-batch-data/
~~~

2\. For **real-time scoring**, you should make sure there are files with a single row of csv data in the following directory:

~~~bash
# go to real-time input dir where we will store real-time data
cd $HOME/daimojo-flink/testData/test-real-time-data/

# copy example.csv to the input dir where Flink pulls the real-time data
cp $HOME/daimojo-flink/mojo-pipeline/example.csv .

# remove file's 1st line, the header
echo -e "$(sed '1d' example.csv)\n" > example.csv

# split file into multiple files having 1 row of data with numeric suffix and .csv extension
split -dl 1 --additional-suffix=.csv example.csv test_

# remove example.csv from real-time input dir
rm -rf example.csv
~~~

### Set Up Flink Local Cluster in EC2

1\. Download **Flink**

~~~bash
cd $HOME
# Download Flink and then extract Flink tgz
wget https://archive.apache.org/dist/flink/flink-1.10.0/flink-1.10.0-bin-scala_2.12.tgz
tar xzf flink-1.10.0-bin-scala_2.12.tgz
~~~

2\. Start the Local Flink Cluster

~~~bash
cd $HOME/flink-1.10.0

# Start Flink
./bin/start-cluster.sh

# Stop Flink
./bin/stop-cluster.sh
~~~

3\. Access the Flink UI: http://${EC2_PUBLIC_DNS}:8081/#/overview

![Flink UI Overview](./images/flink-ui-overview.jpg)

### Compile Flink MOJO ML Data Pipeline Jobs

1\. Download **Driverless AI Deployment Examples** Repo for **Flink** assets

~~~bash
cd $HOME
git clone https://github.com/h2oai/dai-deployment-examples
~~~

2\. Compile the Java code for **Flink MOJO ML Data Pipeline** jobs into a **JAR package**

~~~bash
cd $HOME/dai-deployment-examples/mojo-flink/daimojo-flink-data-pipeline
mvn clean install
~~~

## Task 2: Deploy MOJO Scoring Pipeline to Flink

### Batch Scoring

For Driverless AI MOJO batch scoring in Flink, we will run the following Flink MOJO ML Data Pipeline program **BatchPredHydCoolCond.java** by specifying this class as the entry point for the local Flink cluster to execute.

1\. Run the following command to submit the **Flink MOJO Batch Scoring Job** to the Flink Cluster JobManager for execution:

~~~bash
$HOME/flink-1.10.0/bin/flink run -c org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond.BatchPredHydCoolCond $HOME/dai-deployment-examples/mojo-flink/daimojo-flink-data-pipeline/target/daimojo-flink-data-pipeline-1.10.0.jar
~~~

2\. Once the Flink job finishes running, it will transition to the Completed Job List:

![DAI MOJO Flink Batch Job Finished](./images/finished-dai-mojo-flink-batch-job.jpg)

3\. To view a ETL Pipeline diagram of Flink Job **Deploy DAI Mojo SP within a Flink Batch ETL Pipeline**, click on the Job’s name:

![DAI MOJO Flink Batch ETL Pipeline](./images/dai-mojo-flink-batch-job-etl-pipeline.jpg)

4\. To view the logs for this Flink job, click on one of the blue boxes, click on TaskManagers, click on LOG:

![DAI MOJO Flink Batch Logs](./images/dai-mojo-flink-batch-job-taskmanager-log.jpg)

5\. To view the Batch Scores for Hydraulic Cooling Condition, click on the TaskManager's Stdout:

![DAI MOJO Flink Batch Scores](./images/dai-mojo-flink-batch-scores.jpg)

### Real-Time Scoring

For Driverless AI MOJO batch scoring in Flink, we will run the following Flink MOJO ML Data Pipeline program **RealTimePredHydCoolCond.java** by specifying this class as the entry point for the local Flink cluster to execute.

1\. Run the following command to submit the Flink MOJO Stream Scoring Job for execution:

~~~bash
$HOME/flink-1.10.0/bin/flink run -c org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond.RealTimePredHydCoolCond $HOME/dai-deployment-examples/mojo-flink/daimojo-flink-data-pipeline/target/daimojo-flink-data-pipeline-1.10.0.jar
~~~

2\. Once the Flink job finishes running, it will transition to the Completed Job List:

![DAI MOJO Flink Stream Job Finished](./images/finished-dai-mojo-flink-stream-job.jpg)

3\. Check out the Data Pipeline diagram of the Flink Job **Deploy DAI Mojo SP within a Flink Streaming Data Pipeline**:

![DAI MOJO Flink Stream Data Pipeline](./images/dai-mojo-flink-stream-job-data-pipeline.jpg)

4\. View the Real-Time Scores for Hydraulic Cooling Condition by looking at the TaskManager's Stdout:

![DAI MOJO Flink Stream Scores](./images/dai-mojo-flink-stream-scores.jpg)

## Conclusion

Congratulations, we just deployed a **Driverless AI MOJO Scoring Pipeline** within a **Flink Data Pipeline** to do **batch scoring** or **real-time scoring**. As a recap, we set up the environment in an AWS EC2 instance by setting up the Driverless AI MOJO Scoring Pipeline requirements, setting up a single node Flink cluster and preparing some test data to be used for batch scoring or real-time scoring. With the environment setup, we were able to use a custom Flink DaiMojoTransform RichMapFunction within a Flink Batch ETL Pipeline and a Flink Real-Time Data Pipeline to score our data. From Flink's UI, we were able to see the results from batch scoring and real-time scoring printed to the stdout of Flink's TaskManager.
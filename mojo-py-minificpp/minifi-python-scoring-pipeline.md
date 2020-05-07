# Execute the Driverless AI Python Scoring Pipeline with MiNiFi Data Flow

## Cloudera Integration Point for CDF

Integrate the Driverless AI Python Scoring Pipeline in C++ Runtime with Python Wrapper into Apache MiNiFi C++ through a Python custom processor. This will be a Cloudera integration point for Cloudera Data Flow (CDF), particularly Cloudera Edge Management (CEM). CEM is powered by one or more Apache MiNiFi C++ agents.

## Video Walkthrough

Here is a link to a YouTube video in case you want to see a video walkthrough of running this deployment example: 

## Prerequisites

- Recommended: Launch Ubuntu 18.04 Linux EC2 instance

- Recommended: Create Anaconda or Miniconda Environment

~~~
conda create -n model-deployment python=3.6
conda activate model-deployment
~~~

### MiNiFi C++

- For RHEL/CentOS:

~~~bash
yum install -y epel-release
yum install -y leveldb
~~~

- For Debian/Ubuntu:

~~~bash
apt install -y libleveldb-dev
apt install -y libxml2
~~~

- Download the latest version of MiNiFi C++: https://nifi.apache.org/minifi/download.html

~~~bash
# For Linux
echo "export MINIFI_HOME=/path/to/nifi-minifi-cpp-0.7.0/" | tee -a ~/.profile

# For Mac OS X
echo "export MINIFI_HOME=/path/to/nifi-minifi-cpp-0.7.0/" | tee -a ~/.bash_profile
~~~

### Driverless AI Python Scoring Pipeline

If you have not downloaded the dai-deployment-examples repository, you can do so with the following command:

~~~bash
git clone https://github.com/h2oai/dai-deployment-examples
~~~

- Python Datatable, Pandas, Scipy

~~~bash
pip install datatable
pip install pandas
pip install scipy
~~~

- Driverless AI Python Scoring Pipeline
    - For Linux `model-deployment/common/hydraulic/python-scoring-pipeline/scoring-pipeline/scoring_h2oai_experiment_0abac7c4_8993_11ea_ae54_0242ac110002-1.0.0-py3-none-any.whl`

~~~bash
sudo apt -y install gcc
sudo apt -y install g++
~~~

~~~bash
# For Linux
pip install scoring_h2oai_experiment_0abac7c4_8993_11ea_ae54_0242ac110002-1.0.0-py3-none-any.whl
~~~

- Recommend Set **DRIVERLESS_AI_LICENSE_KEY** as an environment variable for OS that MiNiFi C++ runs on
    - you will need to get Driverless AI product to get the License Key

~~~bash
# Linux user
echo "export DRIVERLESS_AI_LICENSE_KEY={license_key}" | tee -a ~/.profile

# Mac user
echo "export DRIVERLESS_AI_LICENSE_KEY={license_key}" | tee -a ~/.bash_profile
~~~

- Hydraulic Sensor Test Data Set
    - comes with this repo under `model-deployment/common/hydraulic/testData/`


## Process for Developing the MiNiFi C++ Py Processor

Note: This section explains how the processor was built, feel free to go to the next section if you want to run the example.

For developing the MiNiFi C++ Python processor, I used Visual Studio Code. On the nifi-minifi-cpp repo, I read the page called "**Apache NiFi - MiNiFi - Python Processors Readme**", which explains how to write the MiNiFi C++ Python custom processor. I also referenced a few  MiNiFi C++ Python custom processor examples: [ExampleProcessor.py](https://github.com/apache/nifi-minifi-cpp/blob/master/extensions/script/ExampleProcessor.py) and [SentimentAnalysis.py](https://github.com/apache/nifi-minifi-cpp/blob/master/extensions/pythonprocessors/examples/SentimentAnalysis.py). 

I also familiarized myself with executing the Driverless AI Python Scoring Pipeline through reading the examples in the H2O [Driverless AI Standalone Python Scoring Pipeline](http://docs.h2o.ai/driverless-ai/1-8-lts/docs/userguide/scoring-standalone-python.html) documentation. Additionally, I read [HTTP Example for Predictions and Shapley Values](https://github.com/h2oai/dai-deployment-examples/blob/master/python-scoring-pipeline/http_ec2_ubuntu.md) to practice examples of using the Python Scoring Pipeline to do scoring.

When I was writing the code for the MiNiFi C++ Python processor [ExecuteDaiPspBatchScoring.py](model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspBatchScoring.py) and [ExecuteDaiPspRealTimeScoring.py](model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspRealTimeScoring.py), I made sure to install module dependencies using pip, so I could use Driverless AI's Python Scorer and Python Datatable. For example, I ran commands `pip install datatable` and `pip install scoring_h2oai_experiment_0abac7c4_8993_11ea_ae54_0242ac110002-1.0.0-py3-none-any.whl`. You'll notice when you download the Driverless AI Python Scorer module, there is a version for only a version for linux.

If you want to add or update Python code for the MiNiFi C++ Python processor, you can open your favorite IDE, then import the project. I will walk you through the brief steps to open the project in Visual Studio Code:

~~~bash
cd your/path/to/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/

# Open the h2o python processors in Visual Studio Code
code .
~~~

![minifi-python-processor-dev-vscode-2](images/minifi-python-processor-dev-vscode-2.jpg)

**Figure 1:** MiNiFi C++ Python processor "ExecuteDaiPspBatchScoring.py"

Next I needed to write the Python processor and make sure it was added to the MiNiFi product.

Note: the steps moving forward will assume you are running Ubuntu 18.04.

## Add MiNiFi C++ Py Custom Processor to MiNiFi product

If you have not already downloaded the latest version of MiNiFi C++, then download it. MiNiFi C++ is configured by default to read `minifi-python` directory for any python processors. First we will create a `modules` folder in `nifi-minificpp-0.7.0` folder:

~~~bash
# go into nifi-minifi-cpp folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/

# create modules folder
mkdir modules
~~~

We will copy over **DaiPythonScorer.py** to `modules/` folder.

~~~bash
# go to modules folder
cd modules

# copy DaiPythonScorer.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/modules/DaiPythonScorer.py .
~~~

**DaiPythonScorer.py** is needed for the MiNiFi C++ Python processors. With this Python file, we import Driverless AI Python Scorer. We don't directly import the Python Scorer in the python processors because the module name changes frequently. So, the user can update the import statement in this file when the module name changes.

Next we will create an `h2o/dai/psp` folder path in `minifi-python` directory:

~~~bash
# go back to nifi-minifi-cpp folder
cd ../

# go into nifi-minifi-cpp minifi-python/ folder
cd minifi-python/

# create h2o/dai/psp folder path
mkdir -p h2o/dai/psp/
~~~

We will copy over **ConvertDsToCsv.py** to `h2o/` folder.

~~~bash
# go to h2o folder
cd h2o

# copy ConvertDsToCsv.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/ConvertDsToCsv.py .
~~~

Then we copy ExecuteDaiPspBatchScoring.py and ExecuteDaiPspRealTimeScoring.py to `h2o/dai/psp/` folder.

~~~bash
# go into psp (mojo scoring pipeline) folder
cd dai/psp/

# copy ExecuteDaiPspBatchScoring.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspBatchScoring.py .

# copy ExecuteDaiPspRealTimeScoring.py to current folder
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/minifi-python/h2o/dai/psp/ExecuteDaiPspRealTimeScoring.py .
~~~

Now that the python processors are copied over, we need to tell MiNiFi C++ where to find them. So we will overwrite the current `minifi.properties` file with our version of it.

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/

# copy our version of minifi.properties over to conf/ to overwrite the current one
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/conf/minifi.properties .
~~~

We will start MiNiFi C++ after we create the MiNiFi flow through writing a `config.yml` file.

## Build a MiNiFi Flow to do Real-Time & Batch Scoring

You could build a MiNiFi C++ flow from scratch to do batch scoring with the custom processor **ExecuteDaiPspBatchScoring** or to do real-time scoring with the custom processor **ExecuteDaiPspRealTimeScoring**.

To make it easier to see how to run the Driverless AI Python Scoring Pipeline in the MiNiFi flow, I have created two MiNiFi flow config files: one can do interactive (real-time) scoring and the other one can do batch scoring. It just depends on the data you are ingesting into the flow. If you ingest tabular data that has only 1 row of data in each file, then we use **ExecuteDaiPspRealTimeScoring** to do real-time scoring. If you ingest tabular data that has multiple rows of data in each file, then we use **ExecuteDaiPspBatchScoring** to do batch scoring.

Here are the two MiNiFi flow config files:

- MiNiFi Flow Config for batch scoring: [config-psp-batch-scoring.yml](model-deployment/apps/nifi-minifi-cpp/conf/config-psp-batch-scoring.yml)
- MiNiFi Flow Config for real-time scoring: [config-psp-interactive-scoring.yml](model-deployment/apps/nifi-minifi-cpp/conf/config-psp-interactive-scoring.yml)

## Import MiNiFi Flow Config into MiNiFi C++

In the MiNiFi `conf/`, there is a pre-existing `config.yml`. We can replace it with our config file for either batch scoring or interactive scoring. For MiNiFi to recognize the config file, we must rename our chosen config file to `config.yml`. We will walk through how to replace it with our config file for interactive scoring:

~~~bash
# go into nifi-minifi-cpp conf/ folder
cd /home/ubuntu/nifi-minifi-cpp-0.7.0/conf/

# copy our minifi flow config file for interactive scoring to conf/
cp /home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/apps/nifi-minifi-cpp/conf/config-psp-interactive-scoring.yml .

# overwrite the pre-existing config.yml with our config that does interactive scoring
mv config-psp-interactive-scoring.yml config.yml
~~~

Then we can start MiNiFi C++:

~~~bash
# go back to nifi-minifi-cpp base folder
cd ../

# start minifi C++
./bin/minifi.sh start
~~~

**Note:** with Cloudera Edge Management (CEM), we can build MiNiFi flows in a visual canvas similar to how we do for NiFi.

## MiNiFi Run Driverless AI Python Scorer to do Real-Time Scoring

Here we look at the file that the PutFile processor wrote to the local file system, which contains the real-time score result for when MiNiFi C++ executed the Driverless AI Python Scorer on some real-time data (one row of data) to do real-time scoring.

![minifi-real-time-scoring.jpg](images/minifi-real-time-scoring.jpg)

Note: PutFile stores the files with real-time predictions in the following folder path and creates the path if it doesn't exist: `home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/common/hydraulic/predData/pred-real-time-data`

## MiNiFi Run Driverless AI Python Scorer to do Batch Scoring

Here we look at the file that the PutFile processor wrote to the local file system, which contains the batch score result for when MiNiFi C++ executed the Driverless AI Python Scorer on some batch data (multiple rows of data) to do batch scoring.

![minifi-batch-scoring.jpg](images/minifi-batch-scoring.jpg)

Note: PutFile stores the files with batch predictions in the following folder path and creates the path if it doesn't exist: `home/ubuntu/dai-deployment-examples/mojo-py-minificpp/model-deployment/common/hydraulic/predData/pred-batch-data`
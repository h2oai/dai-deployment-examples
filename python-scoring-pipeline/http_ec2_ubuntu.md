# HTTP Example for Predictions and Shapley Values

This guide walks through setting up an HTTP server in an Ubuntu EC2 instance which can return both predictions and shapley values. 

1. Example EC2 Environment Setup
2. Install the Requirements
3. Run the HTTP Server

## Example EC2 Environment Setup 

The linux commands in this guide were specifically tested on Ubuntu and will likely work with any Debian-based distribution. The Driverless AI Python Scoring Pipeline is usuable with other Linux distibutuions and the same steps will apply, but the exact commands will need to be changed to match that distriubtion.



**AMI:** Ubuntu Server 18.04 LTS (HVM), SSD Volume Type

**Instance:** t2.2xlarge

**Storage:** 256GB

**Open Ports:** SSH 22, Custom TCP 9090

**Notes:** Enable public IPv4

When launching your system, you will have the option to select or create a key pair to access the system. This is not required but we reccomend using this method. You can learn more from the [Amazon Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html).

### Connect to the System

You may get an `Unprotected Private Key File` error when you first attempt to connect to your system. This means you'll need to update the permissions on the file. You can learn more about how and why this happens [here](https://99robots.com/how-to-fix-permission-error-ssh-amazon-ec2-instance/).

Since we will be frequently using both our private key file and the EC2 instance name, we will save both of them as environmental variables for this session on our local machine. Below is an example of doing this on MacOS (add these commands to your `~/.bash_profile` if you'd like them to be permenant):

```bash
export DAI_SCORING_INSTANCE="***.compute.amazonaws.com"
export DAI_SCORING_PEM="/file_path_to_pem/***.pem"
```

We can then connect to the instance with the following command:

```bash
ssh -i $DAI_SCORING_PEM ubuntu@$DAI_SCORING_INSTANCE
```

### Set the License Key 

While connected to the EC2 Scoring system, we will want to set our Driverless AI License Key as a environmental variable so that we can use our scoring pipeline. This will be the same license key that you used in Driverless AI to train the model. Without a valid license key the scoring process will fail, so you will likely want to save this as a permenant varibale by adding the following line to your `~/.profile`. 

```bash
export DRIVERLESS_AI_LICENSE_KEY="nHm..."
```

### Move Scoring Pipeline to EC2

We will be using the Python Scoring Pipeline to get our predictions and shapley values. You can download the `Scorer.zip` file from the completed experiment page or using the python client. If you have SSH access to your Driverless AI instance and that instance is running in AWS, it will be much faster to move the file directly from the DAI instance to the EC2 Scoring instance.

Run the following from your local machine (or DAI instance) to move the file to the EC2 scoring machine:

```bash
scp -i $DAI_SCORING_PEM scorer.zip ubuntu@$DAI_SCORING_INSTANCE:
```



## Install the Requirements

In this section all commands will be ran from the EC2 scoring instance. 

We will be using `apt-get` to install the required packages on our instance. Since this is a brand new instance we will need to run the following to make all packages available:

```bash 
sudo apt-get update
```

### Python 3.6

```bash
sudo apt-get install python3.6 python-virtualenv python3.6-dev python3-pip python3-dev python3-virtualenv
```

### OpenBLAS

The OpenBLAS library is used for linear algebra calculations and required to run our scoring pipeline:

```bash
sudo apt-get install libopenblas-dev
```

### Upzip

We'll download the Unzip library and use it to access the individual files in our scoring pipeline:

```bash
sudo apt-get install unzip
unzip scorer.zip
```

### Java 

If you run your experiment with `dai_enable_h2o_recipes=0` java is not needed, but by default Driverless AI gives you the option to include open source H2O-3 algorithms so java is required. Any java version of 8,9,10,11,12 will work.

```bash
sudo apt-get install openjdk-8-jdk
```

### Python Libraries

We will create a virtual environment and then install all of the required libraries. Any time we wish to run one of our python scripts we will activate this environment using `source env/bin/activate` . 

```bash
cd scoring-pipeline
virtualenv -p python3.6 env
source env/bin/activate
pip install -r requirements.txt
```

You may see h2o specific error messages during the install process, this is okay and the install will still be successful. 

```bash
ERROR: h2o4gpu 0.3.2+master.65994f3 has requirement python-dateutil==2.7.2, but you'll have python-dateutil 2.8.1 which is incompatible.
ERROR: h2o4gpu 0.3.2+master.65994f3 has requirement pytz==2018.4, but you'll have pytz 2019.3 which is incompatible.
ERROR: h2o4gpu 0.3.2+master.65994f3 has requirement scipy==1.2.2, but you'll have scipy 1.3.1 which is incompatible.
ERROR: multiprocess 0.70.8 has requirement dill>=0.3.0, but you'll have dill 0.2.9 which is incompatible.
ERROR: h2oaicore 1.8.0 has requirement six==1.12.0, but you'll have six 1.13.0 which is incompatible.
ERROR: h2oaicore 1.8.0 has requirement typesentry==0.2.6, but you'll have typesentry 0.2.7 which is incompatible.
ERROR: h2oaicore 1.8.0 has requirement urllib3==1.23, but you'll have urllib3 1.24.3 which is incompatible.
ERROR: h2oaicore 1.8.0 has requirement wheel==0.33.4, but you'll have wheel 0.33.6 which is incompatible.
```

### Test Install

We will run the example script to test that we can get predictions, transform a dataset, and get Shapley values:

```bash
python example.py
```

Output should be similiar to the following, but columns and predictions will match the data in your scoring pipeline:

```bash
2019-11-21 00:39:05,164 C:   D:      M:        27521 DEBUG  : Could not locate cudart (None) or other error: #_python example.pyget_cuda_versions_subprocess: undefined symbol: cudaRuntimeGetVersion
2019-11-21 00:39:05,186 C:   D:      M:        27521 DEBUG  : Could not locate cudnn (None) or other error #_python example.pyget_cuda_versions_subprocess: undefined symbol: cudnnGetVersion
2019-11-21 00:39:06,112 C:▁ D:242.2GB M:30.7GB 27422 INFO   : font search path ['/home/ubuntu/scoring-pipeline/env/lib/python3.6/site-packages/matplotlib/mpl-data/fonts/ttf', '/home/ubuntu/scoring-pipeline/env/lib/python3.6/site-packages/matplotlib/mpl-data/fonts/afm', '/home/ubuntu/scoring-pipeline/env/lib/python3.6/site-packages/matplotlib/mpl-data/fonts/pdfcorefonts']
2019-11-21 00:39:06,316 C:▁ D:242.2GB M:30.7GB 27422 INFO   : generated new fontManager
2019-11-21 00:39:07,317 C:▁ D:242.2GB M:30.6GB 27422 INFO   : Starting H2O server for recipes
2019-11-21 00:39:10,501 C:▁ D:242.2GB M:30.5GB 27422 INFO   : RECIPE H2O-3 server started
2019-11-21 00:39:10,501 C:▁ D:242.2GB M:30.5GB 27422 INFO   : Started H2O version 3.26.0.1 at http://127.0.0.1:50341
2019-11-21 00:39:10,600 C:   D:      M:        27422 INFO   : License manager initialized
2019-11-21 00:39:10,600 C:   D:      M:        27422 INFO   : -----------------------------------------------------------------
2019-11-21 00:39:10,600 C:   D:      M:        27422 INFO   : Checking whether we have a valid license...
2019-11-21 00:39:10,600 C:   D:      M:        27422 INFO   : No Cloud provider found
2019-11-21 00:39:10,600 C:   D:      M:        27422 INFO   : License inherited from environment
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : 
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : license_version:1
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : serial_number:35
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : licensee_organization:H2O.ai
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : licensee_email:tomk@h2o.ai
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : licensee_user_id:35
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : is_h2o_internal_use:true
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : created_by_email:tomk@h2o.ai
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : creation_date:2019/06/11
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : product:DriverlessAI
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : license_type:developer
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : expiration_date:2020/01/01
2019-11-21 00:39:10,602 C:   D:      M:        27422 INFO   : 
2019-11-21 00:39:10,604 C:   D:      M:        27422 INFO   : License is valid
2019-11-21 00:39:10,604 C:   D:      M:        27422 INFO   : -----------------------------------------------------------------
2019-11-21 00:39:10,618 C:   D:      M:        27422 INFO   : Overriding variable with experiment-specific settings: enable_benchmark not a valid key, enable_startup_checks = False (was True), authentication_method = local (was unvalidated), enabled_file_systems = ['upload', 'file', 'hdfs', 's3', 'gcs', 'gbq', 'minio', 'snow', 'kdb', 'azrbs', 'jdbc'] (was ['upload', 'file', 'hdfs', 's3']), nfeatures_max = 5 (was -1), included_transformers = ['CVCatNumEncodeTransformer', 'CVTargetEncodeTransformer', 'CatOriginalTransformer', 'CatTransformer', 'ClusterDistTransformer', 'ClusterIdTransformer', 'ClusterTETransformer', 'DateOriginalTransformer', 'DateTimeOriginalTransformer', 'DatesTransformer', 'EwmaLagsTransformer', 'FrequentTransformer', 'InteractionsTransformer', 'IsHolidayTransformer', 'IsolationForestAnomalyNumCatAllColsTransformer', 'IsolationForestAnomalyNumCatTransformer', 'IsolationForestAnomalyNumericTransformer', 'LagsAggregatesTransformer', 'LagsInteractionTransformer', 'LagsTransformer', 'LexiLabelEncoderTransformer', 'NumCatTETransformer', 'NumToCatTETransformer', 'NumToCatWoEMonotonicTransformer', 'NumToCatWoETransformer', 'OneHotEncodingTransformer', 'OriginalTransformer', 'RawTransformer', 'TextBiGRUTransformer', 'TextCNNTransformer', 'TextCharCNNTransformer', 'TextLinModelTransformer', 'TextTransformer', 'TruncSVDNumTransformer', 'WeightOfEvidenceTransformer'] (was []), included_models = ['FTRL', 'GLM', 'IMBALANCEDLIGHTGBM', 'IMBALANCEDXGBOOSTGBM', 'LIGHTGBM', 'RULEFIT', 'TENSORFLOW', 'XGBOOSTDART', 'XGBOOSTGBM'] (was []), included_scorers = ['ACCURACY', 'AUC', 'AUCPR', 'F05', 'F1', 'F2', 'GINI', 'LOGLOSS', 'MACROAUC', 'MAE', 'MAPE', 'MCC', 'MER', 'MSE', 'R2', 'RMSE', 'RMSLE', 'RMSPE', 'SMAPE'] (was []), top_pid = 3249 (was -1), resumed_experiment_id = 7cae856e-05ae-11ea-a91e-0242ac110002 (was ), experiment_id = 676b75a4-0b2b-11ea-976f-0242ac110002 (was ), experiment_tmp_dir = ./tmp/h2oai_experiment_676b75a4-0b2b-11ea-976f-0242ac110002 (was ), config_overrides = nfeatures_max=5 (was ), 
2019-11-21 00:39:10,618 C:   D:      M:        27422 INFO   : 
---------- Score Row ----------
2019-11-21 00:39:11,372 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.09053480625152588, 0.9094651937484741]
2019-11-21 00:39:12,103 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.09053480625152588, 0.9094651937484741]
2019-11-21 00:39:12,838 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.9849987030029297, 0.015001311898231506]
2019-11-21 00:39:13,564 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.08170926570892334, 0.9182907342910767]
2019-11-21 00:39:14,292 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.07289433479309082, 0.9271056652069092]
---------- Score Frame ----------
2019-11-21 00:39:15,036 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
   Churn?.False.  Churn?.True.
0       0.090535      0.909465
1       0.090535      0.909465
2       0.984999      0.015001
3       0.081709      0.918291
4       0.072894      0.927106
5       0.083332      0.916668
6       0.898980      0.101020
7       0.083332      0.916668
8       0.083332      0.916668
9       0.984999      0.015001
2019-11-21 00:39:15,781 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
---------- Get Per-Feature Prediction Contributions for Row ----------
2019-11-21 00:39:16,497 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
[0.03984469547867775, -0.5961417555809021, 0.14606477320194244, 0.7558959722518921, 1.9614592790603638, 0.0]
---------- Get Per-Feature Prediction Contributions for Frame ----------
2019-11-21 00:39:17,251 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
   contrib_10_Intl Charge  ...  contrib_bias
0                0.039845  ...           0.0
1                0.039845  ...           0.0
2               -0.536627  ...           0.0
3                0.028152  ...           0.0
4                0.151119  ...           0.0
5                0.017183  ...           0.0
6               -0.305835  ...           0.0
7                0.017183  ...           0.0
8                0.017183  ...           0.0
9               -0.536627  ...           0.0

[10 rows x 6 columns]
---------- Transform Frames ----------
2019-11-21 00:39:17,435 C:   D:      M:        27422 INFO   : Using 3 parallel workers (1 parent workers) for fit_transform.
/home/ubuntu/scoring-pipeline/env/lib/python3.6/site-packages/sklearn/preprocessing/label.py:252: DataConversionWarning: A column-vector y was passed when a 1d array was expected. Please change the shape of y to (n_samples, ), for example using ravel().
  y = column_or_1d(y, warn=True)
2019-11-21 00:39:18,076 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
2019-11-21 00:39:18,663 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
2019-11-21 00:39:19,257 C:   D:      M:        27422 INFO   : Submitted    3 and Completed    3 non-identity feature engineering tasks out of    5 total tasks (including    2 identity)
   10_Intl Charge  13_Night Charge  ...  37_NumToCatWoEMonotonic:CustServ Calls.0  Churn?
0            0.92             2.89  ...                                  0.000000  False.
1            0.84             2.89  ...                                  1.098612  False.
2            0.84             3.41  ...                                 -1.098612   True.
3            0.97             2.40  ...                                  0.451985  False.
4            1.11             2.40  ...                                  0.451985   True.
5            0.84             3.48  ...                                  1.098612  False.
6            1.05             2.43  ...                                  0.451985  False.
7            0.92             2.43  ...                                  0.000000  False.
8            0.84             2.89  ...                                  1.609438   True.
9            0.84             3.25  ...                                 -1.098612   True.

[10 rows x 6 columns]
   10_Intl Charge  13_Night Charge  ...  37_NumToCatWoEMonotonic:CustServ Calls.0  Churn?
0            0.92             2.89  ...                                  0.510826  False.
1            0.84             2.89  ...                                  1.609438  False.
2            0.84             3.41  ...                                 -1.609438   True.
3            0.97             2.40  ...                                  1.098612  False.
4            1.11             2.40  ...                                 -1.098612   True.
5            0.84             3.48  ...                                  1.609438  False.
6            1.05             2.43  ...                                  1.098612  False.
7            0.92             2.43  ...                                  0.510826  False.
8            0.84             2.89  ...                                  0.510826   True.
9            0.84             3.25  ...                                 -1.609438   True.

[10 rows x 6 columns]
   10_Intl Charge  13_Night Charge  ...  37_NumToCatWoEMonotonic:CustServ Calls.0  Churn?
0            0.92             2.89  ...                                  0.510826  False.
1            0.84             2.89  ...                                  1.609438  False.
2            0.84             3.41  ...                                 -1.609438   True.
3            0.97             2.40  ...                                  1.098612  False.
4            1.11             2.40  ...                                 -1.098612   True.
5            0.84             3.48  ...                                  1.609438  False.
6            1.05             2.43  ...                                  1.098612  False.
7            0.92             2.43  ...                                  0.510826  False.
8            0.84             2.89  ...                                  0.510826   True.
9            0.84             3.25  ...                                 -1.609438   True.

[10 rows x 6 columns]
---------- Retrieve column names ----------
("Int'l Plan", 'Day Charge', 'Eve Charge', 'Night Charge', 'Intl Charge', 'CustServ Calls')
---------- Retrieve transformed column names ----------
['10_Intl Charge', '13_Night Charge', "21_CVTE:Int'l Plan.0", '28_InteractionAdd:Day Charge:Eve Charge', '37_NumToCatWoEMonotonic:CustServ Calls.0']
H2O session _sid_9d90 closed.
(env) ubuntu@ip-10-0-0-225:~/scoring-pipeline$ 

```



## Run the HTTP Server

Kick off the HTTP Server by running the following from the EC2 instance:

```bash
bash run_http_server.sh
```

You can use the `scoring-pipeline/run_http_client.sh` file to get example json requests for your specific scoring pipeline.

### Get Predictions for a Row

Run the following from your local machine:

```bash
curl http://$DAI_SCORING_INSTANCE:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
{
  "id": 2,
  "method": "score",
  "params": {
    "row": {
      "Int'l Plan": "no",
      "Day Charge": "8.760000228881836",
      "Eve Charge": "5.46999979019165",
      "Night Charge": "3.4800000190734863",
      "Intl Charge": "1.1299999952316284",
      "CustServ Calls": "0.0"
    }
  }
}
EOF
```

```json
{
  "jsonrpc": "2.0"
  , "id": 2
  , "result": [0.9803596138954163, 0.01964038610458374]
}
```

### Get Feature Column Names for a Row

We will request a list of feature names, so that when we retreive Shapley values we know which value is which column.

Run the following from your local machine:

```bash
curl http://$DAI_SCORING_INSTANCE:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
{
  "id":1,
  "method":"get_transformed_column_names",
  "params":{}
}
EOF
```

```json
{
  "jsonrpc": "2.0"
  , "id": 1
  , "result": ["10_Intl Charge"
               , "13_Night Charge"
               , "21_CVTE:Int'l Plan.0"
               , "28_InteractionAdd:Day Charge:Eve Charge"
               , "37_NumToCatWoEMonotonic:CustServ Calls.0"]
}
```

### Get Shapley Values for a Row

We can use the exact same call as we did for predicts, but this time include the parameter `pred_contribs: true`.

Run the following from your local machine:

```bash
curl http://$DAI_SCORING_INSTANCE:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
{
  "id": 2,
  "method": "score",
  "params": {
    "row": {
      "Int'l Plan": "no",
      "Day Charge": "8.760000228881836",
      "Eve Charge": "5.46999979019165",
      "Night Charge": "3.4800000190734863",
      "Intl Charge": "1.1299999952316284",
      "CustServ Calls": "0.0"
    },
    "pred_contribs": true
  }
}
EOF
```

```json
{
  "jsonrpc": "2.0"
  , "id": 2
  , "result": [-0.31093353033065796
               , -1.2875813245773315
               , -0.4879034757614136
               , -1.1715366840362549
               , -0.6523758769035339
               , 0.0]
}
```
# Shapley Values in ec2

This guide walks through setting up an http server in an ec2 instance which can return both predictions and shapley values. 

1. Example ec2 Environment Setup
2. Install the requirements
3. Run the HTTP Server

## Example ec2 Environment Setup 

The linux commands in this example are for Ubuntu, but any flavor of linux would work fine.

**AMI:** Ubuntu Server 18.04 LTS (HVM), SSD Volume Type

**Instance:** t2.2xlarge

**Storage:** 256GB

**Open Ports:** SSH 22, Custom TCP 9090

**Notes:** Enable public IPv4

### Connect to the System

Example command to ssh to the system with a private key

```bash
chmod 600 ***key***.pem
ssh -i ***key***.pem ubuntu@***.compute.amazonaws.com
```

Update packages you can install

```bash 
sudo apt-get update
```

### Move Scorer.zip to EC2

This is the python scoring pipeline you can download from a completed experiment, run the following from the DAI instance or your local machine:

```bash
scp -i ***key***.pem scorer.zip ubuntu@***.compute.amazonaws.com:
```

```
ssh -i ***key***.pem ubuntu@***.compute.amazonaws.com
```

```bash
unzip scorer.zip
cd scoring-pipeline/
```

### Set the License Key 

```bash
export DRIVERLESS_AI_LICENSE_KEY="oLqLZXMI0y..."
```



## Install the Requirements

### Python 3.6

```bash
sudo apt install python3.6 python3.6-dev python3-pip python3-dev python-virtualenv python3-virtualenv
```

### OpenBLAS

```bash
sudo apt-get install libopenblas-dev
```

### Upzip

```bash
sudo apt-get install unzip
```

### Java 

If you run your experiment with `dai_enable_h2o_recipes=0` java is not needed, but by default we give you the option to include open source H2O-3 algorithms so java is required. Any java version of 8,9,10,11,12 will work.

```bash
sudo apt install openjdk-8-jdk
```

### Python Libraries

Create a python environment with all required packages. Any time we want to run the scoring we will want to activate this environment.

```bash
virtualenv -p python3.6 env
source env/bin/activate
pip install -r requirements.txt
```

The following are okay!

![image-20191119163130015](/Users/mtanco/Library/Application Support/typora-user-images/image-20191119163130015.png)

### Test Install

```bash
python example.py
```

## Run the HTTP Server

Kick off the HTTP Server

```bash
bash run_http_server.sh
```

### Get Prediction for a Row:

Change localhost to ***.compute.amazonaws.com to run from your local machine

```bash
curl http://localhost:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
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

### Get Feature Column Names for a Row:

Change local host to ***.compute.amazonaws.com to run from your local machine. Use the file `run_http_client.sh` for example json format

```bash
curl http://localhost:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
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

Include `"pred_contribs": true`

```bash
curl http://localhost:9090/rpc --header "Content-Type: application/json" --data @- <<EOF
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






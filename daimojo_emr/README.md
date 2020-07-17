# daimojo on EMR

## Upload to s3

- `daimojo-emr-bootstrap.sh`

## EMR Create Cluster - Advanced Options

- Use EMR release `emr-5.29.0` (required as it's the last release that uses Python 3.6)

	- Select Hadoop and Spark

- Configure Cluster Nodes and Instance

- Setup a bootstrap action that points to the `daimojo-emr-bootstrap.sh` script on s3

- Select an EC2 key pair so you can ssh into the Master node

## Prepare to Run

Use `scp` to upload `daimojo-pyspark-udf.py`, `run-daimojo.sh`, `pipeline.mojo`, `license.sig` to the EMR cluster master node.

Edit `daimojo-pyspark-udf.py` so that the input/output paths are set, for example:

	input_path = 's3://h2oai-joeg-us-east-1/data/example.csv'
	output_path = 's3://h2oai-joeg-us-east-1/data/output.csv'
	
## Spark Tuning

Change the number of partitions in `daimojo-pyspark-udf.py`, the optimal number will depend on how many executors, the type of executor, and data homogeneity:

    input_df = spark.read.csv(input_path, header=True).repartition(100) # tune number of partitions
    
Set number of cores per executor, executor memory, driver memory in `run-daimojo.sh`.

## Run

On the Master node, run `bash run-daimojo.sh`.


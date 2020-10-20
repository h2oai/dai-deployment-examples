PYSPARK_PYTHON=/usr/bin/python3 \
DRIVERLESS_AI_LICENSE_KEY=`cat license.sig` \
spark-submit \
	--conf spark.executorEnv.DRIVERLESS_AI_LICENSE_KEY=`cat license.sig` \
	--conf spark.executorEnv.ARROW_PRE_0_15_IPC_FORMAT=1 \
    --driver-memory=8g \
    --executor-cores=8 \
    --executor-memory=8g \
	--files pipeline.mojo \
	daimojo-pyspark-udf.py

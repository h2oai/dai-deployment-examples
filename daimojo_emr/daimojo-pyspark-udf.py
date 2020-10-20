import pandas as pd
import pyspark.sql.functions as F
from pyspark import SparkFiles
from pyspark.sql.types import DoubleType, StringType, ArrayType
from pyspark.sql import SparkSession


model_name = "pipeline.mojo"
input_path = 's3://h2oai-joeg-us-east-1/data/example.csv'
output_path = 's3://h2oai-joeg-us-east-1/data/output.csv'

@F.pandas_udf(returnType=ArrayType(DoubleType()))
def predict_pandas_udf(*cols):
    import daimojo.model
    import datatable as dt
    model_path_remote = SparkFiles.get(model_name)
    model = daimojo.model(model_path_remote)
    X = [pd.to_numeric(c).values for c in cols]
    X = dt.Frame(X, names=model.feature_names)
    return pd.Series(model.predict(X).to_numpy().tolist())

def main():
    import daimojo.model

    spark = SparkSession.builder.appName('daimojo').getOrCreate()
    spark.sparkContext.addFile(model_name)
    model_path_remote = SparkFiles.get(model_name)
    model = daimojo.model(model_path_remote)

    input_df = spark.read.csv(input_path, header=True).repartition(100) # tune number of partitions

    df_pred_multi = (
        input_df.select(
            # F.col('id'), # including a unique identifier is recomended
            predict_pandas_udf(*model.feature_names).alias('predictions')
        ).select(
            # F.col('id'), # including a unique identifier is recomended
            *[F.col('predictions')[i].alias(f'prediction_{c}') for i, c in enumerate(model.output_names)]
        )
    )
    df_pred_multi.write.format('csv').mode('overwrite').option('header', 'true').save(output_path)

main()

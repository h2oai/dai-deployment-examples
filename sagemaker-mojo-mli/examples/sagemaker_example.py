import boto3
import time

client = boto3.client('sagemaker-runtime')

endpoint_name = "mojo-mli-test"
content_type = "application/json"
payload = """
{
  "id": 1,
  "method": "score_both",
  "params": {
    "row": {
      "PAY_AMT4": 14,
      "BILL_AMT2": -137,
      "PAY_2": -1,
      "BILL_AMt1": -208,
      "BILL_AMT6": -1216,
      "BILL_AMT4": -212,
      "PAY_AMT1": 6,
      "PAt_0": -2,
      "PAY_3": 7,
      "BILL_AMT5": -1005,
      "PAY_AMT2": 146,
      "PAY_5": 4,
      "LIMIT_BAL": 50000,
      "PAY_AMT3": 29,
      "PAY_AMT5": 7,
      "BILL_AMT3": -176,
      "PAY_AMT6": 0
    }
  }
}
"""

start = time.time()

response = client.invoke_endpoint(
    EndpointName=endpoint_name,
    ContentType=content_type,
    Body=payload.replace('\n', ' ')
    )

print("Time:", time.time() - start)

print(response)

response_body = response['Body']
print(response_body.read().decode('utf-8'))


# Sagemaker MOJO and MLI Scoring

This is an example of getting single row predictions and reason codes at the same time from Amazon Sagemaker.
The example assumes you have the following from Driverless AI:

-  `mojo.zip` from a completed experiment (to get predictions)
-  `scorer.zip` from a completed MLI interpretation of the experiment (to get reason codes)


## Inference Image

This section describes creating an inference image for Amazon Sagemaker.

### Prepare the files needed for the MLI http server:

```
unzip scorer.zip
mv scoring-pipeline-mli/[^scoring_mli_experiment]*.whl server_setup
mv scoring-pipeline-mli/http_server_requirements.txt server_setup
sed '/scoring_mli_experiment/d' scoring-pipeline-mli/requirements.txt > server_setup/requirements.txt
```

### Prepare files needed for the MOJO rest server:

Build the `local-rest-scorer` as described in the instructions here: https://github.com/h2oai/dai-deployment-templates. Place the resulting `local-rest-scorer-*.jar` in the `server_setup` directory.

### Build in Docker:

```
docker build -t h2oai/sagemaker-mojo-mli .
```

### Add to AWS ECR:

Create `h2oai/sagemaker-hosted-scorer` repository in Sagemaker for the inference image.

Use the output of the command below to `docker login`:

```
aws ecr get-login --region <region> --no-include-email
```

Tag the scorer service image for loading into the `h2oai/sagemaker-hosted-scorer` repository.

```
docker tag <IMAGE ID> <aws_account_id>.dkr.ecr.<region>.amazonaws.com/h2oai/sagemaker-hosted-scorer
```

Then push the scorer service image to AWS ECR (Elastic Container Registry):

```
docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/h2oai/sagemaker-hosted-scorer
```


## Model Artifacts

This section describes creating model artifacts that are used by the inference image.


### Prepare files needed for the model artifacts:

```
unzip scorer.zip
mv scoring-pipeline-mli/scoring_mli_experiment*.whl model_artifacts
unzip mojo.zip
mv mojo-pipeline/pipeline.mojo model_artifacts
```

Optionally add your `license.sig` file to `model_artifacts`. You can also enter the license key as an environment variable in Sagemaker.


### Add the model artifacts to s3:

```
cd model_artifacts
tar cvf model_artifacts.tar *
gzip model_artifacts.tar
aws s3 cp model_artifacts.tar.gz s3://<your-bucket>/
```

## Next Steps

Create the appropriate model and endpoint on Sagemaker, using the inference image and model artifacts. You can check that the endpoint is available with `aws sagemaker list-endpoints`.

See the `examples` directory for examples of making a request to Sagemaker.

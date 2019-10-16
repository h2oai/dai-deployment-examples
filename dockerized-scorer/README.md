# Dockerized scorer example
The steps here allow one to deploy a DAI model (scorer) as a dockerized http service.  This should work even for custom  recipes.

### Prerequisites
1. docker installed (so the `docker` command is available)
1. Access to a Driverless AI (DAI) instance.

### Steps 
1.  create and train an experiment in DAI
1.  once trained, click on "DOWNLOAD PYTHON SCORING PIPELINE" This will store a `scorer.zip` file
1.  run `build_scorer_docker.sh` as in this example

    ```bash
    $ ./dai-deployment-examples/dockerized-scorer/build_scorer_docker.sh ~/Downloads/scorer.zip myimage my-version
    building  myimage:my-version in /var/folders/33/28l9l21n7d3f4h2pswyqqjt00000gn/T/scorer_temp.mIV517QJ
    Sending build context to Docker daemon  396.2MB
    Step 1/18 : FROM ubuntu:18.04
    ...
    Successfully built 3efd661cf151
    Successfully tagged myimage:latest
    Successfully tagged myimage:my-version
    cleaning up
    Build complete.
    Run with
    
    docker run -p 9090:9090 --env DRIVERLESS_AI_LICENSE_KEY=<contents of license.sig file>  myimage:my-version
    ```

    This creates the image and tags it with `myimage:latest` and `myimage:my-version`.  If either image name or 
    version are not specified, defaults will be used.  Run `build_scorer_docker.sh` with no arguments for details.
    
    The last line of the build output is the command to run the image.  Actual use of the scoring service is documented [here](http://docs.h2o.ai/driverless-ai/latest-stable/docs/userguide/scoring-standalone-python.html?highlight=service#scoring-service-http-mode-json-rpc-2-0).
    
Questions? Email [Arthur Kantor](arthur.<REMOVETHIS>kantor@h2o.ai).
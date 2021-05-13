# Python Scoring Pipeline Customer Docker Image Example

This directory demonstrates how to containerize a Python Scoring Pipeline obtained from H2O Driverless AI (DAI) without using a TAR SH install of DAI. The base image used in this example is Ubuntu 18.04.

This example
shows how to use DAI python scoring pipeline as a python module. There are other options like HTTP service and TCP service that can be created too.

Disclaimer
----------

The scoring pipeline wrapper code shared in this directory is created to provide you 
a sample starting point and is not intended to be directly deployed to production as is.
You can use this starting point and build over it to solve your deployment needs ensuring
that your security etc. requirements are met.

# Prerequisites

The following pre-requisites are needed
- [Docker](https://www.docker.com/) 

# Installation

To build the Docker image for your Python Scoring Pipeline, place the scorer.zip and license.sig in the payload directory. Once the scorer and the license key are placed in the payload directory, simply run the following command to build the Docker image:

```
make python-scoring-pipeline-ubuntu18.04
```

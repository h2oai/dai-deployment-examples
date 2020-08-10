# Driverless AI Deployment Examples

This repository contains different examples for deploying Driverless AI (DAI) scorers.

## When to Use this Repository

The goal of the examples in this repo is to easily convey the necessary steps.

To this end, we aim to have the examples:
 - self-contained
 - simple

In production deployments, the following points should be considered:
 - test coverage
 - code reuse
 - error handling
 - monitoring

In other words, here we prioritize simple to follow steps over production readiness.
See https://github.com/h2oai/dai-deployment-templates for production ready
deployment templates.

## Deployment Examples

- [UDF for Running the MOJO in Database](./mojo-db-udf)
   - This example walks through how to use the MOJO as a UDF within in SQL database that has a JDBC type 4 driver.
- [UDF for Running the MOJO in Hive](./mojo-db-hive)
   - This example will walk through how to use the MOJO as a UDF within a HIVE, a database that does not have a JDBC type 4 driver.
- [Python Scoring Pipeline Examples](./python-scoring-pipeline)
   - These examples are based off of the README.txt that comes with the Python Scoring Pipeline and details the many ways it could be used. Here, each example is standalone and has step-by-step instructions for preparing your environment and testing that the setup with successful.
   - Available Examples:
      - [EC2 Ubuntu HTTP Server](./python-scoring-pipeline/http_ec2_ubuntu.md)
- [Flink Custom RichMapFunction for Running the MOJO in Flink Data Pipeline](./mojo-flink)
   - This example will walkthrough how to use a Flink custom RichMapFunction to execute the MOJO Scoring Pipeline within a Flink Data Pipeline to do batch scoring and real-time scoring.
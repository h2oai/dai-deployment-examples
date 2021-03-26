# Alteryx Tool
Integration with Alteryx using HTML Plugins

Note: This integration was originally written for Driverless AI release version 1.8.0, using the python client associated with that specific release. It is highly likely that future releases 1.8.1+ will require code changes to work properly. See below for further information. 

## Making Code Changes

To make changes to the code for functionality past 1.8.0 open a code editor. It is recommended to use an IDE such as pycharm or vscode with a python environment having the release specific Driverless AI python client installed. This will help with identifying the locations where new parameters are required by the Driverless AI python client

Search for the following code snippet: `self.dai`. This points to the initialized Driverless AI python client within the Alteryx frameworks. All the work done by Driverless AI would be initiated via calls from this object.

Add any necessary code changes, and save the file. Then follow the below steps to generate a new Alteryx .yxi to install the plugins to a running Alteryx. 

## Steps for Installing to Alteryx:
How to generate `.yxi` plugin for Alteryx. Take note of project directory structure.

Required Directory Structure:
```bash
alteryx-plugins
|-- INDEPENDENT_COMPONENT
    |-- INDEPENDENT_COMPONENT.html
    |-- INDEPENDENT_COMPONENT.js
    |-- INDEPENDENT_COMPONENT.py
    |-- INDEPENDENT_COMPOENENT_CONFIG.xml
    |-- requirements.txt
    |-- other_required_files
|-- Config.xml
|-- other_required_files
```

* `alteryx-plugins` Directory:
    - requires just one file other than the embedded component directories: `Config.xml`
* INDEPENDENT_COMPONENT:
    - requires:
        - INDEPENDENT_COMPONENT_CONFIG.xml
        - python script
        - html script for defining ui in alteryx gui
        - (optional) js script to augment html script
        - requirements.txt file for describing python environment

Assuming all files are properly defined and generated:
1. create a zip archive of `alteryx-plugins` directory.
    - NOTE: make sure to enter into the directory and then create the archive of all
    files inside of the directory
2. rename the resultant zip archive to `.yxi` file format. This will make it identifiable by Alteryx

## OAuth Configuration:

* To setup OAuth authentication mode, you can enable the `Use OAuth` option. You will then need to fill up the following boxes:
    - `Token Endpoint URL`, taken from the config.toml value: `oauth2_client_tokens_token_url`
    - `Token Introspection URL` taken from the config.toml value: `oauth2_client_tokens_introspection_url`
    - `Client ID` taken from the config.toml value: `oauth2_client_tokens_client_id`
    - `Refresh Token` generated from the DriverlessAI UI under the Resources, then API TOKEN section from the top menu.

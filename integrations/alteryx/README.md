# Alteryx Tool
Integration with Alteryx using HTML Plugins

## Steps to Publish:
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

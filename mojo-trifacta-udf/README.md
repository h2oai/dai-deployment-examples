# TRIFACTA UDF

two modes local scoring and scoring against rest server

### Build:
1. Pull master branch of partner-engineering repo
2. `cd trifacta/trifacta-dai-udf`
3. COMMAND: `./gradlew shadowJar`

### Configure Trifacta to use JAVA UDF
1. `scp` compiled udf jar artifact to Trifacta machine and move to Trifacta process working directory.
    * you can check the working dir by looking at file trifacta-config.json
2. `ssh` into Trifacta machine or go to admin settings in Trifacta console
    * ssh: follow example code below to edit trifacta-config.json file and restart Trifacta service
    * console: use search bar to find the appropriate config settings and edit in the console as appropriate. Click `Save`

EXAMPLE:
```
...
"feature": {
  "enableUDFTransform": {
    "enabled": true // THIS IS IMPORTANT!!
  }
},
"udf-service": {
  "classpath": "%(topOfTree)s/services/udf-service/build/libs/udf-service.jar:%(topOfTree)s/services/udf-service/build/dependencies/*",
  "additionalJars": [
    "/vagrant/libs/custom-udfs-sdk/build/libs/custom-udfs-example.jar",
    "/path/to/new/java/udf.jar"
  ],
  "udfPackages": [
    "com.trifacta.trifactaudfs",
    "package.name.of.udf" // For precompiled jar this is: "ai.h2o.dai.trifacta.mojo.udf"
  ]
},
...
```

### Local:
1. add license.sig and mojos to file in trifacta working directory called dai
2. configure trifacta-config.json in /opt/trifacta/conf/ to have correct udf settings as stated in trifacta docs
3. in dataset on trifacta, convert all columns into single column separated by a specified delimter
4. SCORE -- inputs. One dictionary '{"mojo_name":"pipeline.mojo", "delimiter": "," }'
   * mojo_name = name of file in `{trifacta_working_directory}/dai/`
   * delimiter = delimiter used to separate column values when concatenating columns in trifacta

### Rest:
1. Launch rest server somewhere that is callable by trifacta
2. configure trifacta-config.json to have correct udf settings as stated in trifacta docs
   * NOTE: you may need to increase some of the settings to allow for greater batch_size, udfCommunicationTimeout, and outputBufferSize
3. in dataset on trifacta, convert all columns into single column separated by a ","
4. SCORE -- input. One dictionary '{"mojoName": "pipeline.mojo", "serverIP": "ip address of rest server", "serverPort":"port on which rest server is listening"}'
   * mojoName = name of mojo pipeline file as is seen by rest server
   * serverIP = ip address that the mojo server is exposed on
   * serverPort = port number on which mojo server is listening

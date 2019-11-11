package common.ai.h2o.dai.mojo;


import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.MojoPipeline;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;


public class MojoScoringInterface {

    public String transformLocal(MojoPipeline mojo, MojoFrameMeta mojoFrameMeta, String inputRow, String delimiter) {
        try {
            String[] rowArray = inputRow.split(delimiter);
            MojoFrame inputFrame = generateInputMojoFrame(mojo, mojoFrameMeta, rowArray);
            MojoFrame outputFrame = mojo.transform(inputFrame);
            return generateResponse(outputFrame);
        } catch (Exception e) {
            System.out.println("EXCEPTION: ");
            System.out.println(e.getMessage());
        }
        return null;
    }

    public String transformRest(String inputRow, String mojoName, String serverIP, String serverPort) {
        try {
            String restRequest = generateRestRequest(serverIP, serverPort, mojoName, inputRow);
            HttpURLConnection conn = getRestRequest(restRequest);
            String restResponse = handleRestResponse(conn);
            return convertRestResponseToJson(restResponse);
        } catch (Exception e) {
            System.out.println("ERROR MESSAGE:");
            System.out.println(e.getMessage());
            Gson gson = new Gson();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("ERROR", "There was an error running the UDF, check the logs");
            jsonObject.addProperty("CAUSE", e.getMessage());
            return gson.toJson(jsonObject);
        }
    }

    // Utility functions for local transformations
    private MojoFrame generateInputMojoFrame(MojoPipeline mojo, MojoFrameMeta mojoFrameMeta, String[] rowArray) {
        MojoFrameBuilder mojoFrameBuilder = mojo.getInputFrameBuilder();
        MojoRowBuilder mojoRowBuilder = mojoFrameBuilder.getMojoRowBuilder();
        for(int i = 0; i < rowArray.length; i++) {
            mojoRowBuilder.setValue(mojoFrameMeta.getColumnName(i), rowArray[i]);
        }
        mojoFrameBuilder.addRow(mojoRowBuilder);
        return mojoFrameBuilder.toMojoFrame();
    }

    private String generateResponse(MojoFrame outputFrame) {
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        String[] colNames = outputFrame.getColumnNames();
        for(int i = 0; i < colNames.length; i++) {
            String predictionVal = outputFrame.getColumn(i).getDataAsStrings()[0];
            jsonObject.addProperty(colNames[i], predictionVal);
        }
        return gson.toJson(jsonObject);
    }

    // Utility functions for transformations against REST server
    private HttpURLConnection getRestRequest(String restRequest) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL cloudURL = new URL(restRequest);
            conn = (HttpURLConnection) cloudURL.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

        } catch (IOException e) {
            System.out.println("IO Exception at getRestRequest: ");
            System.out.println(e.getMessage());
            throw e;
        }
        return conn;
    }

    private String generateRestRequest(String serverIP, String serverPort, String mojoName, String inputRow) {
        String type = System.getProperty("Type", "2");;
        String restAPI = "/model?name="+mojoName+"&type="+type+"&verbose=true&row="+inputRow;
        return "http://"+serverIP+":"+serverPort+""+restAPI;
    }

    private String handleRestResponse(HttpURLConnection conn) throws IOException {
        String output = "";
        BufferedReader br = null;
        try {
            if (conn.getResponseCode() != 200) {
                return "ERROR: " + conn.getResponseMessage();
            }
            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String nextLine;
            while ((nextLine = br.readLine()) != null) {
                output = nextLine;
            }
        } catch (IOException e){
            System.out.println("IO Exception at handleRestResponse:");
            System.out.println(e.getMessage());
            throw e;
        } finally {
            br.close();
        }
        return output;
    }

    private String convertRestResponseToJson(String restResponse) {
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        HashMap<String, String> params = new Gson().fromJson(restResponse, new TypeToken<HashMap<String, Object>>() {}.getType());
        String predictions = params.get("result").replaceAll("[\"\\[\\]]", "");
        String[] predictArray = predictions.split(",");
        for (String predictChunk : predictArray) {
            String[] predObject = predictChunk.split(":");
            jsonObject.addProperty(predObject[0].trim(), predObject[1].trim());
        }
        return gson.toJson(jsonObject);
    }
}
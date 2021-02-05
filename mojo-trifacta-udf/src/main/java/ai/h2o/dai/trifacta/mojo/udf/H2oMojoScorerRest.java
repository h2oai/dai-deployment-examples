package ai.h2o.dai.trifacta.mojo.udf;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import common.ai.h2o.dai.mojo.MojoScoringInterface;
import com.trifacta.trifactaudfs.TrifactaUDF;


public class H2oMojoScorerRest implements TrifactaUDF<String> {

    private String SERVER_IP;
    private String SERVER_PORT;
    private String MOJO_NAME;
    private String OUTPUT;
    private boolean error;

    @Override
    public String exec(List<Object> inputs) throws IOException {

        if (inputs == null) {
            return null;
        }
        String inputRow = inputs.get(0).toString();
        MojoScoringInterface mojoInterface = new MojoScoringInterface();
        try {
            if (!error) OUTPUT = mojoInterface.transformRest(inputRow, MOJO_NAME, SERVER_IP, SERVER_PORT);
        } catch (Exception e) {
            System.out.format("Error during execution: \n%s", OUTPUT);
        }
        System.out.println("FINAL OUTPUT BEING RETURNED TO THE UDF");
        System.out.println(OUTPUT);
        return OUTPUT;
    }

    @SuppressWarnings("rawtypes")
    public Class[] inputSchema() {
        return new Class[]{String.class};
    }

    @Override
    public void finish() throws IOException {
    }

    @Override
    public void init(List<Object> initArgs) {
        int numArgs = initArgs.size();
        if (numArgs != 1) {
            OUTPUT = "Missing Rest Server parameters: mojoName, serverIP, serverPort";
            error = true;
        }
        else {
            String argsJson = initArgs.get(0).toString();
            HashMap<String, String> params = new Gson().fromJson(argsJson, new TypeToken<HashMap<String, Object>>() {}.getType());
            SERVER_IP = params.get("serverIP");
            SERVER_PORT = params.get("serverPort");
            MOJO_NAME = params.get("mojoName");
        }
    }
}
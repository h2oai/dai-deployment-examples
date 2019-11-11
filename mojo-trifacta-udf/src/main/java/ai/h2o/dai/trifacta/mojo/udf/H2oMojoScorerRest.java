package ai.h2o.dai.trifacta.mojo.udf;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import common.ai.h2o.dai.mojo.MojoScoringInterface;
import com.trifacta.trifactaudfs.TrifactaUDF;


public class H2oMojoScorerRest implements TrifactaUDF<String> {

    private String _SERVERIP;
    private String _SERVERPORT;
    private String _MOJONAME;
    private String _OUTPUT;
    private boolean _error;

    @Override
    public String exec(List<Object> inputs) throws IOException {

        if (inputs == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        String inputRow = inputs.get(0).toString();
        MojoScoringInterface mojoInterface = new MojoScoringInterface();
        try {
            if (!_error) _OUTPUT = mojoInterface.transformRest(inputRow, _MOJONAME, _SERVERIP, _SERVERPORT);
        } catch (Exception e) {
            System.out.println("THERE WAS AN EXCEPTION THAT GOT THROWN");
            System.out.println("PRINT OUTPUT ERROR FOR DEBUG");
            System.out.println(_OUTPUT);
        }
        System.out.println("FINAL OUTPUT BEING RETURNED TO THE UDF");
        System.out.println(_OUTPUT);
        return _OUTPUT;
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
            _OUTPUT = "Missing Rest Server parameters: mojoName, serverIP, serverPort";
            _error = true;
        }
        else {
            String argsJson = initArgs.get(0).toString();
            HashMap<String, String> params = new Gson().fromJson(argsJson, new TypeToken<HashMap<String, Object>>() {}.getType());
            _SERVERIP = params.get("serverIP");
            _SERVERPORT = params.get("serverPort");
            _MOJONAME = params.get("mojoName");
        }
    }
}
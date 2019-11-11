package ai.h2o.dai.trifacta.mojo.udf;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import common.ai.h2o.dai.mojo.MojoScoringInterface;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import com.trifacta.trifactaudfs.TrifactaUDF;
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.lic.LicenseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;


public class H2oMojoScorerLocal implements TrifactaUDF<String> {

    private MojoPipeline _MOJO;
    private MojoFrameMeta _MOJOFRAMEMETA;
    private String _DELIMITER;
    private String _OUTPUT;
    private boolean _error;

    @Override
    public String exec(List<Object> inputs) throws IOException {
        if (inputs == null) {
            return null;
        }
        String inputRow = inputs.get(0).toString();
        MojoScoringInterface mojoInterface = new MojoScoringInterface();
        if(!_error) _OUTPUT = mojoInterface.transformLocal(_MOJO, _MOJOFRAMEMETA, inputRow, _DELIMITER);
        return _OUTPUT;
    }

    @SuppressWarnings("rawtypes")
    public Class[] inputSchema() { return new Class[]{String.class}; }

    @Override
    public void finish() throws IOException {
    }

    @Override
    public void init(List<Object> initArgs) {
        try {
            if (initArgs.size() != 1) {
                _OUTPUT = "Needs 1 Arguments: Json String with keys mojo_name and delimiter";
                _error = true;
            }
            String workingDir = System.getProperty("user.dir");
            System.setProperty("ai.h2o.mojos.runtime.license.file", workingDir + "/dai/license.sig");
            String argsJson = initArgs.get(0).toString();
            HashMap<String, String> params = new Gson().fromJson(argsJson, new TypeToken<HashMap<String, Object>>() {}.getType());
            _MOJO = MojoPipeline.loadFrom(workingDir + "/" + params.get("mojo_name"));
            _MOJOFRAMEMETA = _MOJO.getInputMeta();
            _DELIMITER = params.get("delimiter");
            if (_MOJO == null || _MOJOFRAMEMETA == null || _DELIMITER == null ) {
                throw new Exception("Did Not Initialize Variables");
            }
        } catch (LicenseException e) {
            System.out.format("Could not find License for Mojo:\n%s", e.toString());
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.format("Unexpected IOException: \n%s", e.toString());
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println("Random error which I don't care about");
            System.out.println(e.getMessage());
        }
    }
}
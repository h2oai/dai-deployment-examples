package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

import com.opencsv.exceptions.CsvValidationException;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.lic.LicenseException;

@SuppressWarnings("serial")
public class AddKeyToPrediction extends RichMapFunction<String, String> {
	private MojoPipeline model;
	private final File pipelineMojoFilePath;
	private MojoFrameMeta predMojoFrameMeta;
	private String predHeaderKey;
	
	public AddKeyToPrediction(File pipelineMojoPath) {
		pipelineMojoFilePath = pipelineMojoPath;
	}

	/* 
	 * Initialization method for the function. 
	 * It is called one time before the actual working method (map) and suitable for one time setup
	 */
	@Override
	public void open(Configuration parameters) throws IOException, LicenseException {
		model = MojoPipelineService.loadPipeline(pipelineMojoFilePath);
		predMojoFrameMeta = model.getOutputMeta();
		predHeaderKey = Arrays.toString(predMojoFrameMeta.getColumnNames());
		predHeaderKey = predHeaderKey.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
	}
	
	@Override
	public String map(String inputData) throws CsvValidationException, IOException, LicenseException {
		String addKeyToString = predHeaderKey + ":" + inputData;
		System.out.println(addKeyToString);
		return addKeyToString;
	}
}

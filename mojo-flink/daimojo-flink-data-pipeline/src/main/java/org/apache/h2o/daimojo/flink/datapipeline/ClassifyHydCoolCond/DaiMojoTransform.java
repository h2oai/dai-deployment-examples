package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoColumn;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;
import ai.h2o.mojos.runtime.lic.LicenseException;

@SuppressWarnings("serial")
public class DaiMojoTransform extends RichMapFunction<String, String> {
	private MojoPipeline model;
	private final File pipelineMojoFilePath;
	private MojoFrameMeta inMojoFrameMeta;
	
	public DaiMojoTransform(File pipelineMojoPath) {
		pipelineMojoFilePath = pipelineMojoPath;
	}
	
	/* 
	 * Initialization method for the function. 
	 * It is called one time before the actual working method (map) and suitable for one time setup
	 */
	@Override
	public void open(Configuration parameters) throws IOException, LicenseException {
		model = MojoPipelineService.loadPipeline(pipelineMojoFilePath);
		inMojoFrameMeta = model.getInputMeta();
	}
	
	@Override
	public String map(String inputData) throws CsvValidationException, IOException, LicenseException {
		MojoFrame inputFrame = stringToMojoFrame(inputData);
		
		MojoFrame predFrame = predict(inputFrame);
		
		String predData = mojoFrameToString(predFrame);
		
		return predData;
	}
	
	private MojoFrame stringToMojoFrame(String inputData) throws IOException, CsvValidationException {
		MojoFrameBuilder frameBuilder = model.getInputFrameBuilder();
		MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();
		
		try (CSVReader reader = new CSVReader(new StringReader(inputData))) {
			String[] nextLine;
			if ((nextLine = reader.readNext()) != null) {
				for (int i = 0; i < nextLine.length; i++) {
					rowBuilder.setValue(inMojoFrameMeta.getColumnName(i), nextLine[i]);
				}
			}
		}
		
		frameBuilder.addRow(rowBuilder);
		return frameBuilder.toMojoFrame();
	}
	
	private MojoFrame predict(MojoFrame inputFrame) {
		return model.transform(inputFrame);
	}
	
	private String mojoFrameToString(MojoFrame outputFrame) {
		// Declare 1D String arr from out MojoFrame that only has 1 row and multi columns
		String[] data = new String[outputFrame.getNcols()];

		// Iterate over all columns in current row, store data into String data arr
		for(int colN = 0; colN < outputFrame.getNcols(); colN++) {
			MojoColumn col = outputFrame.getColumn(colN);
			String[] colDataStrArr = col.getDataAsStrings();
			data[colN] = colDataStrArr[0];
		}
		
		String rowStr = stringArrToString(data);
		
		return rowStr;
	}
	
	// Convert 1D String data array into String
	private String stringArrToString(String[] data) {
		String rowStr = Arrays.toString(data);
		return rowStr.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
	}
}

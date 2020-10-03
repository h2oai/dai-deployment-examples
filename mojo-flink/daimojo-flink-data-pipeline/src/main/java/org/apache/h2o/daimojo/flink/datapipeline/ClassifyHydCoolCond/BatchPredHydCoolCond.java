package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.client.JobExecutionException;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.lic.LicenseException;

/**
 * Flink Batch Scoring Job
 *
 * Execute an H2O.ai Driverless AI MOJO Scoring Pipeline on a batch of Hydraulic Sensor data
 * to classify for Hydraulic Cooling Condition
 */
public class BatchPredHydCoolCond {

	private static final String homePath = System.getProperty("user.home");
	private static Configuration config = new Configuration();
	
	public static void main(String[] args) throws IOException, LicenseException, JobExecutionException {
		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		
		File pathToDaiMojo = new File(homePath + "/dai-model-deployment/mojo-pipeline/pipeline.mojo");
		
		DataSet<String> hydPredHeader = getPredHeader(pathToDaiMojo, env, "Get Pred Header via DAI MOJO");
		
		String pathToHydraulicData = homePath + "/dai-model-deployment/testData/test-batch-data/example.csv";
		DataSet<String> hydraulic = getBatchData(env, pathToHydraulicData, "Get Batch Data");
		
		DataSet<String> hydHeadFiltered = filterOutHeader(true, hydraulic, "Filter Out Input Header");
		
		DataSet<String> predHydraulic = predictBatchData(hydHeadFiltered, pathToDaiMojo, "Run DAI MOJO Batch Scoring");
		
		DataSet<String> batchScores = prependPredHeader(hydPredHeader, predHydraulic, "Prepend Pred Header to Batch Scores");
		
		batchScores.printOnTaskManager("Batch Score: ").name("Print Batch Scores");
		
		executeFlinkBatchJob(env, "Deploy DAI Mojo SP within a Flink Batch ETL Pipeline");
	}
	
	/*
	 * Pull in Batch data, meaning each file has multiple rows of data
	 * Reads Text File line by line and stores it into DataSet<String> in order due to parallelism being 1
	 * If parallelism is blank, then flink local mode will set parallelism to number of cpu cores causing unsorted output
	 */
	private static DataSet<String> getBatchData(ExecutionEnvironment env, String pathToData, String operatorName) {
		return env.readTextFile(pathToData).setParallelism(1).name(operatorName);
	}
	
	// Filter out header from csv data set
	private static DataSet<String> filterOutHeader(boolean skipFirstLine, DataSet<String> dataSet, String operatorName) {
		config.setBoolean("skipFirstLineHeader", skipFirstLine);
		return dataSet.filter(new FilterHeader()).withParameters(config).name(operatorName);
	}
	
	// Perform Predictions on incoming hydraulic system data using Driverless AI MOJO Scoring Pipeline
	private static DataSet<String> predictBatchData(DataSet<String> dataSet, File pathToMojoScorer, String operatorName) {
		DaiMojoTransform daiMojoTransform = new DaiMojoTransform(pathToMojoScorer);
		return dataSet.map(daiMojoTransform).name(operatorName);
	}
	
	// Create prediction header using Collection of Strings
	private static DataSet<String> getPredHeader(File pathToMojoScorer, ExecutionEnvironment env, String operatorName) throws IOException, LicenseException {
		MojoPipeline daiMojoModel = MojoPipelineService.loadPipeline(pathToMojoScorer);
		MojoFrameMeta predMojoFrameMeta = daiMojoModel.getOutputMeta();
		String predHeader = Arrays.toString(predMojoFrameMeta.getColumnNames());
		predHeader = predHeader.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
		
		Collection<String> predHydHeader = Arrays.asList(predHeader);
		return env.fromCollection(predHydHeader).name(operatorName);
	}
	
	// Prepend predicted header predlHydLabels to DataSet predHydraulic
	private static DataSet<String> prependPredHeader(DataSet<String> predHeader, DataSet<String> predDataSet, String operatorName) {
		return predHeader.union(predDataSet).name(operatorName);
	}
	
	private static void executeFlinkBatchJob(ExecutionEnvironment env, String jobName) throws JobExecutionException {
		try {
			env.execute(jobName);
		} catch (Exception e) {
			// Throw more specific exception because env.execute() expects throws or catch Exception
			throw new JobExecutionException(null, jobName, e);
		}
	}
}

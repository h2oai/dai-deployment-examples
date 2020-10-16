package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.flink.runtime.client.JobExecutionException;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.lic.LicenseException;

/**
 * Flink Stream Scoring Job.
 * 
 * Execute an H2O.ai Driverless AI MOJO Scoring Pipeline on a stream (real-time) of Hydraulic Sensor data
 * to classify for Hydraulic Cooling Condition
 */
public class RealTimePredHydCoolCond {

	private static final String homePath = System.getProperty("user.home");
	public static void main(String[] args) throws IOException, LicenseException, JobExecutionException {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		
		File pathToDaiMojo = new File(homePath + "/dai-model-deployment/mojo-pipeline/pipeline.mojo");
		
		DataStream<String> hydPredHeader = getPredHeader(pathToDaiMojo, env, "Get Pred Header via DAI MOJO");
		
		String pathToHydraulicData = homePath + "/dai-model-deployment/testData/test-real-time-data/";
		DataStream<String>hydraulic = getRealTimeData(env, pathToHydraulicData, "Get Real-Time Data");
		
		DataStream<String> predHydraulic = predictRealTimeData(hydraulic, pathToDaiMojo, "Run DAI Mojo Real-Time Scoring");
		
		DataStream<String> streamScores = prependPredHeader(hydPredHeader, predHydraulic); // Prepend Pred Header to Real-Time Scores
		
		streamScores.print("Real-Time Score: ").name("Print Real-Time Scores");
		
		executeFlinkStreamJob(env, "Deploy DAI Mojo SP within a Flink Streaming Data Pipeline");
	}
	
	// Pulling in Real-Time data, meaning each file has an individual row of data
	private static DataStream<String> getRealTimeData(StreamExecutionEnvironment env, String pathToData, String operatorName) {
		return env.readTextFile(pathToData).name(operatorName);
	}
	
	// Perform Predictions on incoming hydraulic system data using Driverless AI MOJO Scoring Pipeline
	private static DataStream<String> predictRealTimeData(DataStream<String> dataStream, File pathToMojoScorer, String operatorName) {
		DaiMojoTransform daiMojoTransform = new DaiMojoTransform(pathToMojoScorer);
		return dataStream.map(daiMojoTransform).name(operatorName);
	}
	
	// Create prediction header using Collection of Strings
	private static DataStream<String> getPredHeader(File pathToMojoScorer, StreamExecutionEnvironment env, String operatorName) throws IOException, LicenseException {
		MojoPipeline daiMojoModel = MojoPipelineService.loadPipeline(pathToMojoScorer);
		MojoFrameMeta predMojoFrameMeta = daiMojoModel.getOutputMeta();
		String predHeader = Arrays.toString(predMojoFrameMeta.getColumnNames());
		predHeader = predHeader.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
		
		Collection<String> predHydHeader = Arrays.asList(predHeader);
		return env.fromCollection(predHydHeader).name(operatorName);
	}
	
	// Prepend predicted header hydPredHeader to DataStream predHydraulic
	private static DataStream<String> prependPredHeader(DataStream<String> predHeader, DataStream<String> predDataStream) {
		return predHeader.union(predDataStream);
	}
	
	private static void executeFlinkStreamJob(StreamExecutionEnvironment env, String jobName) throws JobExecutionException {
		try {
			env.execute(jobName);
		} catch (Exception e) {
			// Throw more specific exception because env.execute() expects throws or catch Exception
			throw new JobExecutionException(null, jobName, e);
		}
	}
}

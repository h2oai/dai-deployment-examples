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

	public static void main(String[] args) throws IOException, LicenseException, JobExecutionException {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		String homePath = System.getProperty("user.home");
		File pipelineMojoPath = new File(homePath + "/daimojo-flink/DAI-1.8.7.1/mojo-pipeline/pipeline.mojo");
		MojoPipeline daiMojoModel = MojoPipelineService.loadPipeline(pipelineMojoPath);
		
		// Pulling in Real-Time data, meaning each file has an individual row of data
		String pathToHydraulicData = homePath + "/daimojo-flink/testData/test-real-time-data/";
		DataStream<String>hydraulic = env.readTextFile(pathToHydraulicData).name("Get Real-Time Data");
		
		// Perform Predictions on incoming hydraulic system data using Driverless AI MOJO Scoring Pipeline
		// Note: The user defined MojoTransform function, which inherits from Flink's RichMapFunction is leveraged
		DataStream<String> predHydraulic = hydraulic.map(new MojoTransform(pipelineMojoPath, RealTimePredHydCoolCond.class))
				.name("Execute DAI Mojo Real-Time Scoring");
	
		// Create prediction header using Collection of Strings
		MojoFrameMeta predMojoFrameMeta = daiMojoModel.getOutputMeta();
		String predHeader = Arrays.toString(predMojoFrameMeta.getColumnNames());
		predHeader = predHeader.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
		
		Collection<String>  predHydHeader = Arrays.asList(predHeader);
		DataStream<String> predlHydLabels = env.fromCollection(predHydHeader).name("Get Predicted Labels from Mojo");
		
		// Prepend predicted header to DataStream predHydraulic
		DataStream<String> streamScores = predlHydLabels.union(predHydraulic);
		
		// predHydraulic.writeAsText(homePath + "/daimojo-flink/stream-scores/hyd-cool-cond/predHydCoolCond.csv").setParallelism(1);
		streamScores.print("Real-Time Score").name("Print Real-Time Scores");
		String jobName = "Deploy DAI Mojo SP within a Flink Streaming Data Pipeline";
		try {
			env.execute(jobName);
		} catch (Exception e) {
			// Throw more specific exception because env.execute() expects throws or catch Exception
			throw new JobExecutionException(null, jobName, e);
		}
	}
}

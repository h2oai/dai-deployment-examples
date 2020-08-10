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

	public static void main(String[] args) throws IOException, LicenseException, Exception {
		final ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		
		String homePath = System.getProperty("user.home");
		File pipelineMojoPath = new File(homePath + "/daimojo-flink/DAI-1.8.7.1/mojo-pipeline/pipeline.mojo");
		MojoPipeline daiMojoModel = MojoPipelineService.loadPipeline(pipelineMojoPath);
		
		Configuration config = new Configuration();
		config.setBoolean("skipFirstLineHeader", true);
		
		// Pull in Batch data, meaning each file has multiple rows of data
		// Reads Text File line by line and stores it into DataSet<String> in order due to parallelism being 1
		// If parallelism is blank, then flink local mode will set parallelism to number of cpu cores causing unsorted output
		String pathToHydraulicData = homePath + "/daimojo-flink/testData/test-batch-data/example.csv";
		DataSet<String> hydraulic = env.readTextFile(pathToHydraulicData).setParallelism(1).name("Get Batch Data");
		
		// Filter out header of csv data set
		DataSet<String> hydraulicFiltered = hydraulic.filter(new FilterHeader(BatchPredHydCoolCond.class))
				.withParameters(config)
				.name("Filter Out Header");
		
		// Perform Predictions on incoming hydraulic system data using Driverless AI MOJO Scoring Pipeline
		// Note: The user defined MojoTransform function, which inherits from Flink's RichMapFunction is leveraged
		DataSet<String> predHydraulic = hydraulicFiltered.map(new MojoTransform(pipelineMojoPath, BatchPredHydCoolCond.class))
				.name("Execute DAI Mojo Batch Scoring");

		// Create prediction header using Collection of Strings
		MojoFrameMeta predMojoFrameMeta = daiMojoModel.getOutputMeta();
		String predHeader = Arrays.toString(predMojoFrameMeta.getColumnNames());
		predHeader = predHeader.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s+", "");
		
		Collection<String> predHydHeader = Arrays.asList(predHeader);
		DataSet<String> predlHydLabels = env.fromCollection(predHydHeader).name("Get Predicted Labels from Mojo");
		
		// Prepend predicted header predlHydLabels to DataSet predHydraulic
		DataSet<String> batchScores = predlHydLabels.union(predHydraulic).name("Prepend Predicted Labels to Batch Data");
		
//		batchScores.writeAsText(homePath + "/daimojo-flink/batch-scores/hyd-cool-cond/predHydCoolCond.csv").setParallelism(1);
		batchScores.printOnTaskManager("Batch Score: ").name("Print Batch Scores");
		String jobName = "Deploy DAI Mojo SP within a Flink Batch ETL Pipeline";
		try {
			env.execute(jobName);
		} catch (Exception e) {
			// Throw more specific exception because env.execute() expects throws or catch Exception
			throw new JobExecutionException(null, jobName, e);
		}
	}
}

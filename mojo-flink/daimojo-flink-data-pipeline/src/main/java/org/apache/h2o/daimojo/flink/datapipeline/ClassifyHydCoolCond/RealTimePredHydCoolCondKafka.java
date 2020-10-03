package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import java.io.File;
import java.util.Properties;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.runtime.client.JobExecutionException;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;

/**
 * Flink Stream Scoring Job.
 * 
 * Execute an H2O.ai Driverless AI MOJO Scoring Pipeline on a stream (real-time) of Hydraulic Sensor data
 * to classify for Hydraulic Cooling Condition and push to a Kafka topic
 */
@SuppressWarnings("deprecation")
public class RealTimePredHydCoolCondKafka {

	private static final String homePath = System.getProperty("user.home");
	public static void main(String[] args) throws JobExecutionException {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		ParameterTool parameter = ParameterTool.fromArgs(args);
		
		File pathToDaiMojo = new File(homePath + "/dai-model-deployment/mojo-pipeline/pipeline.mojo");
		String pathToHydraulicData = homePath + "/dai-model-deployment/testData/test-real-time-data/";
		DataStream<String>hydraulic = getRealTimeData(env, pathToHydraulicData, "Get Real-Time Data");
		
		DataStream<String> predHydraulic = predictRealTimeData(hydraulic, pathToDaiMojo, "Run DAI Mojo Real-Time Scoring");	
		
		DataStream<String> streamKeyedScores = addKeyToPrediction(predHydraulic, pathToDaiMojo, "Add Key To Prediction for Kafka");
		
		String sinkTopic = parameter.getRequired("topic");
		String bootstrapServers = parameter.getRequired("bootstrap.servers");
		String zookeeperConnect = parameter.getRequired("zookeeper.connect");
		
		Properties properties = new Properties();
		properties.setProperty("bootstrap.servers", bootstrapServers);
		properties.setProperty("groupId", sinkTopic);
		properties.setProperty("zookeeper.connect", zookeeperConnect);
		properties.setProperty("parse.key", "true");
		properties.setProperty("key.separator", ":");
		
		FlinkKafkaProducer<String> flinkKafkaProducer = new FlinkKafkaProducer<String>(
				sinkTopic, new SimpleStringSchema(), properties);
		
		streamKeyedScores.addSink(flinkKafkaProducer).name("Push RT Scores to Kafka Topic");
		
		executeFlinkStreamJob(env, "Deploy DAI Mojo SP within a Flink Kafka Streaming Data Pipeline");
	}
	
	/* Pulling in Real-Time data, meaning each file has an individual row of data */
	private static DataStream<String> getRealTimeData(StreamExecutionEnvironment env, String pathToData, String operatorName) {
		return env.readTextFile(pathToData).name(operatorName);
	}
	
	/* Perform Predictions on incoming hydraulic system data using Driverless AI MOJO Scoring Pipeline */
	private static DataStream<String> predictRealTimeData(DataStream<String> dataStream, File pathToMojoScorer, String operatorName) {
		DaiMojoTransform daiMojoTransform = new DaiMojoTransform(pathToMojoScorer);
		return dataStream.map(daiMojoTransform).name(operatorName);
	}
	
	private static DataStream<String> addKeyToPrediction(DataStream<String> dataStream, File pathToMojoScorer, String operatorName) {
		AddKeyToPrediction addKeyToData = new AddKeyToPrediction(pathToMojoScorer);
		return dataStream.map(addKeyToData).name(operatorName);
	}
	
	/* Throw more specific exception because env.execute() expects throws or catch Exception */
	private static void executeFlinkStreamJob(StreamExecutionEnvironment env, String jobName) throws JobExecutionException {
		try {
			env.execute(jobName);
		} catch (Exception e) {
			throw new JobExecutionException(null, jobName, e);
		}
	}
}

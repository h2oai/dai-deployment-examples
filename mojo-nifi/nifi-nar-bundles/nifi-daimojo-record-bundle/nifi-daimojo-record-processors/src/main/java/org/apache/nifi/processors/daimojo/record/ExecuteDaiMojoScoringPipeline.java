package org.apache.nifi.processors.daimojo.record;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.RecordReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.serialization.RecordSetWriter;
import org.apache.nifi.serialization.RecordSetWriterFactory;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.WriteResult;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.util.DataTypeUtils;
import org.apache.nifi.util.StopWatch;

import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.api.MojoPipelineService;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoColumn;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;
import ai.h2o.mojos.runtime.lic.LicenseException;

@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"record", "execute", "mojo", "scoring", "predictions", "driverless ai", "h2o.ai", "machine learning"})
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@WritesAttributes({
	@WritesAttribute(attribute = "record.count", description = "The number of records in an outgoing FlowFile"),
	@WritesAttribute(attribute = "mime.type", description = "The MIME Type that the configured Record Writer indicates is appropriate"),
})
@CapabilityDescription("Executes H2O.ai's Driverless AI MOJO Scoring Pipeline using Java Mojo2 Runtime API to do batch "
		+ "scoring or real time scoring for one or more predicted label(s) on the tabular test data in "
		+ "the incoming flow file content. If tabular data is one row, then MOJO does real time scoring. "
		+ "If tabular data is multiple rows, then MOJO does batch scoring.")
@RequiresInstanceClassLoading
public class ExecuteDaiMojoScoringPipeline extends AbstractProcessor {

	static final PropertyDescriptor RECORD_READER = new PropertyDescriptor.Builder()
			.name("h2o-record-record-reader")
			.displayName("Record Reader")
			.description("Specifies the Controller Service to use for parsing incoming data and determining the data's schema.")
			.identifiesControllerService(RecordReaderFactory.class)
			.required(true)
			.build();
	
	static final PropertyDescriptor RECORD_WRITER = new PropertyDescriptor.Builder()
			.name("h2o-record-record-writer")
			.displayName("Record Writer")
			.description("Specifies the Controller Service to use for writing out the records")
			.identifiesControllerService(RecordSetWriterFactory.class)
			.required(true)
			.build();
	
	public static final PropertyDescriptor PIPELINE_MOJO_FILEPATH = new PropertyDescriptor.Builder()
			.name("h2o-record-pipeline-mojo-filepath")
			.displayName("Pipeline MOJO Filepath")
			.description("File path to a Driverless AI MOJO Scoring Pipeline 'pipeline.mojo'. This file is used to instantiate a " +
						 "MOJO Pipeline object that will be used to make predictions.")
			.required(true)
			.addValidator(StandardValidators.FILE_EXISTS_VALIDATOR)
			.expressionLanguageSupported(ExpressionLanguageScope.NONE)
			.build();
	
	public static final Relationship REL_SUCCESS = new Relationship.Builder()
			.name("success")
			.description("The FlowFile with prediction content will be routed to this relationship")
			.build();
	
	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description("If a FlowFile fails processing for any reason (for example, the FlowFile records cannot be parsed), it " +
						 "will be routed to this relationship")
			.build();
	
	public static final Relationship REL_ORIGINAL = new Relationship.Builder()
			.name("original")
			.description("The original FlowFile that was scored. If the FlowFile fails processing, nothing will be sent to this relationship")
			.build();
			
	private final static List<PropertyDescriptor> properties;
	private final static Set<Relationship> relationships;
			
	private ComponentLog logger;
	
	private MojoPipeline model;
	
	private String mojoPipelineUUID;
	
	static {
		ArrayList<PropertyDescriptor> propertiesBuilder = new ArrayList<>();
		propertiesBuilder.add(RECORD_READER);
		propertiesBuilder.add(RECORD_WRITER);
		propertiesBuilder.add(PIPELINE_MOJO_FILEPATH);
		properties = Collections.unmodifiableList(propertiesBuilder);
		
		final Set<Relationship> relationshipsBuilder = new HashSet<>();
		relationshipsBuilder.add(REL_SUCCESS);
		relationshipsBuilder.add(REL_FAILURE);
		relationshipsBuilder.add(REL_ORIGINAL);
		relationships = Collections.unmodifiableSet(relationshipsBuilder);
	}
	
	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}
	
	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}
	
	/**
	 * Gets called once when the processor is scheduled to run
	 * @param context
	 */
	@OnScheduled
	public void onScheduled(final ProcessContext context) {
		final String pipelineMojoPath = context.getProperty(PIPELINE_MOJO_FILEPATH).getValue();

			try {
				model = MojoPipelineService.loadPipeline(new File(pipelineMojoPath));
				
				mojoPipelineUUID = "pipeline.mojo uuid " + model.getUuid();
			} catch (LicenseException | IOException e) {
				getLogger().error("Unable to load MOJO from pipelineMojoPath due to " + e.toString());
			}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
		// added in onTrigger since it doesn't run fast enough inside onScheduler
		RecordSchema scoredMojoRecordSchema = convertMojoFrameMetaToRecordSchema();
		
		final FlowFile original = session.get();
		
		if(original == null) {
			return;
		}
		
		logger = getLogger();
		final StopWatch stopWatch = new StopWatch(true);
		
		final RecordReaderFactory readerFactory = context.getProperty(RECORD_READER).asControllerService(RecordReaderFactory.class);
		final RecordSetWriterFactory writerFactory = context.getProperty(RECORD_WRITER).asControllerService(RecordSetWriterFactory.class);
		
		final RecordSchema schema;
		FlowFile scored = null;
		
		try (final InputStream in = session.read(original);
			 final RecordReader reader = readerFactory.createRecordReader(original, in, logger)
			) {
			schema = writerFactory.getSchema(original.getAttributes(), reader.getSchema());
			
			final Map<String, String> attributes = new HashMap<>();
			final WriteResult writeResult;
			scored = session.create(original);
			
			Record record = reader.nextRecord();
			
			if(record == null) {
				try (final OutputStream out = session.write(scored);
					 final RecordSetWriter writer = writerFactory.createWriter(logger, schema, out, scored)
					) {
					writer.beginRecordSet();
					writeResult = writer.finishRecordSet();
						
					attributes.put("record.count", String.valueOf(writeResult.getRecordCount()));
					attributes.put(CoreAttributes.MIME_TYPE.key(), writer.getMimeType());
					attributes.putAll(writeResult.getAttributes());
				}
				scored = session.putAllAttributes(scored, attributes);
				logger.info("{} had no Records to score", new Object[]{original});
			}
			else {
				// Create the writeSchema based on the scored MojoFrame Record Schema so that if the Record Set Writer chooses
				// to Inherit Record Schema, it will inherit the Record Schema from the MojoPipeline model because the model
				// already knows the MojoFrame Meta Data (it gets converted to NiFi Record Schema)
				final RecordSchema writeSchema = writerFactory.getSchema(original.getAttributes(), scoredMojoRecordSchema);
				
				// try-with-resources block opens RecordSetWriter, writes the scoredRecords to the given output stream,
				// and closes RecordSetWriter automatically once execution leaves try-with-resources block
				// Multilevel Inheritance: RecordSetWriter <- RecordWriter <- Closeable <- AutoCloseable
				try (final OutputStream out = session.write(scored);
					 final RecordSetWriter writer = writerFactory.createWriter(logger, writeSchema, out, scored)
				    ) {
					writer.beginRecordSet();
					
					while(record != null) {
						MojoFrame mojoFrame = convertRecordToMojoFrame(record);

						final MojoFrame scoredMojoFrame = predict(mojoFrame, model);

						final Record scoredRecord = convertMojoFrameToRecord(scoredMojoFrame, logger);

						writer.write(scoredRecord);

						record = reader.nextRecord();
					}
					writeResult = writer.finishRecordSet();
					
					attributes.put("record.count", String.valueOf(writeResult.getRecordCount()));
					attributes.put(CoreAttributes.MIME_TYPE.key(), writer.getMimeType());
					attributes.putAll(writeResult.getAttributes());
				}
				
				scored = session.putAllAttributes(scored, attributes);
				session.getProvenanceReporter().modifyContent(scored, "Modified With " + mojoPipelineUUID, stopWatch.getElapsed(TimeUnit.MILLISECONDS));
				logger.debug("Scored {}", new Object[] {original});
			}
		} catch (IOException | MalformedRecordException | SchemaNotFoundException ex) {
			logger.error("Unable to score {} due to {}", new Object[] { original, ex.toString(), ex });
			session.transfer(original, REL_FAILURE);
			if (scored != null) {
				session.remove(scored);
			}
			return;
		}
		if (scored != null) {
			session.transfer(scored, REL_SUCCESS);
		}
		session.transfer(original, REL_ORIGINAL);
	}
	
	private RecordSchema convertMojoFrameMetaToRecordSchema() {
		// Need a list of record fields to create a record schema for output write schema
		List<RecordField> scoredMojoFrameFields = new ArrayList<>();

		MojoFrameMeta scoredMojoFrameMeta = model.getOutputMeta();

		for (int nCol = 0; nCol < scoredMojoFrameMeta.size(); nCol++) {
			String scoredMojoFrameColumnName = scoredMojoFrameMeta.getColumnName(nCol);
			MojoColumn.Type scoredMojoFrameColumnType = scoredMojoFrameMeta.getColumnType(nCol);

			DataType scoredColumnDataType = convertMojoTypeToRecordType(scoredMojoFrameColumnType);
			RecordField scoredMojoFrameField = new RecordField(scoredMojoFrameColumnName, scoredColumnDataType);
			scoredMojoFrameFields.add(scoredMojoFrameField);
		}
		return new SimpleRecordSchema(scoredMojoFrameFields);
	}
	
	/**
	 * Check MojoFrame Column Data Type at a index using MojoColumn.Type enum {Bool, Int32, Int64, Float32, Float64, Str}
	 * Assign NiFi Data Type based on MojoColumn.Type
	 * @param mojoType
	 * @return
	 */
	private DataType convertMojoTypeToRecordType(MojoColumn.Type mojoType) {
		DataType recordType = null;
		
		switch (mojoType) {
		case Bool:
			recordType = RecordFieldType.BOOLEAN.getDataType();
			break;
		case Int32:
			recordType = RecordFieldType.INT.getDataType();
			break;
		case Int64:
			recordType = RecordFieldType.LONG.getDataType();
			break;
		case Float32:
			recordType = RecordFieldType.FLOAT.getDataType();
			break;
		case Float64:
			recordType = RecordFieldType.DOUBLE.getDataType();
			break;
		case Str:
			recordType = RecordFieldType.STRING.getDataType();
			break;
		default:
			throw new ProcessException("Error converting Mojo Column Type to NiFi Data Type");
		}
		return recordType;
	}
	
	/**
	 * Executes the pipeline of transformers as stated in this model's Mojo file
	 * In other words, use Mojo to predict the labels from mojoFrame real world data
	 */
	private MojoFrame predict(MojoFrame mojoFrame, MojoPipeline model) {
		return model.transform(mojoFrame);
	}
	
	/**
	 * TODO: Future improvement -
	 * Create a new NiFi Controller Service API called DaiMojoFrameRecordSetWriter that writes out a 
	 * Driverless AI Mojo Frame from reading a NiFi Record
	 * @param record
	 * @return
	 */	 
	@SuppressWarnings("unchecked")
	private MojoFrame convertRecordToMojoFrame(final Record record) {
		// Convert record to hash map
		DataType dataType = RecordFieldType.RECORD.getRecordDataType(record.getSchema());
		Map<String, Object> recordMap = (Map<String, Object>) DataTypeUtils.convertRecordFieldtoObject(record, dataType);
		
		MojoFrameBuilder frameBuilder = model.getInputFrameBuilder();
		MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();
		
		for(Map.Entry<String, Object> recordEntry: recordMap.entrySet()) {
			rowBuilder.setValue(recordEntry.getKey(), String.valueOf(recordEntry.getValue()));
		}
		frameBuilder.addRow(rowBuilder);
		
		return frameBuilder.toMojoFrame();
	}
	
	/**
	 * TODO: Future improvement -
	 * Create a new NiFi Controller Service API called DaiMojoFrameRecordSetReader that reads a
	 * Driverless AI Mojo Frame from a NiFi Record
	 * @param mojoFrame
	 * @param logger
	 * @return
	 */		 
	private Record convertMojoFrameToRecord(MojoFrame mojoFrame, final ComponentLog logger) {
		// Create a hash map, then we will store the data from the MojoFrame into it
		Map<String, Object> recordMap = new HashMap<>();
		
		// Iterate through the MojoFrame and store all the data into a hash map
		// Loop on the number of columns in the first row of the MojoFrame
		for (int nCol = 0; nCol < mojoFrame.getNcols(); nCol++) {
			String key = mojoFrame.getColumnName(nCol);

			Object mojoFrameColumnData = mojoFrame.getColumnData(nCol);
			
			MojoColumn.Type mojoFrameColDataType = mojoFrame.getColumnType(nCol);
			
			Object dataEntry = getMojoFrameDataEntry(mojoFrameColDataType, mojoFrameColumnData);
			recordMap.put(key, dataEntry);
		}
		
		return DataTypeUtils.toRecord(recordMap, "r");
	}
	
	/**
	 * Check MojoFrame Column Data Type at a index using MojoColumn.Type enum {Bool, Int32, Int64, Float32, Float64, Str}
	 * Cast Object array to appropriate Data Type. There is only ever one row in the MojoFrame
	 * @param mojoType
	 * @param mojoFrameData
	 * @return
	 */
	private Object getMojoFrameDataEntry(MojoColumn.Type mojoType, Object mojoFrameData) {
		Object mojoFrameDataEntry = null;
		
		switch (mojoType) {
		case Bool:
			boolean[] columnBoolArray = (boolean[]) mojoFrameData;
			mojoFrameDataEntry = columnBoolArray[0]; // since there is only one row in a MojoFrame, we apply static 0
			break;
		case Int32:
			int[] columnIntArray = (int[]) mojoFrameData;
			mojoFrameDataEntry = columnIntArray[0];
			break;
		case Int64:
			long[] columnLongArray = (long[]) mojoFrameData;
			mojoFrameDataEntry = columnLongArray[0];
			break;
		case Float32:
			float[] columnFloatArray = (float[]) mojoFrameData;
			mojoFrameDataEntry = columnFloatArray[0];
			break;
		case Float64:
			double[] columnDoubleArray = (double[]) mojoFrameData;
			mojoFrameDataEntry = columnDoubleArray[0];
			break;
		case Str:
			String[] columnStringArray = (String[]) mojoFrameData;
			mojoFrameDataEntry = columnStringArray[0];
			break;
		default:
			throw new ProcessException("Unable to convert MojoFrame Data Object to Data Type to get Data Entry");
		}
		return mojoFrameDataEntry;
	}
}

package org.apache.nifi.processors.h2o.record;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
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
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
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

// Require using MODULES property to dynamically load in MOJO2 Runtime JAR (ex: mojo2-runtime.jar)
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.frame.MojoFrameBuilder;
import ai.h2o.mojos.runtime.frame.MojoFrameMeta;
import ai.h2o.mojos.runtime.frame.MojoColumn;
import ai.h2o.mojos.runtime.frame.MojoRowBuilder;
import ai.h2o.mojos.runtime.lic.LicenseException;

@EventDriven
@SideEffectFree
@SupportsBatching
@Tags({"record", "execute", "mojo", "scoring", "predictions", "driverless ai", "h2o", "machine learning"})
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@WritesAttributes({
	@WritesAttribute(attribute = "record.count", description = "The number of records in an outgoing FlowFile"),
	@WritesAttribute(attribute = "mime.type", description = "The MIME Type that the configured Record Writer indicates is appropriate"),
})
@CapabilityDescription("Executes H2O's Driverless AI MOJO Scoring Pipeline in Java Runtime to do batch "
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
	
	public static final PropertyDescriptor MODULE = new PropertyDescriptor.Builder()
			.name("h2o-record-custom-modules")
			.displayName("MOJO2 Runtime JAR Directory")
			.description("Path to the file or directory which contains the JAR (ex: mojo2-runtime.jar) containing modules to " 
					+ "execute the MOJO to do scoring (that are not included on NiFi's classpath)")
			.required(true)
			.expressionLanguageSupported(ExpressionLanguageScope.NONE)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.dynamicallyModifiesClasspath(true)
			.build();
	
	public static final PropertyDescriptor PIPELINE_MOJO_FILEPATH = new PropertyDescriptor.Builder()
			.name("h2o-record-pipeline-mojo-filepath")
			.displayName("Pipeline MOJO Filepath")
			.description("Path to the pipeline.mojo. This file will be used with the custom MOJO2 runtime JAR modules to instantiate MOJOPipeline object.")
			.required(true)
			.expressionLanguageSupported(ExpressionLanguageScope.NONE)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.build();
	
	public static final Relationship REL_SUCCESS = new Relationship.Builder()
			.name("success")
			.description("The FlowFile with prediction content will be routed to this relationship")
			.build();
	
	public static final Relationship REL_FAILURE = new Relationship.Builder()
			.name("failure")
			.description("If a FlowFile fails processing for any reason (for example, the FlowFile records cannot be parsed), it will be routed to this relationship")
			.build();
	
	public static final Relationship REL_ORIGINAL = new Relationship.Builder()
			.name("original")
			.description("The original FlowFile that was scored. If the FlowFile fails processing, nothing will be sent to this relationship")
			.build();
			
	private final static List<PropertyDescriptor> properties;
	private final static Set<Relationship> relationships;
			
	// Declare Mojo Pipeline model instance
	private MojoPipeline model;
	
	// Declare Mojo Pipeline model UUID
	private String mojoPipelineUUID;
	
	// Declare scored Mojo Record Schema
	private RecordSchema scoredMojoRecordSchema;
	
	static {
		ArrayList<PropertyDescriptor> _properties = new ArrayList<>();
		_properties.add(RECORD_READER);
		_properties.add(RECORD_WRITER);
		_properties.add(MODULE);
		_properties.add(PIPELINE_MOJO_FILEPATH);
		properties = Collections.unmodifiableList(_properties);
		
		final Set<Relationship> _relationships = new HashSet<>();
		_relationships.add(REL_SUCCESS);
		_relationships.add(REL_FAILURE);
		_relationships.add(REL_ORIGINAL);
		relationships = Collections.unmodifiableSet(_relationships);
	}
	
	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}
	
	@Override
	protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}
	
	@OnScheduled
	public void onScheduled(final ProcessContext context) {
		// gets called once when the processor is scheduled to run
		
		try {
			// Load Mojo Pipeline model (includes feature engineering + ML model)
			final String pipelineMojoPath = context.getProperty(PIPELINE_MOJO_FILEPATH).getValue();
			model = MojoPipeline.loadFrom(pipelineMojoPath);
			
			// Get Mojo Pipeline model UUID
			mojoPipelineUUID = "pipeline.mojo uuid " + model.getUuid();
			
			// Convert MojoFrameMeta to NiFi RecordSchema
			scoredMojoRecordSchema = convertMojoFrameMetaToRecordSchema();

		} catch (Exception e) {
			getLogger().error("Unable to load MOJO from pipelineMojoPath or " + "Unable to get MOJO Pipeline Model UUID or " +
							  "Unable to convert MojoFrameMeta to NiFi RecordSchema due to " + e.toString());
		}
	}
	
	private RecordSchema convertMojoFrameMetaToRecordSchema() {
		// Need a list of record fields to create a record schema for output write schema
		List<RecordField> scoredMojoFrameFields = new ArrayList<>();
		// Get scored Meta Data for the Output MojoFrame
		MojoFrameMeta scoredMojoFrameMeta = model.getOutputMeta();
		// NiFi DataType
		DataType scoredColumnDataType = null;

		for (int nCol = 0; nCol < scoredMojoFrameMeta.size(); nCol++) {
			String scoredMojoFrameColumnName = scoredMojoFrameMeta.getColumnName(nCol);
			MojoColumn.Type scoredMojoFrameColumnType = scoredMojoFrameMeta.getColumnType(nCol);

			// Check MojoFrame Column Data Type at a index using MojoColumn.Type enum {Bool, Int32, Int64, Float32, Float64, Str}
			// Assign NiFi Data Type based on MojoColumn.Type
			switch (scoredMojoFrameColumnType) {
			case Bool:
				scoredColumnDataType = RecordFieldType.BOOLEAN.getDataType();
				break;
			case Int32:
				scoredColumnDataType = RecordFieldType.INT.getDataType();
				break;
			case Int64:
				scoredColumnDataType = RecordFieldType.LONG.getDataType();
				break;
			case Float32:
				scoredColumnDataType = RecordFieldType.FLOAT.getDataType();
				break;
			case Float64:
				scoredColumnDataType = RecordFieldType.DOUBLE.getDataType();
				break;
			case Str:
				scoredColumnDataType = RecordFieldType.STRING.getDataType();
				break;
			default:
				getLogger().error("Mojo Column Type is not supported, so couldn't get NiFi Data Type.");
				break;
			}

			if (scoredColumnDataType == null) {
				throw new ProcessException("Error converting MojoFrame Column Data Type to NiFi Data Type");
			}

			RecordField scoredMojoFrameField = new RecordField(scoredMojoFrameColumnName, scoredColumnDataType);
			scoredMojoFrameFields.add(scoredMojoFrameField);
		}
		return new SimpleRecordSchema(scoredMojoFrameFields);
	}
	
	@Override
	protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
		final List<ValidationResult> results = new ArrayList<>(super.customValidate(validationContext));
		final String pipelineMojoPath = validationContext.getProperty(PIPELINE_MOJO_FILEPATH).isSet() ? validationContext.getProperty(PIPELINE_MOJO_FILEPATH).getValue() : null;
		
		if(pipelineMojoPath == null) {
			final String message = "A Pipeline MOJO filepath is required to instantiate MOJOPipeline object";
			results.add(new ValidationResult.Builder().valid(false)
				   .explanation(message)
				   .build());
		}
		return results;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
		final FlowFile original = session.get();
		
		if(original == null) {
			return;
		}
		
		final ComponentLog logger = getLogger();
		final StopWatch stopWatch = new StopWatch(true);
		
		final RecordReaderFactory readerFactory = context.getProperty(RECORD_READER).asControllerService(RecordReaderFactory.class);
		final RecordSetWriterFactory writerFactory = context.getProperty(RECORD_WRITER).asControllerService(RecordSetWriterFactory.class);
		
		final RecordSchema schema;
		FlowFile scored = null; // flowfile contents contains scored (predicted) data
		
		try (final InputStream in = session.read(original);
			 final RecordReader reader = readerFactory.createRecordReader(original, in, getLogger())
			) {
			schema = writerFactory.getSchema(original.getAttributes(), reader.getSchema());
			
			final Map<String, String> attributes = new HashMap<>();
			final WriteResult writeResult;
			scored = session.create(original);
			
			Record record = reader.nextRecord();
			
			if(record == null) {
				try (final OutputStream out = session.write(scored);
					 final RecordSetWriter writer = writerFactory.createWriter(getLogger(), schema, out, scored)
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
					 final RecordSetWriter writer = writerFactory.createWriter(getLogger(), writeSchema, out, scored)
				    ) {
					writer.beginRecordSet();
					
					while(record != null) {
						// Convert input Record to MojoFrame
						MojoFrame mojoFrame = convertRecordToMojoFrame(record);
						// Execute prediction on first mojo frame using Driverless AI MOJO Scoring Pipeline "model"
						final MojoFrame scoredMojoFrame = predict(mojoFrame, model);
						// Convert scored MojoFrame to Record
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
		} catch (final Exception ex) {
			logger.error("Unable to score {} due to {}", new Object[]{original, ex.toString(), ex});
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
	
	private MojoFrame predict(MojoFrame mojoFrame, MojoPipeline model) {
		// Executes the pipeline of transformers as stated in this model's Mojo file
		// In other words, use Mojo to predict the labels from iframe real world data
		MojoFrame oframe = model.transform(mojoFrame);
		
		return oframe;
	}
	
	// TODO: Future improvement -
	// 		 Create a new NiFi Controller Service API called DaiMojoFrameRecordSetWriter that writes out a 
	//		 Driverless AI Mojo Frame from reading a NiFi Record
	@SuppressWarnings("unchecked")
	private MojoFrame convertRecordToMojoFrame(final Record record) {
		// Convert record to hash map
		Map<String, Object> recordMap = (Map<String, Object>) DataTypeUtils.convertRecordFieldtoObject(record, RecordFieldType.RECORD.getRecordDataType(record.getSchema()));
		
		// Get an instance of a MojoFrameBuilder that will be used to make a MojoFrame
		MojoFrameBuilder frameBuilder = model.getInputFrameBuilder();
		// Get an instance of a MojoRowBuilder that will be used to construct a row for this builder
		MojoRowBuilder rowBuilder = frameBuilder.getMojoRowBuilder();
		
		for(Map.Entry<String, Object> recordEntry: recordMap.entrySet()) {
			// Set a value to the position associated with the column name in the row
			rowBuilder.setValue(recordEntry.getKey(), String.valueOf(recordEntry.getValue()));
		}
		// Append a row from the current state of the rowBuilder
		frameBuilder.addRow(rowBuilder);
		
		// Construct a MojoFrame
		MojoFrame iframe = frameBuilder.toMojoFrame();
		
		return iframe;
	}
	
	// TODO: Future improvement -
	//		 Create a new NiFi Controller Service API called DaiMojoFrameRecordSetReader that reads a
	//		 Driverless AI Mojo Frame from a NiFi Record
	private Record convertMojoFrameToRecord(MojoFrame mojoFrame, final ComponentLog logger) {
		// Create a hash map, then we will store the data from the MojoFrame into it
		Map<String, Object> recordMap = new HashMap<>();
		
		// There is only one row in the MojoFrame
		int firstRow = 0;
		
		// Iterate through the MojoFrame and store all the data into a hash map
		// Loop on the number of columns in the first row of the MojoFrame
		for (int nCol = 0; nCol < mojoFrame.getNcols(); nCol++) {
			// Get the name of a column at a particular index getColumnName(int index)
			String key = mojoFrame.getColumnName(nCol);

			// Get the data stored in the column at a certain index getColumnData(int index)
			Object mojoFrameColumnData = mojoFrame.getColumnData(nCol);
			
			MojoColumn.Type mojoFrameColDataType = mojoFrame.getColumnType(nCol);
			
			// Check MojoFrame Column Data Type at a index using MojoColumn.Type enum {Bool, Int32, Int64, Float32, Float64, Str}
			// Cast Object array to appropriate Data Type
			switch (mojoFrameColDataType) {
			case Bool:
				boolean[] columnBoolArray = (boolean[]) mojoFrameColumnData;
				recordMap.put(key, columnBoolArray[firstRow]);
				break;
			case Int32:
				int[] columnIntArray = (int[]) mojoFrameColumnData;
				recordMap.put(key, columnIntArray[firstRow]);
				break;
			case Int64:
				long[] columnLongArray = (long[]) mojoFrameColumnData;
				recordMap.put(key, columnLongArray[firstRow]);
				break;
			case Float32:
				float[] columnFloatArray = (float[]) mojoFrameColumnData;
				recordMap.put(key, columnFloatArray[firstRow]);
				break;
			case Float64:
				double[] columnDoubleArray = (double[]) mojoFrameColumnData;
				recordMap.put(key, columnDoubleArray[firstRow]);
				break;
			case Str:
				String[] columnStringArray = (String[]) mojoFrameColumnData;
				recordMap.put(key, columnStringArray[firstRow]);
				break;
			default:
				logger.error("Mojo Column Type is not supported.");
				break;
			}
		}
		final Record predictedRecord = DataTypeUtils.toRecord(recordMap, "r");
		return predictedRecord;
	}
}

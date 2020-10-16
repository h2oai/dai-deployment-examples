package org.apache.h2o.daimojo.flink.datapipeline.ClassifyHydCoolCond;

import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("serial")
public class FilterHeader extends RichFilterFunction<String> {
	private final Logger logger;
	private boolean skipFirstLineHeader;

	public FilterHeader() {
		logger = LogManager.getLogger();
	}
	/* Initialization method for the function.
	 * It is called one time before the actual working method (filter) and suitable for one time setup
	 */
	@Override
	public void open(Configuration parameters) {
		ConfigOption<Boolean> configOption = ConfigOptions.key("skipFirstLineHeader").booleanType().defaultValue(false);
		skipFirstLineHeader = parameters.getBoolean(configOption);
	}

	/* Filter out the first line (header) of the csv data
	 * Evaluates a boolean function for each element and retains those for which the function returns true
	 */ 
	public boolean filter(String inputData) {
		if (skipFirstLineHeader) {
			logger.warn("has HEADER, FILTER it OUT");
			skipFirstLineHeader = false;
			return false;
		}
		
		return true;
	}
	
}

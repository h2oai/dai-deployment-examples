#!/usr/bin/env python

import os
import sys
import codecs
import pandas as pd
import datatable as dt
sys.path.append(os.environ['HOME'] + "/nifi-minifi-cpp-0.7.0/modules/")
from DaiPythonScorer import *

scorer = None

def describe(processor):
    """ describe what this processor does
    """
    processor.setDescription("Executes H2O's Python Scoring Pipeline to do batch \
        scoring for one or more predicted label(s) on the tabular test data in the \
        incoming flow file content.")

def onInitialize(processor):
    """ onInitialize is where you can set properties
    """
    processor.setSupportsDynamicProperties()

def onSchedule(context):
    """ onSchedule is where you load and read properties
        this function is called when the processor is scheduled to run
    """
    # instantiate H2O's python scoring pipeline scorer
    global scorer
    scorer = Scorer()

class ContentExtract(object):
    """ ContentExtract callback class is defined for reading streams of data through the session
        and has a process function that accepts the input stream
    """
    def __init__(self):
        self.content = None
    
    def process(self, input_stream):
        """ Use codecs getReader to read that data
        """
        self.content = codecs.getreader('utf-8')(input_stream).read()
        return len(self.content)

class ContentWrite(object):
    """ ContentWrite callback class is defined for writing streams of data through the session
    """
    def __init__(self, data):
        self.content = data

    def process(self, output_stream):
        """ Use codecs getWriter to write data encoded to the stream
        """
        codecs.getwriter('utf-8')(output_stream).write(self.content)
        return len(self.content)

def onTrigger(context, session):
    """ onTrigger is executed and passed processor context and session
    """
    global scorer
    flow_file = session.get()
    if flow_file is not None:
        read_cb = ContentExtract()
        # read flow file tabular data content into read_cb.content data member
        session.read(flow_file, read_cb)
        # load tabular data str into datatable
        test_dt_frame = dt.Frame(read_cb.content)
        # do batch scoring on test data in datatable frame, return pandas df with predicted labels
        batch_scores_df = scorer.score_batch(test_dt_frame)
        # convert df to str without df index, then write to flow file
        batch_scores_df_str = batch_scores_df.to_string(index=False)
        write_cb = ContentWrite(batch_scores_df_str)
        session.write(flow_file, write_cb)
        # add flow file attribute: number of rows in the frame to know how many rows were scored
        flow_file.addAttribute("num_rows_scored", str(test_dt_frame.nrows))
        # add flow file attribute: the score of the first row in the frame to see it's pred label
        pred_header = batch_scores_df.columns
        for i in range(len(pred_header)):
            ff_attr_name = pred_header[i] + "_pred_0"
            flow_file.addAttribute(ff_attr_name, str(batch_scores_df.at[0,pred_header[i]]))
            log.info("getAttribute({}): {}".format(ff_attr_name, flow_file.getAttribute(ff_attr_name)))
        session.transfer(flow_file, REL_SUCCESS)

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
    processor.setDescription("Executes H2O's Python Scoring Pipeline to do real time scoring for \
        one or more predicted label(s) on the list test data in the incoming flow file content.")

def onInitialize(processor):
    """ onInitialize is where you can set properties
    """
    processor.addProperty("Predicted Label(s)", "Add One or more predicted label names for the prediction \
        header. If there is only one predicted label name, then write it in directly. If there is more than \
        one predicted label name, then write a comma separated list of predicted label names.", "", True, False)

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
        # read test data of flow file content into read_cb.content
        read_cb = ContentExtract()
        session.read(flow_file, read_cb)
        # get predicted label(s) for prediction header: comma separated labels if more than one
        pred_header_str = context.getProperty("Predicted Label(s)")
        pred_header_list = pred_header_str.split(",")
        # load tabular data str into datatable, convert to pd df, then to list of lists
        test_dt_frame = dt.Frame(read_cb.content)
        test_pd_df = test_dt_frame.to_pandas()
        # grab first list since there is only 1 list in the list of lists 
        test_list = test_pd_df.values.tolist()[0]
        log.info("test_list = {}".format(test_list))
        log.info("len(test_list) = {}".format(len(test_list)))
        # do real time scoring on test data in the list, return list with predicted label(s)
        preds_list = scorer.score(test_list)
        # convert pred list to a comma-separated string followed by \n for line end
        preds_list_str = ','.join(map(str, preds_list)) + '\n'
        # concatenate prediction header and list string to pred table string
        preds_str = pred_header_str + '\n' + preds_list_str
        write_cb = ContentWrite(preds_str)
        session.write(flow_file, write_cb)
        # add flow file attribute: number of lists to know how many lists were scored
        flow_file.addAttribute("num_lists_scored", str(len(test_list)))
        # add one or more flow file attributes: predicted label name and associated score pair
        for i in range(len(pred_header_list)):
            ff_attr_name = pred_header_list[i] + "_pred"
            flow_file.addAttribute(ff_attr_name, str(preds_list[i]))
            log.info("getAttribute({}): {}".format(ff_attr_name, flow_file.getAttribute(ff_attr_name)))
        session.transfer(flow_file, REL_SUCCESS)

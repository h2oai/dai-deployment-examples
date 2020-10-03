#!/usr/bin/env python

import codecs
import pandas as pd
import datatable as dt
from collections import Counter
from scipy.special._ufuncs import expit
import daimojo.model

mojo_sp = None


def describe(processor):
    """describe what this processor does"""
    processor.setDescription(
        "Executes H2O's MOJO Scoring Pipeline in C++ Runtime Python Wrapper \
        to do batch scoring or real time scoring for one or more predicted label(s) on the tabular \
        test data in the incoming flow file content. If tabular data is one row, then MOJO does \
        real time scoring. If tabular data is multiple rows, then MOJO does batch scoring."
    )


def onInitialize(processor):
    """onInitialize is where you can set properties"""
    processor.addProperty(
        "MOJO Pipeline Filepath",
        "Add the filepath to the MOJO pipeline file. For example, \
        'path/to/mojo-pipeline/pipeline.mojo'.",
        "",
        True,
        False,
    )


def loadMojoPipeline(context):
    """instantiate Driverless AI MOJO Scoring Pipeline for mojo_sp object"""
    global mojo_sp
    mojo_pipeline_filepath = context.getProperty("MOJO Pipeline Filepath")
    mojo_sp = daimojo.model(mojo_pipeline_filepath)


def onSchedule(context):
    """onSchedule is where you load and read properties
    this function is called when the processor is scheduled to run
    """
    loadMojoPipeline(context)


class ReadInputStream(object):
    """ReadInputStream callback class is defined for reading streams of data through the session
    and has a process function that accepts the input stream
    """

    def __init__(self):
        self.content = None

    def process(self, input_stream):
        """Use codecs getReader to read that data"""
        self.content = codecs.getreader("utf-8")(input_stream).read()
        return len(self.content)


class WriteOutputStream(object):
    """ContentWrite callback class is defined for writing streams of data through the session"""

    def __init__(self, data):
        self.content = data

    def process(self, output_stream):
        """Use codecs getWriter to write data encoded to the stream"""
        codecs.getwriter("utf-8")(output_stream).write(self.content)
        return len(self.content)


def addFlowFileAttributes(flow_file, preds_dt_frame, preds_df):
    """Adds Flow File attributes for DAI mojo creation time, mojo uuid, number of rows scored
    and one or more pair of predicted label names and each associated score
    """
    flow_file.addAttribute("mojo_creation_time", mojo_sp.created_time)
    flow_file.addAttribute("mojo_uuid", mojo_sp.uuid)
    pred_header = mojo_sp.output_names
    flow_file.addAttribute("num_rows_scored", str(preds_dt_frame.nrows))
    # add one or more flow file attributes: pair of predicted label names and each associated score
    for header in pred_header:
        ff_attr_name = header + "_pred_0"
        flow_file.addAttribute(ff_attr_name, str(preds_df.at[0, header]))
        log.info(
            "getAttribute({}): {}".format(
                ff_attr_name, flow_file.getAttribute(ff_attr_name)
            )
        )

def writePredictionsToFlowFile(session, preds_df_str, flow_file):
    """Writes streams of data to each flow file through session write() using the predictions
    'preds_df_str' passed into write_cb
    """
    write_cb = WriteOutputStream(preds_df_str)
    session.write(flow_file, write_cb)


def convertDatatableToPandasStr(preds_dt_frame):
    """Converts the datatable of predictions to a pandas dataframe and returns the pandas dataframe
    and its string representation without the dataframe index
    """
    preds_df = preds_dt_frame.to_pandas()
    return preds_df, preds_df.to_string(index=False)


def compare(header, exp_header):
    """Returns whether header (datatable column names) equals expected header (mojo_sp feature names)"""
    return Counter(header) == Counter(exp_header)


def loadFlowFileIntoDt(session, flow_file):
    """Reads streams of data from each flow file through session read() into read_cb's content
    and loads that content of str data of 1 or more rows into a datatable frame
    """
    read_cb = ReadInputStream()
    session.read(flow_file, read_cb)
    return dt.Frame(read_cb.content)


def onTrigger(context, session):
    """onTrigger is executed and passed processor context and session"""
    flow_file = session.get()
    if flow_file is None:
        return

    if mojo_sp is None:
        loadMojoPipeline(context)

    dt_frame = loadFlowFileIntoDt(session, flow_file)
    if compare(dt_frame.names, mojo_sp.feature_names) == False:
        dt_frame.names = tuple(mojo_sp.feature_names)
    preds_dt_frame = mojo_sp.predict(dt_frame)
    preds_df, preds_df_str = convertDatatableToPandasStr(preds_dt_frame)
    writePredictionsToFlowFile(session, preds_df_str, flow_file)
    addFlowFileAttributes(flow_file, preds_dt_frame, preds_df)
    session.transfer(flow_file, REL_SUCCESS)

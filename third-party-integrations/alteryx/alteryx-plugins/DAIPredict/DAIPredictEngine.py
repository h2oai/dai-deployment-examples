import AlteryxPythonSDK as Sdk
import xml.etree.ElementTree as Et
import h2oai_client
import csv
from pathlib import Path
import os
import pandas as pd


class AyxPlugin:
    """
    Implements the plugin interface methods, to be utilized by the Alteryx engine to communicate with a plugin.
    Prefixed with "pi", the Alteryx engine will expect the below five interface methods to be defined.
    """

    def __init__(self, n_tool_id: int, alteryx_engine: object, output_anchor_mgr: object):
        """
        Constructor is called whenever the Alteryx engine wants to instantiate an instance of this plugin.
        :param n_tool_id: The assigned unique identification for a tool instance.
        :param alteryx_engine: Provides an interface into the Alteryx engine.
        :param output_anchor_mgr: A helper that wraps the outgoing connections for a plugin.
        """

        # Default properties
        self.n_tool_id = n_tool_id
        self.alteryx_engine = alteryx_engine
        self.output_anchor_mgr = output_anchor_mgr

        self.is_valid = False
        self.str_file_path = self.alteryx_engine.create_temp_file_name('predict_tmp', 0) + '.csv'
        # Custom properties
        self.is_initialized = True
        self.key = None
        self.data_done = None
        self.model_done = None
        self.single_input = None
        self.output_anchor = None
        self.output_model = None
        self.starting_value = None
        self.total_record_count = None
        self.record_increment = None
        self.record_info_out = None
        self.model_id = None

    def pi_init(self, str_xml: str):
        """
        Handles configuration based on the GUI.
        Called when the Alteryx engine is ready to provide the tool configuration from the GUI.
        :param str_xml: The raw XML from the GUI.
        """
        # Getting the dataName data property from the Gui.html
        #self.output_field = Et.fromstring(str_xml).find('OutputField').text if 'OutputField' in str_xml else None
        self.interpretability = int(Et.fromstring(str_xml).find('Interpretability').text) if 'Interpretability' in str_xml else None
        self.model_id = Et.fromstring(str_xml).find('ModelID').text if 'ModelID' in str_xml else None
        self.use_https = Et.fromstring(str_xml).find('UseHttps').text == "True" if 'UseHttps' in str_xml else False
        verify = False if self.use_https else True

        # Get connection information
        base = Et.fromstring(str_xml)
        addr = base.find('Address').text if 'Address' in str_xml else 'http://prerelease.h2o.ai'
        username = base.find('Username').text if 'Username' in str_xml else 'h2oai'
        password = self.alteryx_engine.decrypt_password(base.find('Password').text, 0) if 'Password' in str_xml else 'h2oai'
        self.dai = h2oai_client.Client(address=addr, username=username, password=password, verify=verify)

        # Valid target name checks.
        error_msg = self.msg_str(self.str_file_path)
        if error_msg != '':
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg(error_msg))
        else:
            self.is_valid = True

        self.output_anchor = self.output_anchor_mgr.get_output_anchor('Data')

    def pi_add_incoming_connection(self, str_type: str, str_name: str) -> object:
        """
        The IncomingInterface objects are instantiated here, one object per incoming connection.
        Called when the Alteryx engine is attempting to add an incoming data connection.
        :param str_type: The name of the input connection anchor, defined in the Config.xml file.
        :param str_name: The name of the wire, defined by the workflow author.
        :return: The IncomingInterface object(s).
        """

        if str_type == 'Model':
            self.model_input = IncomingInterface(self, True)
            return self.model_input
        else:
            self.data_input = IncomingInterface(self, False)
            return self.data_input

    def pi_add_outgoing_connection(self, str_name: str) -> bool:
        """
        Called when the Alteryx engine is attempting to add an outgoing data connection.
        :param str_name: The name of the output connection anchor, defined in the Config.xml file.
        :return: True signifies that the connection is accepted.
        """

        return True


    def pi_push_all_records(self, n_record_limit: int) -> bool:
        """
        Called when a tool has no incoming data connection.
        :param n_record_limit: Set it to <0 for no limit, 0 for no records, and >0 to specify the number of records.
        :return: True for success, False for failure.
        """

        self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg('Missing Incoming Connection'))
        return False

    def check_done(self):

        if self.data_done is None:
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg('Data input is required for prediction'))
            return False
        if not self.data_done:
            return False
        
        if self.model_done is None:
            self.key = self.model_id
        elif not self.model_done:
            return False

        if self.key is None:
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg(
                'Model ID must be provided from GUI input or passed from a preceding experiment'))
        p = Path(self.str_file_path)
        if not p.is_file():
            return False

        dat = self.dai.upload_dataset_sync(p)

        predict = self.dai.make_prediction_sync(self.key, dat.key, False, False)
        p_path = self.dai.download(predict.predictions_csv_path, os.path.dirname(self.alteryx_engine.create_temp_file_name('dai_predict', 0)))
        out_p = pd.read_csv(p_path)
        #if self.parent.output_field is not None:
        for col in out_p.columns:
            self.record_info_out.add_field(col, Sdk.FieldType.float)
        self.output_anchor.init(self.record_info_out)
        self.record_creator = self.record_info_out.construct_record_creator()
        out_d = pd.read_csv(self.str_file_path)
        df = pd.concat([out_d.reset_index(drop=True), out_p.reset_index(drop=True)], axis=1)

        for index, row in df.iterrows():
            for col in df.columns:
                self.record_info_out.get_field_by_name(col).set_from_string(self.record_creator, str(row[col]))
            outrec = self.record_creator.finalize_record()
            self.output_anchor.push_record(outrec, False)
            self.record_creator.reset()
        self.output_anchor.update_progress(1.0)
        self.output_anchor.close()
        os.remove(p)

    def pi_close(self, b_has_errors: bool):
        """
        Called after all records have been processed.
        :param b_has_errors: Set to true to not do the final processing.
        """
        pass
        # Checks whether connections were properly closed.
        #self.output_anchor.assert_close()

    def display_error_msg(self, msg_string: str):
        """
        A non-interface method, that is responsible for displaying the relevant error message in Designer.
        :param msg_string: The custom error message.
        """

        self.is_initialized = False
        self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, msg_string)

    def xmsg(self, msg_string: str) -> str:
        """
        A non-interface, non-operational placeholder for the eventual localization of predefined user-facing strings.
        :param msg_string: The user-facing string.
        :return: msg_string
        """

        return msg_string

    @staticmethod
    def write_lists_to_csv(file_path: str, field_lists: list):
        """
        A non-interface, helper function that handles writing to csv and clearing the list elements.
        :param file_path: The file path and file name input by user.
        :param field_lists: The data for all fields.
        """

        with open(file_path, 'a', encoding='utf-8', newline='') as output_file:
            csv.writer(output_file, delimiter=',').writerows(zip(*field_lists))
        for sublist in field_lists:
            del sublist[:]

    @staticmethod
    def msg_str(file_path: str):
        """
        A non-interface, helper function that handles validating the file path input.
        :param file_path: The file path and file name input by user.
        :return: The chosen message string.
        """

        msg_str = ''
        if os.access(file_path, os.F_OK):
            msg_str = file_path + ' already exists. Enter a different path.'
        elif len(file_path) > 259:
            msg_str = 'Maximum path length is 259'
        elif any((char in set('/;?*"<>|')) for char in file_path):
            msg_str = 'These characters are not allowed in the filename: /;?*"<>|'
        elif len(file_path) == 0:
            msg_str = 'Enter a filename'
        elif not file_path.endswith('.csv'):
            msg_str = 'File extension must be .csv'
        return msg_str


class IncomingInterface:
    """
    This class is returned by pi_add_incoming_connection, and it implements the incoming interface methods, to be\
    utilized by the Alteryx engine to communicate with a plugin when processing an incoming connection.
    Prefixed with "ii", the Alteryx engine will expect the below four interface methods to be defined.
    """

    def __init__(self, parent: object, is_model: bool):
        """
        Constructor for IncomingInterface.
        :param parent: AyxPlugin
        """

        # Default properties
        self.parent = parent
        self.is_model = is_model

        if is_model:
            self.parent.model_done = False
        else:
            self.parent.data_done = False

        # Custom properties
        self.record_copier = None
        self.record_creator = None
        self.field_lists = []
        self.counter = 0
        self.record_info_in = None
        self.record_info_out = None

    def ii_init(self, record_info_in: object) -> bool:
        """
        Handles appending the new field to the incoming data.
        Called to report changes of the incoming connection's record metadata to the Alteryx engine.
        :param record_info_in: A RecordInfo object for the incoming connection's fields.
        :return: False if there's an error with the field name, otherwise True.
        """

        if not self.parent.is_initialized:
            return False

        self.record_info_in = record_info_in  # For later reference.
        if self.is_model:
            return True

        # Storing the field names to use when writing data out.
        for field in range(record_info_in.num_fields):
            self.field_lists.append([record_info_in[field].name])

        # Returns a new, empty RecordCreator object that is identical to record_info_in.
        self.record_info_out = record_info_in.clone()
        self.parent.record_info_out = self.record_info_out

        # Instantiate a new instance of the RecordCopier class.
        self.record_copier = Sdk.RecordCopier(self.record_info_out, record_info_in)
        self.parent.record_copier = self.record_copier
        # Map each column of the input to where we want in the output.
        for index in range(record_info_in.num_fields):
            # Adding a field index mapping.
            self.record_copier.add(index, index)

        # Let record copier know that all field mappings have been added.
        self.record_copier.done_adding()

        return True

    def ii_push_record(self, in_record: object) -> bool:
        """
        Responsible for writing the data to csv in chunks.
        Called when an input record is being sent to the plugin.
        :param in_record: The data for the incoming record.
        :return: False if file path string is invalid, otherwise True.
        """

        if self.is_model:
            index = self.record_info_in.get_field_num("Model")
            if index == -1:
                self.display_error_msg(self.alteryx_engine.xmsg('Model input requires the key be stored in field "Model"'))
                return False
            self.parent.key = self.record_info_in[index].get_as_string(in_record)
            return True

        self.counter += 1  # To keep track for chunking



        if not self.parent.is_valid:
            return False

        # Storing the string data of in_record
        for field in range(self.record_info_in.num_fields):
            in_value = self.record_info_in[field].get_as_string(in_record)
            self.field_lists[field].append(in_value) if in_value is not None else self.field_lists[field].append('')

        # Writing when chunk mark is met, then resetting counter.
        if self.counter == 100000:
            self.parent.write_lists_to_csv(self.parent.str_file_path, self.field_lists)
            self.counter = 0
        return True


    def ii_update_progress(self, d_percent: float):
        """
        Called by the upstream tool to report what percentage of records have been pushed.
        :param d_percent: Value between 0.0 and 1.0.
        """

        # Inform the Alteryx engine of the tool's progress.
        self.parent.alteryx_engine.output_tool_progress(self.parent.n_tool_id, d_percent)


    def ii_close(self):
        """
        Called when the incoming connection has finished passing all of its records.
        """
        if self.is_model:
            self.parent.model_done = True
            self.parent.check_done()
            return True
        else:
            pass           

        if len(self.field_lists) > 0 and len(self.field_lists[0]) > 1:
            self.parent.write_lists_to_csv(self.parent.str_file_path, self.field_lists)

        self.parent.data_done = True
        self.parent.check_done()
        return True



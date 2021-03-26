import csv
import os
import webbrowser
import xml.etree.ElementTree as Et
from pathlib import Path
from time import sleep

import AlteryxPythonSDK as Sdk
import h2oai_client


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
        self.str_file_path = self.alteryx_engine.create_temp_file_name('daiplugin', 0) + '.csv'
        # Custom properties
        self.is_initialized = True
        self.output_dataset = None
        self.starting_value = None
        self.total_record_count = None
        self.record_increment = None

    def pi_init(self, str_xml: str):
        """
        Handles configuration based on the GUI.
        Called when the Alteryx engine is ready to provide the tool configuration from the GUI.
        :param str_xml: The raw XML from the GUI.
        """
        # Getting the dataName data property from the Gui.html
        self.data_source = Et.fromstring(str_xml).find('DataImportSource').text if 'DataImportSource' in str_xml else None
        self.data_location = Et.fromstring(str_xml).find('DataLocation').text if 'DataLocation' in str_xml else None
        self.do_auto_viz = Et.fromstring(str_xml).find('DoAutoViz').text == "True" if 'DoAutoViz' in str_xml else False
        self.display_browser = Et.fromstring(str_xml).find('DisplayBrowser').text == "True" if 'DisplayBrowser' in str_xml else False
        self.use_https = Et.fromstring(str_xml).find('UseHttps').text == "True" if 'UseHttps' in str_xml else False
        verify = False if self.use_https else True

        # Get connection information
        base = Et.fromstring(str_xml)
        self.address = base.find('Address').text if 'Address' in str_xml else 'http://prerelease.h2o.ai'
        use_oauth = base.find('UseOAuth').text == "True" if 'UseOAuth' in str_xml else False

        if use_oauth:
            endpoint_url = base.find('EndpointURL').text \
                if 'EndpointURL' in str_xml else 'No Endpoint URL Provided'
            introspection_url = base.find('IntrospectionURL').text \
                if 'IntrospectionURL' in str_xml else 'No Introspection URL Provided'
            client_id = base.find('ClientID').text \
                if 'ClientID' in str_xml else 'No Client ID Provided'
            refresh_token = self.alteryx_engine.decrypt_password(base.find('RefreshToken').text, 0) \
                if 'RefreshToken' in str_xml else 'No Token Provided'
            token_provider = h2oai_client.OAuth2tokenProvider(
                refresh_token=refresh_token,
                client_id=client_id,
                token_endpoint_url=endpoint_url,
                token_introspection_url=introspection_url
            )
            self.dai = h2oai_client.Client(
                address=self.address,
                token_provider=token_provider.ensure_fresh_token,
                verify=verify,
            )
        else:
            username = base.find('Username').text if 'Username' in str_xml else 'h2oai'
            password = self.alteryx_engine.decrypt_password(base.find('Password').text, 0) \
                if 'Password' in str_xml else 'h2oai'
            self.dai = h2oai_client.Client(address=self.address, username=username, password=password, verify=verify)

        # Valid target name checks.
        if self.data_source is None:
            self.display_error_msg(self.alteryx_engine.xmsg('Data Source must be specified. Please select a data source.'))
        elif self.data_location is None and self.data_source != "upload":
            self.display_error_msg(self.alteryx_engine.xmsg('Data Location must be specified. Please enter a data location'))

        error_msg = self.msg_str(self.str_file_path)

        if error_msg != '':
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg(error_msg))
        else:
            self.is_valid = True

        self.output_dataset = self.output_anchor_mgr.get_output_anchor('Dataset')

    def pi_add_incoming_connection(self, str_type: str, str_name: str) -> object:
        """
        The IncomingInterface objects are instantiated here, one object per incoming connection.
        Called when the Alteryx engine is attempting to add an incoming data connection.
        :param str_type: The name of the input connection anchor, defined in the Config.xml file.
        :param str_name: The name of the wire, defined by the workflow author.
        :return: The IncomingInterface object(s).
        """

        if self.data_source == "upload":
            return IncomingInterface(self)
        else:
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error,
                                               self.xmsg('Missing Incoming Connection'))
            return False

    def pi_add_outgoing_connection(self, str_name: str) -> bool:
        """
        Called when the Alteryx engine is attempting to add an outgoing data connection.
        :param str_name: The name of the output connection anchor, defined in the Config.xml file.
        :return: True signifies that the connection is accepted.
        """

        return True

    def pi_push_all_records(self, n_record_limit: str) -> bool:
        """
        Called when a tool has no incoming data connection.
        :param n_record_limit: Set it to <0 for no limit, 0 for no records, and >0 to specify the number of records.
        :return: True for success, False for failure.
        """
        if self.data_source == "upload":
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error,
                                               self.xmsg('Missing Incoming Connection'))
            return False
        else:
            if self.data_source == "azure":
                dataset = self.dai.create_dataset_from_azure_blob_store_sync(self.data_location)
            elif self.data_source == "s3":
                dataset = self.dai.create_dataset_from_s3_sync(self.data_location)
            elif self.data_source == "gcs":
                dataset = self.dai.create_dataset_from_gcs_sync(self.data_location)
            else:
                dataset = self.dai.create_dataset_sync(self.data_location)
            # Output M
            # handle or way to reference datasets for additional use
            auto_vis = self.run_auto_viz(dataset)
            mod_record_info = Sdk.RecordInfo(self.alteryx_engine)
            mod_record_info.add_field('Dataset', Sdk.FieldType.string, 255)
            self.output_dataset.init(mod_record_info)
            mod_creator = mod_record_info.construct_record_creator()
            mod_record_info.get_field_by_name('Dataset').set_from_string(mod_creator, dataset.key)
            outmod = mod_creator.finalize_record()
            self.output_dataset.push_record(outmod, False)
            # self.output_dataset.update_progress(1.0)
            self.output_dataset.close()
            return True

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

    def run_auto_viz(self, dataset: object):
        """
        A non-interface helper function to wrap autoviz decision making ad functionality
        :param dataset: Dataset Object as returned from self.dai.create_dataset_sync...
        :return: True if success or False if fail
        """
        try:
            if self.do_auto_viz:
                auto_viz = self.dai.get_autoviz(dataset.key, 100)
                auto_viz_running = True
                while auto_viz_running:
                    auto_viz_job = self.dai.get_autoviz_job(auto_viz)
                    if auto_viz_job.progress == 1:
                        auto_viz_running = False
                    else:
                        self.output_dataset.update_progress(auto_viz_job.progress)
                        sleep(1)
                if self.display_browser:
                    url = self.address + "/#auto_viz?datasetName={}&datasetKey={}".format(dataset.name, dataset.key)
                    chrome_path = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe %s'
                    webbrowser.get(chrome_path).open(url, new=2)
            return True
        except Exception as e:
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, e)
            return False

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

    def __init__(self, parent: object):
        """
        Constructor for IncomingInterface.
        :param parent: AyxPlugin
        """

        # Default properties
        self.parent = parent

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

        # Storing the field names to use when writing data out.
        for field in range(record_info_in.num_fields):
            self.field_lists.append([record_info_in[field].name])

        # Returns a new, empty RecordCreator object that is identical to record_info_in.
        self.record_info_out = record_info_in.clone()

        # Instantiate a new instance of the RecordCopier class.
        self.record_copier = Sdk.RecordCopier(self.record_info_out, record_info_in)

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

        # ['Filesystem', 'Upload', 'Azure', 'S3', 'GCS']
        # First element for each list will always be the field names.
        if len(self.field_lists) > 0 and len(self.field_lists[0]) > 1:
            self.parent.write_lists_to_csv(self.parent.str_file_path, self.field_lists)
        p = Path(self.parent.str_file_path)
        if not p.is_file():
            return False

        dataset = self.parent.dai.upload_dataset_sync(p)
        auto_viz = self.parent.run_auto_viz(dataset)
        # Output M
        # handle or way to reference datasets for additional use
        mod_record_info = Sdk.RecordInfo(self.parent.alteryx_engine)
        mod_record_info.add_field('Dataset', Sdk.FieldType.string, 255)
        self.parent.output_dataset.init(mod_record_info)
        mod_creator = mod_record_info.construct_record_creator()
        mod_record_info.get_field_by_name('Dataset').set_from_string(mod_creator, dataset.key)
        outmod = mod_creator.finalize_record()
        self.parent.output_dataset.push_record(outmod, False)
        self.parent.output_dataset.update_progress(1.0)
        self.parent.output_dataset.close()
        os.remove(p)


import csv
import os
import keycloak
import webbrowser
import xml.etree.ElementTree as Et
from urllib.parse import urljoin

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
        self.str_file_path = self.alteryx_engine.create_temp_file_name('predict_tmp', 0) + '.csv'
        # Custom properties
        self.is_initialized = True
        self.data_done = None
        self.single_input = None
        self.output_anchor = None
        self.starting_value = None
        self.total_record_count = None
        self.record_increment = None
        self.record_info_out = None

        # DAI necessary values
        self.model_id = ""
        self.is_timeseries = False
        self.target_col = ""
        self.dataset_key = ""
        self.display_browser = False

    def pi_init(self, str_xml: str):
        """
        Handles configuration based on the GUI.
        Called when the Alteryx engine is ready to provide the tool configuration from the GUI.
        :param str_xml: The raw XML from the GUI.
        """
        # Getting the dataName data property from the Gui.html
        self.model_id = Et.fromstring(str_xml).find('ModelID').text if 'ModelID' in str_xml else None
        self.is_timeseries = Et.fromstring(str_xml).find('IsTimeSeries').text == "True" if "IsTimeSeries" in str_xml else False
        self.target_col = Et.fromstring(str_xml).find("TargetColumn").text if "TargetColumn" in str_xml else ""
        self.dataset_key = Et.fromstring(str_xml).find('DatasetKey').text if "DatasetKey" in str_xml else ""
        self.display_browser = Et.fromstring(str_xml).find('DisplayBrowser').text == "True" if "DisplayBrowser" in str_xml else False
        self.use_https = Et.fromstring(str_xml).find('UseHttps').text == "True" if 'UseHttps' in str_xml else False
        verify = False if self.use_https else True

        # Get connection information
        base = Et.fromstring(str_xml)
        self.addr = base.find('Address').text if 'Address' in str_xml else 'http://prerelease.h2o.ai'

        username = base.find('Username').text if 'Username' in str_xml else 'h2oai'
        password = self.alteryx_engine.decrypt_password(base.find('Password').text, 0) \
            if 'Password' in str_xml else 'h2oai'
        use_keycloak = base.find('UseKeycloak').text == "True" if 'UseKeycloak' in str_xml else False

        if use_keycloak:
            keycloak_server_url = base.find('keycloakServerURL').text \
                if 'keycloakServerURL' in str_xml else 'No Keycloak Server URL Provided'
            keycloak_token_endpoint = base.find('keycloakTokenEndpoint').text \
                if 'keycloakTokenEndpoint' in str_xml else 'No Keycloak Token Enpoint Provided'
            keycloak_realm = base.find('keycloakRealm').text \
                if 'keycloakRealm' in str_xml else 'No Keycloak Realm Provided'
            public_client_id = base.find('ClientID').text \
                if 'ClientID' in str_xml else 'No Client ID Provided'

            keycloak_openid = keycloak.KeycloakOpenID(
                server_url=urljoin(keycloak_server_url, "/auth/"),
                client_id=public_client_id,
                realm_name=keycloak_realm,
            )

            token = keycloak_openid.token(
                username, password
            )
            refresh_token = keycloak_openid.refresh_token(
                token["refresh_token"],
            )

            token_provider = h2oai_client.OAuth2tokenProvider(
                refresh_token=refresh_token["refresh_token"],
                client_id=public_client_id,
                token_endpoint_url=keycloak_token_endpoint,
                token_introspection_url=urljoin(keycloak_token_endpoint, "/introspect"),
            )
            self.dai = h2oai_client.Client(
                address=self.addr,
                token_provider=token_provider.ensure_fresh_token,
                verify=verify,
            )
        else:
            self.dai = h2oai_client.Client(address=self.addr, username=username, password=password, verify=verify)

        # Valid target name checks.
        error_msg = self.msg_str(self.str_file_path)
        if error_msg != '':
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg(error_msg))
        else:
            self.is_valid = True

        self.output_anchor = self.output_anchor_mgr.get_output_anchor('MLI')

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
        if self.is_timeseries:
            if self.model_id:
                return True
            else:
                self.alteryx_engine.output_message(self.n_tool_id,
                                                   Sdk.EngineMessageType.error,
                                                   self.xmsg("Timeseries MLI requires a model id to be defined"))
                return False
        else:
            if self.model_id and self.dataset_key and self.target_col:
                return True
            else:
                self.alteryx_engine.output_message(self.n_tool_id,
                                                   Sdk.EngineMessageType.error,
                                                   self.xmsg("Standard MLI requires Model ID, Dataset ID, and target column to be defined"))
                return False

    def pi_close(self, b_has_errors: bool):
        """
        Called after all records have been processed.
        :param b_has_errors: Set to true to not do the final processing.
        """
        chrome_path = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe %s'
        if self.is_timeseries:
            mli = self.dai.run_interpret_timeseries_sync(self.dataset_key)
            if self.display_browser:
                url = self.addr + "/mli/#/"
                webbrowser.get(chrome_path).open(url, new=2)
        else:
            mli = self.dai.run_interpretation_sync(self.model_id, self.dataset_key, self.target_col)
            if self.display_browser:
                url = self.addr + "/static/mli/mli.html?interpret_key={}".format(mli.key)
                webbrowser.get(chrome_path).open(url, new=2)

        mod_record_info = Sdk.RecordInfo(self.alteryx_engine)
        mod_record_info.add_field('MLI', Sdk.FieldType.string, 255)
        self.output_anchor.init(mod_record_info)
        mod_creator = mod_record_info.construct_record_creator()
        mod_record_info.get_field_by_name('MLI').set_from_string(mod_creator, mli.key)
        outmod = mod_creator.finalize_record()
        self.output_anchor.push_record(outmod, False)
        self.output_anchor.update_progress(1.0)
        self.output_anchor.close()

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

        return True

    def ii_push_record(self, in_record: object) -> bool:
        """
        Responsible for writing the data to csv in chunks.
        Called when an input record is being sent to the plugin.
        :param in_record: The data for the incoming record.
        :return: False if file path string is invalid, otherwise True.
        """
        if not self.parent.is_valid:
            return False

        if self.is_model:
            index = self.record_info_in.get_field_num("Model")
            if index == -1:
                self.display_error_msg(self.alteryx_engine.xmsg('Model input requires the key be stored in field "Model"'))
                return False
            self.parent.model_id = self.record_info_in[index].get_as_string(in_record)
        else:
            index = self.record_info_in.get_field_num("Dataset")
            if index == -1:
                self.display_error_msg(self.alteryx_engine.xmsg('Dataset input requires key to be stored in field "Dataset"'))
                return False
            self.parent.dataset_key = self.record_info_in[index].get_as_string(in_record)
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
        return True



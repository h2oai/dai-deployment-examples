import csv
import os
import keycloak
import webbrowser
import xml.etree.ElementTree as Et
from urllib.parse import urljoin

import AlteryxPythonSDK as Sdk
import h2oai_client
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
        self.str_file_path = self.alteryx_engine.create_temp_file_name('daiplugin', 0) + '.csv'
        # Custom properties
        self.is_initialized = True
        self.output_anchor = None
        self.output_model = None
        self.starting_value = None
        self.total_record_count = None
        self.record_increment = None

        # initialize default values for datasets to be used by DAI experiment
        self.train = ""
        self.test = ""
        self.valid = ""
        self.target_name = None
        self.accuracy = None
        self.time = None
        self.interpretability = None
        self.is_classification = None
        self.is_timeseries = False
        self.config_string = ""
        self.scorer = ""
        self.dai = None

    def pi_init(self, str_xml: str):
        """
        Handles configuration based on the GUI.
        Called when the Alteryx engine is ready to provide the tool configuration from the GUI.
        :param str_xml: The raw XML from the GUI.
        """
        # Getting the dataName data property from the Gui.html
        self.target_name = Et.fromstring(str_xml).find('TargetColumn').text if 'TargetColumn' in str_xml else None
        #self.output_field = Et.fromstring(str_xml).find('OutputField').text if 'OutputField' in str_xml else None
        self.accuracy = int(Et.fromstring(str_xml).find('Accuracy').text) if 'Accuracy' in str_xml else None
        self.time = int(Et.fromstring(str_xml).find('Time').text) if 'Time' in str_xml else None
        self.interpretability = int(Et.fromstring(str_xml).find('Interpretability').text) if 'Interpretability' in str_xml else None
        self.is_classification = Et.fromstring(str_xml).find('IsClassification').text == 'True' if 'IsClassification' in str_xml else None
        # Default function for DAI is to have Time Series off
        self.is_timeseries = Et.fromstring(str_xml).find('IsTimeSeries').text == 'True' if 'IsTimeSeries' in str_xml else False
        self.config_string = Et.fromstring(str_xml).find('AdditionalUserConfigs').text if 'AdditionalUserConfigs' in str_xml else ""
        reg_scorer = Et.fromstring(str_xml).find('RegressionScoringMetric').text if 'RegressionScoringMetric' in str_xml else ""
        class_scorer = Et.fromstring(str_xml).find('ClassificationScoringMetric').text if 'ClassificationScoringMetric' in str_xml else ""
        self.use_https = Et.fromstring(str_xml).find('UseHttps').text == "True" if 'UseHttps' in str_xml else False
        verify = False if self.use_https else True

        if self.is_classification:
            self.scorer = class_scorer
        else:
            self.scorer = reg_scorer
        self.display_browser = Et.fromstring(str_xml).find('DisplayBrowser').text == 'True' if 'DisplayBrowser' in str_xml else False
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
        if self.target_name is None:
            self.display_error_msg(self.alteryx_engine.xmsg('Target Column cannot be empty. Please enter a Target Column.'))
        elif len(self.target_name) > 255:
            self.display_error_msg(self.alteryx_engine.xmsg('Target Column cannot be greater than 255 characters.'))
        error_msg = self.msg_str(self.str_file_path)
        if error_msg != '':
            self.alteryx_engine.output_message(self.n_tool_id, Sdk.EngineMessageType.error, self.xmsg(error_msg))
        else:
            self.is_valid = True

        self.output_anchor = self.output_anchor_mgr.get_output_anchor('Predictions')
        self.output_model = self.output_anchor_mgr.get_output_anchor('Model')

    def pi_add_incoming_connection(self, str_type: str, str_name: str) -> object:
        """
        The IncomingInterface objects are instantiated here, one object per incoming connection.
        Called when the Alteryx engine is attempting to add an incoming data connection.
        :param str_type: The name of the input connection anchor, defined in the Config.xml file.
        :param str_name: The name of the wire, defined by the workflow author.
        :return: The IncomingInterface object(s).
        """

        if str_name in ["train", "test", "valid"]:
            return IncomingInterface(self, str_name)
        else:
            self.alteryx_engine.output_message(self.n_tool_id,
                                               Sdk.EngineMessageType.error,
                                               self.xmsg('Incoming wire must be named: test, train, or valid'))
            return False

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

    def pi_close(self, b_has_errors: bool):
        """
        Called after all records have been processed.
        :param b_has_errors: Set to true to not do the final processing.
        """
        params = self.dai.get_experiment_tuning_suggestion(
            dataset_key=self.train,
            target_col=self.target_name,
            is_classification=self.is_classification,
            is_time_series=self.is_timeseries,
            config_overrides=self.config_string,
            cols_to_drop=[],
            is_image=False
        )
        params.accuracy = self.accuracy
        params.time = self.time
        params.interpretability = self.interpretability
        params.scorer = self.scorer
        if self.is_timeseries:
            params.time_col = "[AUTO]"
        params.dataset_key = self.train
        params.testset_key = self.test
        params.validset_key = self.valid
        params = params.dump()

        exp = self.dai.start_experiment_sync(**params)
        if self.display_browser:
            url = self.addr + "/#experiment?key={}".format(exp.key)
            chrome_path = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe %s'
            webbrowser.get(chrome_path).open(url, new=2)

        # Experiment Complete: Output Predictions and Model Key for future use
        if self.valid != "":
            data = self.dai.get_dataset_summary(self.valid)
        elif self.test != "":
            data = self.dai.get_dataset_summary(self.test)
        else:
            data = self.dai.get_dataset_summary(self.train)
        predict = self.dai.make_prediction_sync(exp.key, data.key, False, False)

        # Output D
        # data with predicted values
        p_path = self.dai.download(predict.predictions_csv_path, os.path.dirname(
            self.alteryx_engine.create_temp_file_name('dai_predict', 0)))
        out_p = pd.read_csv(p_path)
        # if self.parent.output_field is not None:
        record_info_out = Sdk.RecordInfo(self.alteryx_engine)
        for col in out_p.columns:
            record_info_out.add_field(col, Sdk.FieldType.float)
        self.output_anchor.init(record_info_out)
        record_creator = record_info_out.construct_record_creator()

        for index, row in out_p.iterrows():
            for col in out_p.columns:
                record_info_out.get_field_by_name(col).set_from_string(record_creator, str(row[col]))
            outrec = record_creator.finalize_record()
            self.output_anchor.push_record(outrec, False)
            record_creator.reset()
        self.output_anchor.update_progress(1.0)
        self.output_anchor.close()
        os.remove(p_path)
        # Output M
        # handle or way to reference experiment for additional prediction
        mod_record_info = Sdk.RecordInfo(self.alteryx_engine)
        mod_record_info.add_field('Model', Sdk.FieldType.string, 255)
        self.output_model.init(mod_record_info)
        mod_creator = mod_record_info.construct_record_creator()
        mod_record_info.get_field_by_name('Model').set_from_string(mod_creator, exp.key)
        outmod = mod_creator.finalize_record()
        self.output_model.push_record(outmod, False)
        self.output_model.update_progress(1.0)
        self.output_model.close()

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

    def __init__(self, parent: object, wire_name: str):
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
        self.record_info_in = None
        self.record_info_out = None
        self.wire_name = wire_name
        self.dataset_key = ""

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

        index = self.record_info_in.get_field_num("Dataset")
        if index == -1:
            self.display_error_msg(self.alteryx_engine.xmsg('Experiment input requires the key be stored in field "Dataset"'))
            return False
        self.dataset_key = self.record_info_in[index].get_as_string(in_record)

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
        if self.wire_name == "train":
            self.parent.train = self.dataset_key
        elif self.wire_name == "test":
            self.parent.test = self.dataset_key
        elif self.wire_name == "valid":
            self.parent.valid = self.dataset_key
        else:
            self.display_error_msg(self.alteryx_engine.xmsg('Wire names for experiment input must be: train, test, or valid'))
            return False

        return True

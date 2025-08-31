from models.base import CameraModel, Action
from datetime import datetime
from urllib.parse import quote
import os
from errors import CameraError

# -----------------------------------------------------------------------------
#
# LAMAX X9.1 - MODEL DEFINITION
#
# -----------------------------------------------------------------------------

class LamaxX9_1(CameraModel):
    """LAMAX X9.1 camera model implementation."""

    def __init__(self):
        super().__init__("LAMAX X9.1", "/cgi-bin/hi3510/")
        self.workmode_map = {
            "NormalPhoto": "NormalPhoto", "TimerPhoto": "TimerPhoto", "Burst": "Burst", "PhotoLapse": "PhotoLapse",
            "NormalVideo": "NormalVideo", "CarMode": "CarMode", "VideoLapse": "VideoLapse", "SlowRec": "SlowRec",
            "System": "System"
        }
        self.setting_map = {"Resolution": "Resolution", "Lens angle": "Lens angle"}

    @staticmethod
    def get_model_name():
        return "LAMAX X9.1"

    @staticmethod
    def get_cli_name():
        return "X9_1"

    def get_actions(self):
        return [GetParameterOptions, SetParameter, SDCardStatus, SDCardFormat, StartRecording, StopRecording,
                TakePicture, GetAllWorkmodes, SetWorkmode, GetCurrentWorkmode, GetCurrentStatus, GetDeviceInfo,
                SetSystemTime, GetWifiInfo, SetWifiSettings, GetBatteryStatus, GetFileCount, GetFileList,
                DeleteFile, GetFileInfo, DownloadFile, GetParameter]

    # --- Command Implementations ---

    def map_workmode(self, workmode_name):
        return self.workmode_map.get(workmode_name, workmode_name)

    def map_setting_name(self, setting_name):
        return self.setting_map.get(setting_name, setting_name)

    def get_parameter_options(self, workmode, name):
        encoded_name = quote(name)
        return f"getsecondmenuitem.cgi?-workmode={workmode}&-name={encoded_name}"

    def set_parameter(self, workmode, name, value):
        encoded_name = quote(name)
        encoded_value = quote(value)
        return f"setcurparameter.cgi?-workmode={workmode}&-name={encoded_name}&-value={encoded_value}"

    def get_sd_card_status(self):
        return "getsdstate.cgi"

    def format_sd_card(self):
        return "sdcommand.cgi?-format&-partition=1"

    def start_recording(self):
        return "record.cgi?&-cmd=start"

    def stop_recording(self):
        return "record.cgi?&-cmd=stop"

    def take_picture(self):
        return "photo.cgi?&-type=photo"

    def get_all_workmodes(self):
        return "getallworkmode.cgi"

    def set_workmode(self, workmode):
        return f"setcurworkmode.cgi?-workmode={workmode}"

    def get_current_workmode(self):
        return "getcurworkmode.cgi"

    def get_current_status(self):
        return "getcurallinfo.cgi"

    def get_device_info(self):
        return "getdeviceattr.cgi"

    def set_system_time(self, time):
        return f"setsystemtime.cgi?-time={time}"

    def get_wifi_info(self):
        return "getwifi.cgi"

    def set_wifi_settings(self, ssid, password):
        return f"setwifi.cgi?&-wifissid={ssid}&-wifikey={password}"

    def get_battery_status(self):
        return "getbatterycapacity.cgi"

    def get_file_count(self):
        return "getfilecount.cgi"

    def get_file_list(self, start, end):
        return f"getfilelistinfoios.cgi?&-start={start}&-end={end}"

    def delete_file(self, path):
        return f"deletefile.cgi?&-name={path}"

    def get_file_info(self, path):
        return f"getfileinfo.cgi?&-name={path}"

    def get_parameter(self, workmode, name):
        encoded_name = quote(name)
        return f"getsecondmenuitem.cgi?-workmode={workmode}&-name={encoded_name}"

    def get_stresstest_scenario(self):
        return {
            'workmode': 'NormalVideo',
            'settings': [
                ('Looping Video', 'On'),
                ('Resolution', '4K30')
            ]
        }

    # --- X10.1 specific commands, not implemented for X9.1 ---
    def get_led_state(self): raise NotImplementedError("Not available on this model")
    def set_led_state(self, enable): raise NotImplementedError("Not available on this model")
    def get_device_capabilities(self): raise NotImplementedError("Not available on this model")
    def get_viewfield(self): raise NotImplementedError("Not available on this model")
    def set_viewfield(self, fov): raise NotImplementedError("Not available on this model")
    def get_flip(self): raise NotImplementedError("Not available on this model")
    def set_flip(self, enable): raise NotImplementedError("Not available on this model")
    def get_time_osd(self): raise NotImplementedError("Not available on this model")
    def set_time_osd(self, enable): raise NotImplementedError("Not available on this model")
    def get_screen_auto_sleep(self): raise NotImplementedError("Not available on this model")
    def set_screen_auto_sleep(self, time): raise NotImplementedError("Not available on this model")
    def get_boot_action(self): raise NotImplementedError("Not available on this model")
    def set_boot_action(self, action): raise NotImplementedError("Not available on this model")
    def get_audio_encode(self): raise NotImplementedError("Not available on this model")
    def set_audio_encode(self, enable): raise NotImplementedError("Not available on this model")
    def get_screen_brightness(self): raise NotImplementedError("Not available on this model")
    def reset(self): raise NotImplementedError("Not available on this model")


# -----------------------------------------------------------------------------
#
# LAMAX X9.1 - ACTION DEFINITIONS
#
# -----------------------------------------------------------------------------

class GetParameter(Action):
    @staticmethod
    def get_name(): return "getparameter"
    @staticmethod
    def get_help(): return "Get the current value of a specific parameter."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode of the setting.")
        parser.add_argument("name", help="The name of the setting to get.")
    def execute(self, args):
        try:
            response = self.camera_api.call('get_parameter', args.workmode, args.name)
            print(f"\n--- Current Value for '{args.name}' in {args.workmode} ---")
            print(f"  Value: {response.get('value', 'N/A')}")
            print("-----------------------------------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve parameter '{args.name}': {e}")

class GetParameterOptions(Action):
    @staticmethod
    def get_name(): return "getparameteroptions"
    @staticmethod
    def get_help(): return "Get all available options for a specific setting."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode of the setting.")
        parser.add_argument("name", help="The name of the setting.")
    def execute(self, args):
        try:
            workmode = self.camera_api.model.map_workmode(args.workmode)
            name = self.camera_api.model.map_setting_name(args.name)
            response = self.camera_api.call('get_parameter_options', workmode, name)
            print(f"\n--- Options for '{args.name}' in {args.workmode} ---")
            options = response.get('item') or response.get('capability')
            current = response.get('value', 'N/A')
            print(f"  Available: {options or 'N/A'}")
            if 'value' in response:
                 print(f"  Current: {current}")
            print("-------------------------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve options for '{args.name}': {e}")

class SetParameter(Action):
    @staticmethod
    def get_name(): return "setparameter"
    @staticmethod
    def get_help(): return "Set the value for a specific setting."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode of the setting.")
        parser.add_argument("name", help="The name of the setting to change.")
        parser.add_argument("value", help="The new value for the setting.")
    def execute(self, args):
        try:
            workmode = self.camera_api.model.map_workmode(args.workmode)
            name = self.camera_api.model.map_setting_name(args.name)
            response = self.camera_api.call('set_parameter', workmode, name, args.value)
            if response and response.get('status') == 'Success':
                print(f"Successfully set '{args.name}' to '{args.value}' in {args.workmode}.")
            else:
                print(f"Failed to set parameter '{args.name}'. Response: {response}")
        except CameraError as e:
            print(f"Failed to set parameter '{args.name}': {e}")

class SDCardStatus(Action):
    @staticmethod
    def get_name(): return "sdcardstatus"
    @staticmethod
    def get_help(): return "Get SD card status."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_sd_card_status')
            print("\n--- SD Card Status ---")
            print(f"  State: {response.get('sdstate', 'N/A')}")
            print(f"  Total Space: {response.get('total', 'N/A')}")
            print(f"  Used Space: {response.get('used', 'N/A')}")
            print("----------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve SD card status: {e}")

class SDCardFormat(Action):
    @staticmethod
    def get_name(): return "sdcardformat"
    @staticmethod
    def get_help(): return "Format SD card."
    def execute(self, args):
        try:
            response = self.camera_api.call('format_sd_card')
            print("\n--- SD Card Format ---")
            print(f"  Status: {'OK' if response.get('sdstatus') == '1' else 'Failed'}")
            print(f"  Free Space: {response.get('sdfreespace', 'N/A')}")
            print(f"  Total Space: {response.get('sdtotalspace', 'N/A')}")
            print(f"  Partition Number: {response.get('partitionnum', 'N/A')}")
            print("----------------------\n")
        except CameraError as e:
            print(f"Failed to format SD card: {e}")

class StartRecording(Action):
    @staticmethod
    def get_name(): return "startrecording"
    @staticmethod
    def get_help(): return "Start video recording."
    def execute(self, args):
        try:
            response = self.camera_api.call('start_recording')
            print("Recording started.", response)
        except CameraError as e:
            print(f"Failed to start recording: {e}")

class StopRecording(Action):
    @staticmethod
    def get_name(): return "stoprecording"
    @staticmethod
    def get_help(): return "Stop video recording."
    def execute(self, args):
        try:
            response = self.camera_api.call('stop_recording')
            print("Recording stopped.", response)
        except CameraError as e:
            print(f"Failed to stop recording: {e}")

class TakePicture(Action):
    @staticmethod
    def get_name(): return "takepicture"
    @staticmethod
    def get_help(): return "Take a picture."
    def execute(self, args):
        try:
            response = self.camera_api.call('take_picture')
            print("Picture taken.", response)
        except CameraError as e:
            print(f"Failed to take picture: {e}")

class GetAllWorkmodes(Action):
    @staticmethod
    def get_name(): return "getallworkmodes"
    @staticmethod
    def get_help(): return "Get all available work modes."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_all_workmodes')
            print("\n--- Available Work Modes ---")
            for key, value in response.items():
                print(f"  {key.capitalize()}: {', '.join(value.split(','))}")
            print("----------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve work modes: {e}")

class SetWorkmode(Action):
    @staticmethod
    def get_name(): return "setworkmode"
    @staticmethod
    def get_help(): return "Set the current work mode."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The work mode to set.")
    def execute(self, args):
        try:
            workmode = self.camera_api.model.map_workmode(args.workmode)
            response = self.camera_api.call('set_workmode', workmode)
            if response and response.get('status') == 'Success':
                print(f"Work mode set to {args.workmode}.")
            else:
                print(f"Failed to set work mode. Response: {response}")
        except CameraError as e:
            print(f"Failed to set work mode: {e}")

class GetCurrentWorkmode(Action):
    @staticmethod
    def get_name(): return "getcurrentworkmode"
    @staticmethod
    def get_help(): return "Get the current work mode."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_current_workmode')
            print("\n--- Current Work Mode ---")
            print(f"  Mode: {response.get('workmode', 'N/A')}")
            if 'value' in response:
                print(f"  Description: {response.get('value', 'N/A')}")
            print("-------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve current work mode: {e}")

class GetCurrentStatus(Action):
    @staticmethod
    def get_name(): return "getcurrentstatus"
    @staticmethod
    def get_help(): return "Get current camera status."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_current_status')
            print("\n--- Current Camera Status ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("-----------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve camera status: {e}")

class GetDeviceInfo(Action):
    @staticmethod
    def get_name(): return "getdeviceinfo"
    @staticmethod
    def get_help(): return "Get device information."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_device_info')
            print("\n--- Device Information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("--------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve device information: {e}")

class SetSystemTime(Action):
    @staticmethod
    def get_name(): return "setsystemtime"
    @staticmethod
    def get_help(): return "Set system time."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("datetime", help="YYYYMMDDHHMMSS, or leave empty for current time.", nargs='?', default=None)
    def execute(self, args):
        try:
            time_str = args.datetime if args.datetime else datetime.now().strftime("%Y%m%d%H%M%S")
            if len(time_str) != 14 or not time_str.isdigit():
                print("Invalid datetime format. Please use YYYYMMDDHHMMSS.")
                return
            response = self.camera_api.call('set_system_time', time_str)
            if response and response.get('status') == 'Success':
                print("System time successfully set.")
            else:
                print(f"Failed to set system time. Response: {response}")
        except CameraError as e:
            print(f"Failed to set system time: {e}")

class GetWifiInfo(Action):
    @staticmethod
    def get_name(): return "getwifiinfo"
    @staticmethod
    def get_help(): return "Get WiFi information."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_wifi_info')
            print("\n--- WiFi information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("--------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve WiFi information: {e}")

class SetWifiSettings(Action):
    @staticmethod
    def get_name(): return "setwifisettings"
    @staticmethod
    def get_help(): return "Set WiFi settings."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("ssid_prefix", help="The WiFi SSID prefix.")
        parser.add_argument("password", help="The WiFi password (10 chars for X9.1).")
    def execute(self, args):
        try:
            if len(args.password) != 10:
                print("Password must be exactly 10 characters long for LAMAX X9.1.")
                return
            full_ssid = f"{args.ssid_prefix}_aabbcc"
            response = self.camera_api.call('set_wifi_settings', full_ssid, args.password)
            if response:
                 print(f"Successfully set WiFi SSID to '{full_ssid}'.", response)
            else:
                print(f"WiFi settings command sent. Camera may not provide confirmation.")
        except CameraError as e:
            print(f"Failed to set WiFi settings: {e}")

class GetBatteryStatus(Action):
    @staticmethod
    def get_name(): return "getbatterystatus"
    @staticmethod
    def get_help(): return "Get battery status."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_battery_status')
            print("\n--- Battery Status ---")
            print(f"  Capacity: {response.get('capacity', 'N/A')}%")
            print(f"  Charging: {'Yes' if response.get('charge') == '1' else 'No'}")
            print(f"  On AC Power: {'Yes' if response.get('ac') == '1' else 'No'}")
            print("----------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve battery status: {e}")

class GetFileCount(Action):
    @staticmethod
    def get_name(): return "getfilecount"
    @staticmethod
    def get_help(): return "Get file count on SD card."
    def execute(self, args):
        try:
            response = self.camera_api.call('get_file_count')
            print(f"\nFile Count: {response.get('count', 'N/A')}\n")
        except CameraError as e:
            print(f"Failed to retrieve file count: {e}")

class GetFileList(Action):
    @staticmethod
    def get_name(): return "getfilelist"
    @staticmethod
    def get_help(): return "List files on the SD card."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("--start", type=int, default=1, help="Start index.")
        parser.add_argument("--end", type=int, default=999, help="End index.")
    def execute(self, args):
        try:
            response = self.camera_api.call('get_file_list', args.start, args.end)
            if response and 'data' in response:
                print("\n--- File List ---")
                print(f"{'Size (MB)':<12}{'Duration (s)':<14}{'Created':<22}{'Path'}")
                print(f"{'-----------':<12}{'------------':<14}{'--------------------':<22}{'----'}")
                for file_info in response['data']:
                    try:
                        size_mb = int(file_info.get('size', 0)) / (1024 * 1024)
                        size_str = f"{size_mb:<12.2f}"
                    except (ValueError, TypeError):
                        size_str = f"{'N/A':<12}"
                    duration = file_info.get('time', 'N/A')
                    created_date = file_info.get('create', 'N/A')
                    path = file_info.get('path', 'N/A')
                    print(f"{size_str}{duration:<14}{created_date:<22}{path}")
                print("-----------------\n")
            else:
                print(f"Failed to retrieve file list. Response: {response}")
        except CameraError as e:
            print(f"Failed to retrieve file list: {e}")

class DeleteFile(Action):
    @staticmethod
    def get_name(): return "deletefile"
    @staticmethod
    def get_help(): return "Delete a file from SD card."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("path", help="The path of the file to delete.")
    def execute(self, args):
        try:
            response = self.camera_api.call('delete_file', args.path)
            print(f"File '{args.path}' deleted.", response)
        except CameraError as e:
            print(f"Failed to delete file '{args.path}': {e}")

class GetFileInfo(Action):
    @staticmethod
    def get_name(): return "getfileinfo"
    @staticmethod
    def get_help(): return "Get file information."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("path", help="Path of the file.")
    def execute(self, args):
        try:
            response = self.camera_api.call('get_file_info', args.path)
            print("\n--- File Information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("------------------------\n")
        except CameraError as e:
            print(f"Failed to retrieve info for file '{args.path}': {e}")

class DownloadFile(Action):
    @staticmethod
    def get_name(): return "downloadfile"
    @staticmethod
    def get_help(): return "Download a file from the camera."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("remote_path", help="Remote path on camera.")
        parser.add_argument("local_path", nargs='?', help="Local path to save.")
    def execute(self, args):
        try:
            local_path = args.local_path if args.local_path else os.path.basename(args.remote_path)
            success = self.camera_api.download_file(args.remote_path, local_path)
            if not success:
                print("Download failed.")
        except CameraError as e:
            print(f"Download failed: {e}")

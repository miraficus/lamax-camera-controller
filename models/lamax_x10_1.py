from models.base import CameraModel, Action
from datetime import datetime
from urllib.parse import quote
import os

# -----------------------------------------------------------------------------
#
# LAMAX X10.1 - MODEL DEFINITION
#
# -----------------------------------------------------------------------------

class LamaxX10_1(CameraModel):
    """LAMAX X10.1 camera model implementation."""

    def __init__(self):
        super().__init__("LAMAX X10.1", "/cgi-bin/hi3510/")
        # TODO: User needs to verify and complete these mappings.
        self.workmode_map = {
            'photo': '0', 'multi': '1', 'video': '20'
        }
        self.setting_map = {
            'Resolution': '0'
        }

    @staticmethod
    def get_model_name(): return "LAMAX X10.1"
    @staticmethod
    def get_cli_name(): return "X10_1"

    def get_actions(self):
        return [GetParameter, GetParameterOptions, SetParameter, SDCardStatus, SDCardFormat,
                GetCurrentStatus, GetDeviceInfo, SetSystemTime, GetBatteryStatus, GetFileCount,
                GetFileList, DownloadFile, GetLedState, SetLedState, GetDeviceCapabilities,
                GetViewfield, SetViewfield, GetFlip, SetFlip, GetTimeOsd, SetTimeOsd,
                GetScreenAutoSleep, SetScreenAutoSleep, GetBootAction, SetBootAction,
                GetAudioEncode, SetAudioEncode, Reset]

    # --- Command Implementations ---
    def map_workmode(self, workmode_name): return self.workmode_map.get(workmode_name, workmode_name)
    def map_setting_name(self, setting_name): return self.setting_map.get(setting_name, setting_name)

    def get_parameter(self, workmode, name):
        setting_type = self.map_setting_name(name)
        return f"getparameter.cgi?&-workmode={workmode}&-type={setting_type}"
    def get_parameter_options(self, workmode, name):
        setting_type = self.map_setting_name(name)
        return f"getcapability.cgi?&-workmode={workmode}&-type={setting_type}"
    def set_parameter(self, workmode, name, value):
        setting_type = self.map_setting_name(name)
        return f"setparameter.cgi?&-workmode={workmode}&-type={setting_type}&-value={value}"
    def get_sd_card_status(self): return "getsdstate.cgi"
    def format_sd_card(self): return "sdcommand.cgi?-format&-partition=1"
    def get_current_status(self): return "getallinfo.cgi"
    def get_device_info(self): return "getdeviceattr.cgi"
    def get_battery_status(self): return "getbatterycapacity.cgi"
    def set_system_time(self, time): return f"setsystime.cgi?-time={time}"
    def get_file_count(self): return "getfilecount.cgi"
    def get_file_list(self, start, end): return f"getfilelistinfoios.cgi?&-start={start}&-end={end}"
    def get_led_state(self): return "getledstate.cgi"
    def get_device_capabilities(self): return "getdevcapabilities.cgi"
    def get_viewfield(self): return "getviewfield.cgi"
    def get_flip(self): return "getflip.cgi"
    def set_flip(self, enable): return f"setflip.cgi?&-enable={enable}"
    def get_time_osd(self): raise NotImplementedError("Unknown command") # Not in notes
    def set_time_osd(self, enable): return f"settimeosd.cgi?&-enable={enable}"
    def get_screen_auto_sleep(self): return "getscreenautosleep.cgi"
    def set_screen_auto_sleep(self, time): raise NotImplementedError("Unknown command") # Not in notes
    def get_boot_action(self): return "getbootaction.cgi"
    def set_boot_action(self, action): return f"setbootaction.cgi?&-action={action}"
    def get_audio_encode(self): return "getaudioencode.cgi"
    def set_audio_encode(self, enable): return f"setaudioencode.cgi?&-enable={enable}"
    def reset(self): return "reset.cgi"

    # --- Not Implemented Yet ---
    def start_recording(self): raise NotImplementedError("Unknown command")
    def stop_recording(self): raise NotImplementedError("Unknown command")
    def take_picture(self): raise NotImplementedError("Unknown command")
    def get_all_workmodes(self): return "getworkmode.cgi" # Needs special parsing
    def set_workmode(self, workmode): return f"setworkmode.cgi?&-workmode={workmode}"
    def get_current_workmode(self): return "getworkmode.cgi"
    def get_wifi_info(self): raise NotImplementedError("Unknown command")
    def set_wifi_settings(self, ssid, password): raise NotImplementedError("Unknown command")
    def delete_file(self, path): raise NotImplementedError("Unknown command")
    def get_file_info(self, path): raise NotImplementedError("Unknown command")
    def set_led_state(self, enable): raise NotImplementedError("Unknown command")
    def set_viewfield(self, fov): raise NotImplementedError("Unknown command")

    def get_stresstest_scenario(self):
        # NOTE: Looping video command is not yet known for X10.1
        return {
            'workmode': 'video',
            'settings': [
                ('Resolution', '4K30')
            ]
        }

# -----------------------------------------------------------------------------
#
# LAMAX X10.1 - ACTION DEFINITIONS
#
# -----------------------------------------------------------------------------

# Generic actions that are the same as X9.1, just pointing to this model's methods
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
        response = self.camera_api.call('get_parameter', args.workmode, args.name)
        if response:
            print(f"\n--- Current Value for '{args.name}' in {args.workmode} ---")
            print(f"  Value: {response.get('value', 'N/A')}")
            print("-----------------------------------------------------\n")
        else:
            print(f"Failed to retrieve parameter '{args.name}'.")

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
        workmode = self.camera_api.model.map_workmode(args.workmode)
        name = self.camera_api.model.map_setting_name(args.name)
        response = self.camera_api.call('get_parameter_options', workmode, name)
        if response:
            print(f"\n--- Options for '{args.name}' in {args.workmode} ---")
            options = response.get('item') or response.get('capability')
            current = response.get('value', 'N/A')
            print(f"  Available: {options or 'N/A'}")
            if 'value' in response:
                 print(f"  Current: {current}")
            print("-------------------------------------------\n")
        else:
            print(f"Failed to retrieve options for '{args.name}'.")

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
        workmode = self.camera_api.model.map_workmode(args.workmode)
        name = self.camera_api.model.map_setting_name(args.name)
        response = self.camera_api.call('set_parameter', workmode, name, args.value)
        if response and response.get('status') == 'Success':
            print(f"Successfully set '{args.name}' to '{args.value}' in {args.workmode}.")
        else:
            print(f"Failed to set parameter '{args.name}'. Response: {response}")

class SDCardStatus(Action):
    @staticmethod
    def get_name(): return "sdcardstatus"
    @staticmethod
    def get_help(): return "Get SD card status."
    def execute(self, args):
        response = self.camera_api.call('get_sd_card_status')
        if response:
            print("\n--- SD Card Status ---")
            print(f"  State: {response.get('sdstate', 'N/A')}")
            print(f"  Total Space: {response.get('total', 'N/A')}")
            print(f"  Used Space: {response.get('used', 'N/A')}")
            print("----------------------\n")
        else:
            print("Failed to retrieve SD card status.")

class SDCardFormat(Action):
    @staticmethod
    def get_name(): return "sdcardformat"
    @staticmethod
    def get_help(): return "Format SD card."
    def execute(self, args):
        response = self.camera_api.call('format_sd_card')
        if response:
            print("\n--- SD Card Format ---")
            print(f"  Status: {'OK' if response.get('sdstatus') == '1' else 'Failed'}")
            print(f"  Free Space: {response.get('sdfreespace', 'N/A')}")
            print(f"  Total Space: {response.get('sdtotalspace', 'N/A')}")
            print(f"  Partition Number: {response.get('partitionnum', 'N/A')}")
            print("----------------------\n")
        else:
            print("Failed to format SD card.")

class GetCurrentStatus(Action):
    @staticmethod
    def get_name(): return "getcurrentstatus"
    @staticmethod
    def get_help(): return "Get current camera status."
    def execute(self, args):
        response = self.camera_api.call('get_current_status')
        if response:
            print("\n--- Current Camera Status ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("-----------------------------\n")
        else:
            print("Failed to retrieve camera status.")

class GetDeviceInfo(Action):
    @staticmethod
    def get_name(): return "getdeviceinfo"
    @staticmethod
    def get_help(): return "Get device information."
    def execute(self, args):
        response = self.camera_api.call('get_device_info')
        if response:
            print("\n--- Device Information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("--------------------------\n")
        else:
            print("Failed to retrieve device information.")

class SetSystemTime(Action):
    @staticmethod
    def get_name(): return "setsystemtime"
    @staticmethod
    def get_help(): return "Set system time."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("datetime", help="YYYYMMDDHHMMSS, or leave empty for current time.", nargs='?', default=None)
    def execute(self, args):
        time_str = args.datetime if args.datetime else datetime.now().strftime("%Y%m%d%H%M%S")
        if len(time_str) != 14 or not time_str.isdigit():
            print("Invalid datetime format. Please use YYYYMMDDHHMMSS.")
            return
        response = self.camera_api.call('set_system_time', time_str)
        if response and response.get('status') == 'Success':
            print("System time successfully set.")
        else:
            print(f"Failed to set system time. Response: {response}")

class GetBatteryStatus(Action):
    @staticmethod
    def get_name(): return "getbatterystatus"
    @staticmethod
    def get_help(): return "Get battery status."
    def execute(self, args):
        response = self.camera_api.call('get_battery_status')
        if response:
            print("\n--- Battery Status ---")
            print(f"  Capacity: {response.get('capacity', 'N/A')}%")
            print(f"  Charging: {'Yes' if response.get('charge') == '1' else 'No'}")
            print(f"  On AC Power: {'Yes' if response.get('ac') == '1' else 'No'}")
            print("----------------------\n")
        else:
            print("Failed to retrieve battery status.")

class GetFileCount(Action):
    @staticmethod
    def get_name(): return "getfilecount"
    @staticmethod
    def get_help(): return "Get file count on SD card."
    def execute(self, args):
        response = self.camera_api.call('get_file_count')
        if response:
            print(f"\nFile Count: {response.get('count', 'N/A')}\n")
        else:
            print("Failed to retrieve file count.")

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
        local_path = args.local_path if args.local_path else os.path.basename(args.remote_path)
        success = self.camera_api.download_file(args.remote_path, local_path)
        if not success:
            print("Download failed.")

# --- X10.1 Specific Actions ---

class GetLedState(Action):
    @staticmethod
    def get_name(): return "getledstate"
    @staticmethod
    def get_help(): return "Get LED state."
    def execute(self, args):
        response = self.camera_api.call('get_led_state')
        if response:
            print(f"\nLED State: {'On' if response.get('enable') == '1' else 'Off'}\n")
        else:
            print("Failed to get LED state.")

class SetLedState(Action):
    @staticmethod
    def get_name(): return "setledstate"
    @staticmethod
    def get_help(): return "Set LED state."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("state", choices=['on', 'off'])
    def execute(self, args):
        response = self.camera_api.call('set_led_state', '1' if args.state == 'on' else '0')
        if response:
            print("Successfully set LED state.")
        else:
            print("Failed to set LED state (command may not be implemented).")

class GetDeviceCapabilities(Action):
    @staticmethod
    def get_name(): return "getdevicecapabilities"
    @staticmethod
    def get_help(): return "Get device capabilities."
    def execute(self, args):
        response = self.camera_api.call('get_device_capabilities')
        if response:
            print(f"\nDevice Capabilities: {response.get('devcapabilities', 'N/A')}\n")
        else:
            print("Failed to get device capabilities.")

class GetViewfield(Action):
    @staticmethod
    def get_name(): return "getviewfield"
    @staticmethod
    def get_help(): return "Get field of view."
    def execute(self, args):
        response = self.camera_api.call('get_viewfield')
        if response:
            print(f"\nField of View: {response.get('fov', 'N/A')}\n")
        else:
            print("Failed to get field of view.")

class SetViewfield(Action):
    @staticmethod
    def get_name(): return "setviewfield"
    @staticmethod
    def get_help(): return "Set field of view (NOT IMPLEMENTED)."
    def execute(self, args):
        print("This command is not yet implemented for this camera model.")

class GetFlip(Action):
    @staticmethod
    def get_name(): return "getflip"
    @staticmethod
    def get_help(): return "Get display flip status."
    def execute(self, args):
        response = self.camera_api.call('get_flip')
        if response:
            print(f"\nDisplay Flip: {'On' if response.get('enable') == '1' else 'Off'}\n")
        else:
            print("Failed to get flip status.")

class SetFlip(Action):
    @staticmethod
    def get_name(): return "setflip"
    @staticmethod
    def get_help(): return "Set display flip."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("state", choices=['on', 'off'])
    def execute(self, args):
        response = self.camera_api.call('set_flip', '1' if args.state == 'on' else '0')
        if response and response.get('status') == 'Success':
            print("Successfully set flip state.")
        else:
            print("Failed to set flip state.")

class GetTimeOsd(Action):
    @staticmethod
    def get_name(): return "gettimeosd"
    @staticmethod
    def get_help(): return "Get time OSD status (NOT IMPLEMENTED)."
    def execute(self, args):
        print("This command is not yet implemented for this camera model.")

class SetTimeOsd(Action):
    @staticmethod
    def get_name(): return "settimeosd"
    @staticmethod
    def get_help(): return "Set time OSD."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("state", choices=['on', 'off'])
    def execute(self, args):
        response = self.camera_api.call('set_time_osd', '1' if args.state == 'on' else '0')
        if response and response.get('status') == 'Success':
            print("Successfully set time OSD.")
        else:
            print("Failed to set time OSD.")

class GetScreenAutoSleep(Action):
    @staticmethod
    def get_name(): return "getscreenautosleep"
    @staticmethod
    def get_help(): return "Get screen auto sleep time."
    def execute(self, args):
        response = self.camera_api.call('get_screen_auto_sleep')
        if response:
            print(f"\nScreen Auto Sleep Time: {response.get('time', 'N/A')}\n")
        else:
            print("Failed to get screen auto sleep time.")

class SetScreenAutoSleep(Action):
    @staticmethod
    def get_name(): return "setscreenautosleep"
    @staticmethod
    def get_help(): return "Set screen auto sleep time (NOT IMPLEMENTED)."
    def execute(self, args):
        print("This command is not yet implemented for this camera model.")

class GetBootAction(Action):
    @staticmethod
    def get_name(): return "getbootaction"
    @staticmethod
    def get_help(): return "Get boot action."
    def execute(self, args):
        response = self.camera_api.call('get_boot_action')
        if response:
            print(f"\nBoot Action: {response.get('action', 'N/A')}\n")
        else:
            print("Failed to get boot action.")

class SetBootAction(Action):
    @staticmethod
    def get_name(): return "setbootaction"
    @staticmethod
    def get_help(): return "Set boot action."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("action", help="The action to perform on boot.")
    def execute(self, args):
        response = self.camera_api.call('set_boot_action', args.action)
        if response and response.get('status') == 'Success':
            print("Successfully set boot action.")
        else:
            print("Failed to set boot action.")

class GetAudioEncode(Action):
    @staticmethod
    def get_name(): return "getaudioencode"
    @staticmethod
    def get_help(): return "Get audio encode status."
    def execute(self, args):
        response = self.camera_api.call('get_audio_encode')
        if response:
            print(f"\nAudio Encode: {'On' if response.get('enable') == '1' else 'Off'}\n")
        else:
            print("Failed to get audio encode status.")

class SetAudioEncode(Action):
    @staticmethod
    def get_name(): return "setaudioencode"
    @staticmethod
    def get_help(): return "Set audio encode status."
    @staticmethod
    def add_arguments(parser):
        parser.add_argument("state", choices=['on', 'off'])
    def execute(self, args):
        response = self.camera_api.call('set_audio_encode', '1' if args.state == 'on' else '0')
        if response and response.get('status') == 'Success':
            print("Successfully set audio encode status.")
        else:
            print("Failed to set audio encode status.")

class Reset(Action):
    @staticmethod
    def get_name(): return "reset"
    @staticmethod
    def get_help(): return "Reset the camera."
    def execute(self, args):
        response = self.camera_api.call('reset')
        if response:
            print("Camera reset.", response)
        else:
            print("Failed to reset camera.")

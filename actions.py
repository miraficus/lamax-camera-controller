import requests
import json
import os
from datetime import datetime
from urllib.parse import quote
from camera_api import CameraAPI

class Action:
    """Base class for camera actions."""

    def __init__(self, camera_api):
        self.camera_api = camera_api

    @staticmethod
    def add_arguments(parser):
        """Add action-specific arguments to the parser."""
        pass

    def execute(self, args):
        """Execute the action with the given arguments."""
        pass

class GetPrimaryMenu(Action):
    """Get primary menu items and their current values for a given workmode."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode to get the menu for (e.g., NormalPhoto, System).")

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_primary_menu', workmode=args.workmode)
            response = self.camera_api.send_command(endpoint)
            if response:
                print(f"\n--- Primary Menu for {args.workmode} ---")
                items = response.get('item', '').split(',')
                current_values = response.get('cur', '').split(',')

                if not items or items == ['']:
                    print("  No primary menu items found for this mode.")
                    return

                for i, item in enumerate(items):
                    current_value = current_values[i] if i < len(current_values) else 'N/A'
                    print(f"  {item}: {current_value}")
                print("----------------------------------\n")
            else:
                print(f"Failed to retrieve primary menu for {args.workmode}.")
        except KeyError as e:
            print(f"Error: {e}")

class GetSecondaryMenu(Action):
    """Get all available options for a specific setting within a workmode."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode of the setting (e.g., NormalVideo).")
        parser.add_argument("name", help="The name of the setting (e.g., 'Lens angle').")

    def execute(self, args):
        try:
            encoded_name = quote(args.name)
            endpoint = self.camera_api.get_command('get_secondary_menu', workmode=args.workmode, name=encoded_name)
            response = self.camera_api.send_command(endpoint)
            if response:
                print(f"\n--- Options for '{args.name}' in {args.workmode} ---")
                print(f"  Available: {response.get('item', 'N/A')}")
                print(f"  Current: {response.get('value', 'N/A')}")
                print("-------------------------------------------\n")
            else:
                print(f"Failed to retrieve options for '{args.name}'.")
        except KeyError as e:
            print(f"Error: {e}")

class SetParameter(Action):
    """Set the value for a specific setting within a workmode."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The workmode of the setting.")
        parser.add_argument("name", help="The name of the setting to change.")
        parser.add_argument("value", help="The new value for the setting.")

    def execute(self, args):
        try:
            encoded_name = quote(args.name)
            encoded_value = quote(args.value)
            endpoint = self.camera_api.get_command('set_parameter', workmode=args.workmode, name=encoded_name, value=encoded_value)
            response = self.camera_api.send_command(endpoint)
            if response:
                print(f"Successfully set '{args.name}' to '{args.value}' in {args.workmode}.", response)
            else:
                print(f"Failed to set parameter '{args.name}'.")
        except KeyError as e:
            print(f"Error: {e}")

class SDCardStatus(Action):
    """Get SD card status."""
    
    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_sd_card_status')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- SD Card Status ---")
                print(f"  State: {response.get('sdstate', 'N/A')}")
                print(f"  Total Space: {response.get('total', 'N/A')}")
                print(f"  Used Space: {response.get('used', 'N/A')}")
                print("----------------------\n")
            else:
                print("Failed to retrieve SD card status.")
        except KeyError as e:
            print(f"Error: {e}")

class SDCardFormat(Action):
    """Format SD card."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('format_sd_card')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- SD Card Format ---")
                print(f"  Status: {'Yes' if response.get('sdstatus', '0') == '1' else 'No'}")
                print(f"  Free Space: {response.get('sdfreespace', 'N/A')}")
                print(f"  Total Space: {response.get('sdtotalspace', 'N/A')}")
                print(f"  Partition Number: {response.get('partitionnum', 'N/A')}")
                print("----------------------\n")
            else:
                print("Failed to format SD card.")
        except KeyError as e:
            print(f"Error: {e}")

class StartRecording(Action):
    """Start video recording."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('start_recording')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("Recording started.", response)
            else:
                print("Failed to start recording.")
        except KeyError as e:
            print(f"Error: {e}")

class StopRecording(Action):
    """Stop video recording."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('stop_recording')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("Recording stopped.", response)
            else:
                print("Failed to stop recording.")
        except KeyError as e:
            print(f"Error: {e}")

class TakePicture(Action):
    """Take a picture."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('take_picture')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("Picture taken.", response)
            else:
                print("Failed to take picture.")
        except KeyError as e:
            print(f"Error: {e}")

class GetAllWorkmodes(Action):
    """Get all work modes."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_all_workmodes')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- Available Work Modes ---")
                for key, value in response.items():
                    print(f"  {key.capitalize()}: {', '.join(value.split(','))}")
                print("----------------------\n")
            else:
                print("Failed to retrieve work modes.")
        except KeyError as e:
            print(f"Error: {e}")

class SetWorkmode(Action):
    """Set work mode."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("workmode", help="The work mode to set.")

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('set_workmode', workmode=args.workmode)
            response = self.camera_api.send_command(endpoint)
            if response:
                print(f"Work mode set to {args.workmode}.", response)
            else:
                print("Failed to set work mode.")
        except KeyError as e:
            print(f"Error: {e}")

class GetCurrentWorkmode(Action):
    """Get current work mode."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_current_workmode')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- Current Work Mode ---")
                print(f"  Mode: {response.get('workmode', 'N/A')}")
                print(f"  Description: {response.get('value', 'N/A')}")
                print("-------------------------\n")
            else:
                print("Failed to retrieve current work mode.")
        except KeyError as e:
            print(f"Error: {e}")

class GetCurrentStatus(Action):
    """Get current camera status."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_current_status')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- Current Camera Status ---")
                for key, value in response.items():
                    print(f"  {key}: {value}")
                print("-----------------------------\n")
            else:
                print("Failed to retrieve camera status.")
        except KeyError as e:
            print(f"Error: {e}")

class GetDeviceInfo(Action):
    """Get device information."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_device_info')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- Device Information ---")
                for key, value in response.items():
                    print(f"  {key}: {value}")
                print("--------------------------\n")
            else:
                print("Failed to retrieve device information.")
        except KeyError as e:
            print(f"Error: {e}")

class SetSystemTime(Action):
    """Set system time."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("datetime", help="The date and time to set (YYYYMMDDHHMMSS). Current time will be used if not provided.", nargs='?', default=None)

    def execute(self, args):
        try:
            time_str = args.datetime
            if time_str is None:
                time_str = datetime.now().strftime("%Y%m%d%H%M%S")
            elif len(time_str) != 14:
                print("Invalid datetime format. Use YYYYMMDDHHMMSS (14 characters).")
                return

            endpoint = self.camera_api.get_command('set_system_time', time=time_str)
            response = self.camera_api.send_command(endpoint)

            if response:
                if args.datetime is None:
                    print("System time set to current time.", response)
                else:
                    print(f"System time set to {args.datetime}.", response)
            else:
                print("Failed to set system time.")
        except KeyError as e:
            print(f"Error: {e}")

class GetWifiInfo(Action):
    """Get WiFi information."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_wifi_info')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- WiFi information ---")
                for key, value in response.items():
                    print(f"  {key}: {value}")
                print("--------------------\n")
            else:
                print("Failed to retrieve WiFi information.")
        except KeyError as e:
            print(f"Error: {e}")

class SetWifiSettings(Action):
    """Set WiFi settings."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("ssid_prefix", help="The WiFi SSID prefix (e.g., 'mycam'). A suffix will be appended by the camera.")
        parser.add_argument("password", help="The WiFi password to set (exactly 10 characters long).")

    def execute(self, args):
        if len(args.password) != 10:
            print("Password must be exactly 10 characters long.")
            return

        full_ssid = f"{args.ssid_prefix}_123456"

        try:
            endpoint = self.camera_api.get_command('set_wifi_settings', ssid=full_ssid, password=args.password)
            response = self.camera_api.send_command(endpoint)
            if response:
                 print(f"Successfully set WiFi SSID to '{full_ssid}'.", response)
            else:
                print(f"WiFi settings command sent for SSID '{full_ssid}'.")
        except KeyError as e:
            print(f"Error: {e}")

class GetBatteryStatus(Action):
    """Get battery status."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_battery_status')
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- Battery Status ---")
                print(f"  Capacity: {response.get('capacity', 'N/A')}%")
                print(f"  Charging: {'Yes' if response.get('charge', '0') == '1' else 'No'}")
                print(f"  On AC Power: {'Yes' if response.get('ac', '0') == '1' else 'No'}")
                print("----------------------\n")
            else:
                print("Failed to retrieve battery status.")
        except KeyError as e:
            print(f"Error: {e}")

class GetFileCount(Action):
    """Get file count on SD card."""

    @staticmethod
    def add_arguments(parser):
        pass

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_file_count')
            response = self.camera_api.send_command(endpoint)
            if response:
                filecount = response.get('count', 'N/A')
                print("\n--- File Count ---")
                print(f"  Count: {filecount}")
                print("------------------\n")
                if filecount != 'N/A':
                    return filecount
                else:
                    return None
            else:
                print("Failed to retrieve file count.")
                return None
        except KeyError as e:
            print(f"Error: {e}")
            return None

class GetFileList(Action):
    """List files on the SD card in a formatted table."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("--start", type=int, default=1, help="The starting index of the file list.")
        parser.add_argument("--end", type=int, help="The ending index of the file list. Defaults to total file count.")

    def execute(self, args):
        try:
            end_index = args.end
            if not end_index:
                # If no end index is provided, get the total count first
                count_action = GetFileCount(self.camera_api)
                count_response = count_action.execute(args)
                if count_response:
                    end_index = int(count_response)
                    print(f"Found {end_index} total files. Fetching list...")
                else:
                    print("Could not determine total file count. Please specify with --end.")
                    return

            endpoint = self.camera_api.get_command('get_file_list', start=args.start, end=end_index)
            response = self.camera_api.send_command(endpoint)
            
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
                print("Failed to retrieve file list.")
        except KeyError as e:
            print(f"Error: {e}")


class DeleteFile(Action):
    """Delete a file from SD card."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("path", help="The path of the file to delete.")

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('delete_file', path=args.path)
            response = self.camera_api.send_command(endpoint)
            if response:
                print(f"File '{args.path}' deleted.", response)
            else:
                print(f"Failed to delete file '{args.path}'.")
        except KeyError as e:
            print(f"Error: {e}")

class GetFileInfo(Action):
    """Get file information."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("path", help="The path of the file to get information for.")

    def execute(self, args):
        try:
            endpoint = self.camera_api.get_command('get_file_info', path=args.path)
            response = self.camera_api.send_command(endpoint)
            if response:
                print("\n--- File Information ---")
                for key, value in response.items():
                    print(f"  {key}: {value}")
                print("------------------------\n")
            else:
                print(f"Failed to retrieve information for file '{args.path}'.")
        except KeyError as e:
            print(f"Error: {e}")

class DownloadFile(Action):
    """Download a file from the camera."""

    @staticmethod
    def add_arguments(parser):
        parser.add_argument("remote_path", help="The remote path of the file on the camera (e.g., sd/DCIM/.../file.MP4).")
        parser.add_argument("local_path", nargs='?', help="The local path to save the file. Defaults to the original filename in the current directory.")

    def execute(self, args):
        remote_path = args.remote_path

        local_path = args.local_path
        if not local_path:
            local_path = os.path.basename(remote_path)

        self.camera_api.download_file(remote_path, local_path)
import requests
import json
import os
from datetime import datetime
from urllib.parse import quote
from camera_api import CameraAPI

class Action:
    """Base class for camera actions."""

    def __init__(self, parser, camera_api):
        self.parser = parser
        self.camera_api = camera_api

    def add_arguments(self):
        """Add action-specific arguments to the parser."""
        pass

    def execute(self, args):
        """Execute the action with the given arguments."""
        pass

class GetPrimaryMenu(Action):
    """Get primary menu items and their current values for a given workmode."""

    def add_arguments(self):
        """Add action-specific arguments to the parser."""
        self.parser.add_argument("workmode", help="The workmode to get the menu for (e.g., NormalPhoto, System).")

    def execute(self, args):
        """Execute the action with the given arguments."""
        endpoint = f"getprimarymenuitem.cgi?-workmode={args.workmode}"
        response = self.camera_api.send_command(endpoint)
        if response:
            print(f"\n--- Primary Menu for {args.workmode} ---")
            items = response.get('item', '').split(',')
            current_values = response.get('cur', '').split(',')
            
            if not items or items == ['']:
                print("  No primary menu items found for this mode.")
                return

            for i, item in enumerate(items):
                # Ensure we don't go out of bounds if the lists don't match
                current_value = current_values[i] if i < len(current_values) else 'N/A'
                print(f"  {item}: {current_value}")
            print("----------------------------------\n")
        else:
            print(f"Failed to retrieve primary menu for {args.workmode}.")

class GetSecondaryMenu(Action):
    """Get all available options for a specific setting within a workmode."""

    def add_arguments(self):
        """Add action-specific arguments to the parser."""
        self.parser.add_argument("workmode", help="The workmode of the setting (e.g., NormalVideo).")
        self.parser.add_argument("name", help="The name of the setting (e.g., 'Lens angle').")

    def execute(self, args):
        """Execute the action with the given arguments."""
        # URL encode the name parameter in case it contains spaces
        encoded_name = quote(args.name)
        endpoint = f"getsecondmenuitem.cgi?-workmode={args.workmode}&-name={encoded_name}"
        response = self.camera_api.send_command(endpoint)
        if response:
            print(f"\n--- Options for '{args.name}' in {args.workmode} ---")
            print(f"  Available: {response.get('item', 'N/A')}")
            print(f"  Current: {response.get('value', 'N/A')}")
            print("-------------------------------------------\n")
        else:
            print(f"Failed to retrieve options for '{args.name}'.")

class SetParameter(Action):
    """Set the value for a specific setting within a workmode."""

    def add_arguments(self):
        """Add action-specific arguments to the parser."""
        self.parser.add_argument("workmode", help="The workmode of the setting.")
        self.parser.add_argument("name", help="The name of the setting to change.")
        self.parser.add_argument("value", help="The new value for the setting.")

    def execute(self, args):
        """Execute the action with the given arguments."""
        encoded_name = quote(args.name)
        encoded_value = quote(args.value)
        endpoint = f"setcurparameter.cgi?-workmode={args.workmode}&-name={encoded_name}&-value={encoded_value}"
        response = self.camera_api.send_command(endpoint)
        if response:
            print(f"Successfully set '{args.name}' to '{args.value}' in {args.workmode}.", response)
        else:
            print(f"Failed to set parameter '{args.name}'.")

class SDCardStatus(Action):
    """Get SD card status."""
    
    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getsdstate.cgi")
        if response:
            print("\n--- SD Card Status ---")
            print(f"  State: {response.get('sdstate', 'N/A')}")
            print(f"  Total Space: {response.get('total', 'N/A')}")
            print(f"  Used Space: {response.get('used', 'N/A')}")
            print("----------------------\n")
        else:
            print("Failed to retrieve SD card status.")

class SDCardFormat(Action):
    """Format SD card."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("sdcommand.cgi?-format&-partition=1")
        if response:
            print("\n--- SD Card Format ---")
            print(f"  Status: {'Yes' if response.get('sdstatus', '0') == '1' else 'No'}")
            print(f"  Free Space: {response.get('sdfreespace', 'N/A')}")
            print(f"  Total Space: {response.get('sdtotalspace', 'N/A')}")
            print(f"  Partition Number: {response.get('partitionnum', 'N/A')}")
            print("----------------------\n")
        else:
            print("Failed to format SD card.")

class StartRecording(Action):
    """Start video recording."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("record.cgi?&-cmd=start")
        if response:
            print("Recording started.", response)
        else:
            print("Failed to start recording.")

class StopRecording(Action):
    """Stop video recording."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("record.cgi?&-cmd=stop")
        if response:
            print("Recording stopped.", response)
        else:
            print("Failed to stop recording.")

class TakePicture(Action):
    """Take a picture."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("photo.cgi?&-type=photo")
        if response:
            print("Picture taken.", response)
        else:
            print("Failed to take picture.")

class GetAllWorkmodes(Action):
    """Get all work modes."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getallworkmode.cgi")
        if response:
            print("\n--- Available Work Modes ---")
            for key, value in response.items():
                print(f"  {key.capitalize()}: {', '.join(value.split(','))}")
            print("----------------------\n")
        else:
            print("Failed to retrieve work modes.")

class SetWorkmode(Action):
    """Set work mode."""

    def add_arguments(self):
        self.parser.add_argument("workmode", help="The work mode to set.")

    def execute(self, args):
        response = self.camera_api.send_command("setcurworkmode.cgi?-workmode=" + args.workmode)
        if response:
            print(f"Work mode set to {args.workmode}.", response)
        else:
            print("Failed to set work mode.")

class GetCurrentWorkmode(Action):
    """Get current work mode."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getcurworkmode.cgi")
        if response:
            print("\n--- Current Work Mode ---")
            print(f"  Mode: {response.get('workmode', 'N/A')}")
            print(f"  Description: {response.get('value', 'N/A')}")
            print("-------------------------\n")
        else:
            print("Failed to retrieve current work mode.")

class GetCurrentStatus(Action):
    """Get current camera status."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getgetcurallinfostatus.cgi")
        if response:
            print("\n--- Current Camera Status ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("-----------------------------\n")
        else:
            print("Failed to retrieve camera status.")

class GetDeviceInfo(Action):
    """Get device information."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getdeviceattr.cgi")
        if response:
            print("\n--- Device Information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("--------------------------\n")
        else:
            print("Failed to retrieve device information.")

class SetSystemTime(Action):
    """Set system time."""

    def add_arguments(self):
        self.parser.add_argument("datetime", help="The date and time to set (YYYYMMDDHHMMSS). Current time will be used if not provided.", nargs='?', default=None)

    def execute(self, args):
        if args.datetime is None:
            now = datetime.now().strftime("%Y%m%d%H%M%S")
            response = self.camera_api.send_command("setsystemtime.cgi?-time=" + now)
            if response:
                print("System time set to current time.", response)
            else:
                print("Failed to set system time.")
            return
        else:
            if len(args.datetime) != 12:
                print("Invalid datetime format. Use YYYYMMDDHHMMSS.")
                return
            response = self.camera_api.send_command("setsystemtime.cgi?-time=" + args.datetime)
        if response:
            print(f"System time set to {args.datetime}.", response)
        else:
            print("Failed to set system time.")

class GetWifiInfo(Action):
    """Get WiFi information."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getwifi.cgi")
        if response:
            print("\n--- WiFi information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("--------------------\n")
        else:
            print("Failed to retrieve WiFi information.")

class SetWifiSettings(Action):
    """Set WiFi settings."""

    def add_arguments(self):
        self.parser.add_argument("ssid", help="The WiFi SSID to set (exactly 9 characters long).")
        self.parser.add_argument("password", help="The WiFi password to set (exactly 10 characters long).")

    def execute(self, args):
        if len(args.ssid) != 9:
            print("SSID must be exactly 9 characters long.")
            return
        if len(args.password) != 10:
            print("Password must be exactly 10 characters long.")
            return
        command = f"setwifi.cgi?&-wifissid={args.ssid}_123456&-wifikey={args.password}"
        try:
            response = self.camera_api.send_command(command)
        except requests.RequestException as e:
            print(f"Error updating WiFi settings: {e}")

class GetBatteryStatus(Action):
    """Get battery status."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getbatterycapacity.cgi")
        if response:
            print("\n--- Battery Status ---")
            print(f"  Capacity: {response.get('capacity', 'N/A')}%")
            print(f"  Charging: {'Yes' if response.get('charge', '0') == '1' else 'No'}")
            print(f"  On AC Power: {'Yes' if response.get('ac', '0') == '1' else 'No'}")
            print("----------------------\n")
        else:
            print("Failed to retrieve battery status.")

class GetFileCount(Action):
    """Get file count on SD card."""

    def add_arguments(self):
        pass

    def execute(self, args):
        response = self.camera_api.send_command("getfilecount.cgi")
        if response:
            print("\n--- File Count ---")
            filecount = response.get('count', 'N/A')
            print(f"  Count: {filecount}")
            print("------------------\n")
            if filecount != 'N/A':
                return filecount
            else:
                return None
        else:
            print("Failed to retrieve file count.")
            return None

class GetFileList(Action):
    """List files on the SD card in a formatted table."""

    def add_arguments(self):
        self.parser.add_argument("--start", type=int, default=1, help="The starting index of the file list.")
        self.parser.add_argument("--end", type=int, help="The ending index of the file list. Defaults to total file count.")

    def execute(self, args):
        end_index = args.end
        if not end_index:
            # If no end index is provided, get the total count first
            count_response = GetFileCount(parser=self.parser, camera_api=self.camera_api).execute(self)
            if count_response:
                end_index = int(count_response)
                print(f"Found {end_index} total files. Fetching list...")
            else:
                print("Could not determine total file count. Please specify with --end.")
                return

        endpoint = f"getfilelistinfoios.cgi?&-start={args.start}&-end={end_index}"
        response = self.camera_api.send_command(endpoint)
        
        if response and 'data' in response:
            print("\n--- File List ---")
            # Print a formatted header for the table
            print(f"{'Size (MB)':<12}{'Duration (s)':<14}{'Created':<22}{'Path'}")
            print(f"{'-----------':<12}{'------------':<14}{'--------------------':<22}{'----'}")
            
            for file_info in response['data']:
                # Calculate size in MB and format it
                try:
                    size_mb = int(file_info.get('size', 0)) / (1024 * 1024)
                    size_str = f"{size_mb:<12.2f}"
                except (ValueError, TypeError):
                    size_str = f"{'N/A':<12}"

                duration = file_info.get('time', 'N/A')
                created_date = file_info.get('create', 'N/A')
                path = file_info.get('path', 'N/A')
                
                # Print the formatted row
                print(f"{size_str}{duration:<14}{created_date:<22}{path}")

            print("-----------------\n")
        else:
            print("Failed to retrieve file list.")


class DeleteFile(Action):
    """Delete a file from SD card."""

    def add_arguments(self):
        self.parser.add_argument("path", help="The path of the file to delete.")

    def execute(self, args):
        endpoint = f"deletefile.cgi?&-name={args.path}"
        response = self.camera_api.send_command(endpoint)
        if response:
            print(f"File '{args.path}' deleted.", response)
        else:
            print(f"Failed to delete file '{args.path}'.")

class GetFileInfo(Action):
    """Get file information."""

    def add_arguments(self):
        self.parser.add_argument("path", help="The path of the file to get information for.")

    def execute(self, args):
        endpoint = f"getfileinfo.cgi?&-name={args.path}"
        response = self.camera_api.send_command(endpoint)
        if response:
            print("\n--- File Information ---")
            for key, value in response.items():
                print(f"  {key}: {value}")
            print("------------------------\n")
        else:
            print(f"Failed to retrieve information for file '{args.path}'.")

class DownloadFile(Action):
    """Download a file from the camera."""

    def add_arguments(self):
        self.parser.add_argument("remote_path", help="The remote path of the file on the camera (e.g., sd/DCIM/.../file.MP4).")
        self.parser.add_argument("local_path", nargs='?', help="The local path to save the file. Defaults to the original filename in the current directory.")

    def execute(self, args):
        remote_path = args.remote_path

        local_path = args.local_path
        if not local_path:
            # Default to saving in the current directory with the same filename
            local_path = os.path.basename(remote_path)

        # The download logic is now in the CameraAPI class
        self.camera_api.download_file(remote_path, local_path)
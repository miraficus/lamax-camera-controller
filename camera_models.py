"""
This module defines the command sets for various camera models.

Each camera model is represented by a dictionary containing key information
and a `commands` dictionary. The `commands` dictionary maps a generic action
name (e.g., 'start_recording') to the specific CGI command string used by
that camera model.

Placeholders in command strings (e.g., {workmode}) can be formatted
by the CameraAPI.
"""

MODELS = {
    "LAMAX_X9_1": {
        "model_name": "LAMAX X9.1",
        "cgi_base_path": "/cgi-bin/hi3510/",
        "commands": {
            # Menu and Settings
            "get_primary_menu": "getprimarymenuitem.cgi?-workmode={workmode}",
            "get_secondary_menu": "getsecondmenuitem.cgi?-workmode={workmode}&-name={name}",
            "set_parameter": "setcurparameter.cgi?-workmode={workmode}&-name={name}&-value={value}",

            # SD Card
            "get_sd_card_status": "getsdstate.cgi",
            "format_sd_card": "sdcommand.cgi?-format&-partition=1",

            # Recording and Photos
            "start_recording": "record.cgi?&-cmd=start",
            "stop_recording": "record.cgi?&-cmd=stop",
            "take_picture": "photo.cgi?&-type=photo",

            # Workmodes
            "get_all_workmodes": "getallworkmode.cgi",
            "get_current_workmode": "getcurworkmode.cgi",
            "set_workmode": "setcurworkmode.cgi?-workmode={workmode}",

            # Device Status and Info
            "get_current_status": "getcurallinfo.cgi", # Corrected from getgetcurallinfostatus.cgi
            "get_device_info": "getdeviceattr.cgi",
            "get_battery_status": "getbatterycapacity.cgi",

            # System
            "set_system_time": "setsystemtime.cgi?-time={time}",

            # Wi-Fi
            "get_wifi_info": "getwifi.cgi",
            "set_wifi_settings": "setwifi.cgi?&-wifissid={ssid}&-wifikey={password}",

            # File Management
            "get_file_count": "getfilecount.cgi",
            "get_file_list": "getfilelistinfoios.cgi?&-start={start}&-end={end}",
            "delete_file": "deletefile.cgi?&-name={path}",
            "get_file_info": "getfileinfo.cgi?&-name={path}",
        }
    }
    # Future camera models can be added here
    # "ANOTHER_MODEL": { ... }
}

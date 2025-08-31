from abc import ABC, abstractmethod

class Action(ABC):
    """
    Abstract base class for a camera action.
    This is the user-facing part of a command.
    """
    def __init__(self, camera_api):
        self.camera_api = camera_api

    @staticmethod
    @abstractmethod
    def get_name():
        """Returns the command name for the CLI."""
        pass

    @staticmethod
    @abstractmethod
    def get_help():
        """Returns the help text for the command."""
        pass

    @staticmethod
    def get_api_method_name():
        """
        Returns the name of the method to call on the CameraAPI.
        By default, it's the same as the action's name.
        """
        # This is a bit of a placeholder. In a real implementation,
        # we might convert from CamelCase to snake_case or have a mapping.
        # For now, we assume the action name is the method name.
        return ""

    @staticmethod
    def add_arguments(parser):
        """Add action-specific arguments to the parser."""
        pass

    @abstractmethod
    def execute(self, args):
        """Execute the action with the given arguments."""
        pass


class CameraModel(ABC):
    """
    Abstract base class for a camera model. It defines the interface
    that all camera models must implement to define their commands.
    """
    def __init__(self, model_name, cgi_base_path):
        self.model_name = model_name
        self.cgi_base_path = cgi_base_path

    @staticmethod
    @abstractmethod
    def get_model_name():
        """Returns the user-facing model name (e.g., 'LAMAX X9.1')."""
        pass

    @staticmethod
    @abstractmethod
    def get_cli_name():
        """Returns the name used for the --model CLI flag (e.g., 'X9_1')."""
        pass

    @abstractmethod
    def get_actions(self):
        """Returns a list of Action classes supported by this model."""
        pass

    # Generic command methods that models must implement
    @abstractmethod
    def get_parameter_options(self, workmode, name): pass
    @abstractmethod
    def set_parameter(self, workmode, name, value): pass
    @abstractmethod
    def get_sd_card_status(self): pass
    @abstractmethod
    def format_sd_card(self): pass
    @abstractmethod
    def start_recording(self): pass
    @abstractmethod
    def stop_recording(self): pass
    @abstractmethod
    def take_picture(self): pass
    @abstractmethod
    def get_all_workmodes(self): pass
    @abstractmethod
    def set_workmode(self, workmode): pass
    @abstractmethod
    def get_current_workmode(self): pass
    @abstractmethod
    def get_current_status(self): pass
    @abstractmethod
    def get_device_info(self): pass
    @abstractmethod
    def set_system_time(self, time): pass
    @abstractmethod
    def get_wifi_info(self): pass
    @abstractmethod
    def set_wifi_settings(self, ssid, password): pass
    @abstractmethod
    def get_battery_status(self): pass
    @abstractmethod
    def get_file_count(self): pass
    @abstractmethod
    def get_file_list(self, start, end): pass
    @abstractmethod
    def delete_file(self, path): pass
    @abstractmethod
    def get_file_info(self, path): pass
    @abstractmethod
    def get_parameter(self, workmode, name): pass
    @abstractmethod
    def get_led_state(self): pass
    @abstractmethod
    def set_led_state(self, enable): pass
    @abstractmethod
    def get_device_capabilities(self): pass
    @abstractmethod
    def get_viewfield(self): pass
    @abstractmethod
    def set_viewfield(self, fov): pass
    @abstractmethod
    def get_flip(self): pass
    @abstractmethod
    def set_flip(self, enable): pass
    @abstractmethod
    def get_time_osd(self): pass
    @abstractmethod
    def set_time_osd(self, enable): pass
    @abstractmethod
    def get_screen_auto_sleep(self): pass
    @abstractmethod
    def set_screen_auto_sleep(self, time): pass
    @abstractmethod
    def get_boot_action(self): pass
    @abstractmethod
    def set_boot_action(self, action): pass
    @abstractmethod
    def get_audio_encode(self): pass
    @abstractmethod
    def set_audio_encode(self, enable): pass
    @abstractmethod
    def reset(self): pass

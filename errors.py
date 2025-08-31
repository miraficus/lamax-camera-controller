class CameraError(Exception):
    """Base class for exceptions in this module."""
    pass

class CameraConnectionError(CameraError):
    """Raised when there is a problem connecting to the camera."""
    def __init__(self, message="Failed to connect to the camera"):
        self.message = message
        super().__init__(self.message)

class InvalidResponseError(CameraError):
    """Raised when the camera's response is invalid or unexpected."""
    def __init__(self, message="Invalid response from the camera"):
        self.message = message
        super().__init__(self.message)

class CommandNotSupportedError(CameraError):
    """Raised when a command is not supported by the camera model."""
    def __init__(self, command_name, model_name):
        self.message = f"The command '{command_name}' is not supported by the '{model_name}' model."
        super().__init__(self.message)

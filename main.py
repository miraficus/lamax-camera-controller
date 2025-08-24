import argparse
from camera_api import CameraAPI
from actions import *

CAMERA_IP = "192.168.0.1"
CAMERA_MODEL = "hi3510"

CGI_BASE_URL = f"http://{CAMERA_IP}/cgi-bin/{CAMERA_MODEL}/"
FILE_BASE_URL = f"http://{CAMERA_IP}/"

def main():
    parser = argparse.ArgumentParser(description="Control an action camera over WiFi.")
    subparsers = parser.add_subparsers(dest="command", help="The command to send to the camera.", required=True)

    camera_api = CameraAPI(CGI_BASE_URL, FILE_BASE_URL)

    actions = {}
    for action_class in Action.__subclasses__():
        command_name = action_class.__name__.lower()
        action_parser = subparsers.add_parser(command_name, help=action_class.__doc__)
        action_instance = action_class(action_parser, camera_api)
        action_instance.add_arguments()
        actions[command_name] = action_instance

    args = parser.parse_args()

    if args.command:
        actions[args.command].execute(args)

if __name__ == "__main__":
    main()
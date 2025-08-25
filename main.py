import argparse
import sys
from camera_api import CameraAPI
import actions
from camera_models import MODELS

def main():
    parser = argparse.ArgumentParser(description="Control an action camera over WiFi.")
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera.")
    parser.add_argument("--model", default="LAMAX_X9_1", choices=MODELS.keys(), help="The model of the camera.")

    subparsers = parser.add_subparsers(dest="command", help="The command to send to the camera.", required=True)

    # --- Dynamically discover and register all actions ---
    action_classes = {}
    for action_class in actions.Action.__subclasses__():
        command_name = action_class.__name__.lower()
        action_parser = subparsers.add_parser(command_name, help=action_class.__doc__)
        action_classes[command_name] = action_class
        # Call the static method to add action-specific arguments
        action_class.add_arguments(action_parser)

    args = parser.parse_args()

    # --- Initialize CameraAPI with the selected model ---
    selected_model = MODELS.get(args.model)
    if not selected_model:
        print(f"Error: Model '{args.model}' not found.")
        sys.exit(1)

    camera_api = CameraAPI(camera_ip=args.ip, model_definition=selected_model)

    # --- Instantiate and execute the selected action ---
    if args.command:
        action_class_to_run = action_classes[args.command]
        action_instance = action_class_to_run(camera_api)
        action_instance.execute(args)

if __name__ == "__main__":
    main()
import argparse
import sys
import os
import importlib
from camera_api import CameraAPI
from models.base import CameraModel, Action

def discover_models():
    """Dynamically discovers and loads camera models from the 'models' directory."""
    models = {}
    models_dir = os.path.join(os.path.dirname(__file__), 'models')
    for filename in os.listdir(models_dir):
        if filename.endswith('.py') and not filename.startswith('__') and not filename == 'base.py':
            module_name = f"models.{filename[:-3]}"
            try:
                module = importlib.import_module(module_name)
                for item_name in dir(module):
                    item = getattr(module, item_name)
                    if isinstance(item, type) and issubclass(item, CameraModel) and item is not CameraModel:
                        model_instance = item()
                        cli_name = model_instance.get_cli_name()
                        if cli_name in models:
                            print(f"Warning: Duplicate model CLI name '{cli_name}' found. Overwriting.")
                        models[cli_name] = model_instance
            except Exception as e:
                print(f"Warning: Could not load model from {filename}: {e}")
    return models

def main():
    """Main function to run the camera control CLI."""
    available_models = discover_models()
    if not available_models:
        print("Error: No camera models found. Exiting.")
        sys.exit(1)

    # --- Pre-parser to find the model ---
    # This parser is created only to find the --model argument.
    # It has add_help=False so it doesn't conflict with the main parser's help.
    model_parser = argparse.ArgumentParser(add_help=False)
    model_parser.add_argument(
        "--model",
        required=True,
        choices=available_models.keys(),
        help="The model of the camera to control."
    )
    # We use parse_known_args to grab just the --model argument and leave the rest.
    model_args, _ = model_parser.parse_known_args()
    selected_model = available_models[model_args.model]

    # --- Main parser ---
    # Now we build the main parser, using the model_parser as a parent.
    # This makes the --model argument available in the final parsed args.
    parser = argparse.ArgumentParser(
        description="Control an action camera over WiFi using a model-specific plugin system.",
        parents=[model_parser] # Inherit the --model argument
    )
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera.")

    subparsers = parser.add_subparsers(dest="command", help="The command to execute.", required=True)

    action_classes = {}
    for action_class in selected_model.get_actions():
        command_name = action_class.get_name()
        action_parser = subparsers.add_parser(
            command_name,
            help=action_class.get_help(),
            formatter_class=argparse.RawTextHelpFormatter
        )
        action_class.add_arguments(action_parser)
        action_classes[command_name] = action_class

    # Parse all arguments with the fully constructed parser.
    args = parser.parse_args()

    # --- Initialize CameraAPI and execute action ---
    camera_api = CameraAPI(camera_ip=args.ip, model=selected_model)

    if args.command:
        action_class_to_run = action_classes[args.command]
        action_instance = action_class_to_run(camera_api)
        action_instance.execute(args)

if __name__ == "__main__":
    main()
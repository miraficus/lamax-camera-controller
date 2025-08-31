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

    parser = argparse.ArgumentParser(
        description="Control an action camera over WiFi using a model-specific plugin system.",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera.")
    parser.add_argument(
        "--model",
        required=True,
        choices=available_models.keys(),
        help="The model of the camera to control."
    )

    # This is a bit of a trick to get the model-specific subparsers working.
    # We parse the known arguments first to see which model was selected.
    args, remaining_argv = parser.parse_known_args()

    selected_model = available_models.get(args.model)
    if not selected_model:
        print(f"Error: Model '{args.model}' not found.")
        sys.exit(1)

    # Now, we add the subparsers for the actions of the selected model.
    subparsers = parser.add_subparsers(dest="command", help="The command to execute.", required=True)

    action_classes = {}
    for action_class in selected_model.get_actions():
        command_name = action_class.get_name()
        action_parser = subparsers.add_parser(command_name, help=action_class.get_help())
        action_class.add_arguments(action_parser)
        action_classes[command_name] = action_class

    # We parse the arguments again, this time with the subparsers.
    # We use the 'remaining_argv' so that the main parser doesn't complain about the command.
    final_args = parser.parse_args(remaining_argv)

    # --- Initialize CameraAPI with the selected model ---
    camera_api = CameraAPI(camera_ip=args.ip, model=selected_model)

    # --- Instantiate and execute the selected action ---
    if final_args.command:
        action_class_to_run = action_classes[final_args.command]
        action_instance = action_class_to_run(camera_api)
        action_instance.execute(final_args)

if __name__ == "__main__":
    main()
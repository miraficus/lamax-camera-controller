import argparse
import sys
import os
from camera_api import CameraAPI
from main import discover_models

def main():
    """
    Main function to run the compatibility test for all discovered camera models.
    """
    parser = argparse.ArgumentParser(description="Test camera compatibility by executing all commands for each model.")
    parser.add_argument("--ip", default="192.168.0.1", help="The IP address of the camera to test.")
    args = parser.parse_args()

    print(f"--- Starting Compatibility Test for Camera at {args.ip} ---")

    available_models = discover_models()
    if not available_models:
        print("Error: No camera models found. Exiting.")
        sys.exit(1)

    for model_cli_name, model_instance in available_models.items():
        print(f"\n--- Testing Model: {model_instance.get_model_name()} ({model_cli_name}) ---")

        camera_api = CameraAPI(camera_ip=args.ip, model=model_instance)
        actions_to_test = model_instance.get_actions()

        if not actions_to_test:
            print("  No actions defined for this model.")
            continue

        successful_commands = []
        failed_commands = []
        skipped_commands = []

        # --- Define dummy values for commands that need them ---
        dummy_args = argparse.Namespace(
            workmode="video",
            name="Resolution",
            value="1080P30",
            datetime="20230101000000",
            ssid_prefix="test_ssid",
            password="test_password_123",
            start=1,
            end=1,
            path="sd/DCIM/100GKUDV/DUMMY.MP4",
            state='on',
            action='idle'
        )

        # --- Define commands to exclude from testing ---
        # These are commands that are destructive or break the connection.
        EXCLUDED_COMMANDS = [
            "setwifisettings",
            "sdcardformat",
            "reset",
            "deletefile",
            "downloadfile" # This is a long running command
        ]

        for action_class in actions_to_test:
            action_name = action_class.get_name()

            if action_name in EXCLUDED_COMMANDS:
                print(f"  [SKIPPED] {action_name} (Excluded command)")
                skipped_commands.append(action_name)
                continue

            print(f"  Testing command: {action_name}...")
            try:
                action_instance = action_class(camera_api)

                # We need to provide all arguments the action might need.
                # The action itself will only use what it's been programmed to use.
                action_instance.execute(dummy_args)

                # A simple heuristic for success: if no exception was raised, we assume it worked.
                # The command's own output will indicate success or failure to the user.
                print(f"  [SUCCESS] {action_name}")
                successful_commands.append(action_name)

            except Exception as e:
                print(f"  [FAIL]    {action_name}: An unexpected error occurred - {e}")
                failed_commands.append(action_name)

        # --- Generate Summary Report ---
        print("\n--- Compatibility Summary ---")
        print(f"Model: {model_instance.get_model_name()}")
        print(f"Total Commands: {len(actions_to_test)}")
        print(f"Successful: {len(successful_commands)}")
        print(f"Failed: {len(failed_commands)}")
        print(f"Skipped: {len(skipped_commands)}")

        if failed_commands:
            print("\nFailed or Errored Commands:")
            for cmd in failed_commands:
                print(f"  - {cmd}")

        print(f"--- Finished Testing Model: {model_instance.get_model_name()} ---")

    print("\n--- Test Complete ---")

if __name__ == "__main__":
    main()

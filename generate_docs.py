import re
from main import discover_models

def generate_command_table(model_instance):
    """Generates a Markdown table for a model's commands."""
    lines = []
    lines.append("| Command | Description |")
    lines.append("| :--- | :--- |")

    actions = sorted(model_instance.get_actions(), key=lambda a: a.get_name())

    for action_class in actions:
        name = action_class.get_name()
        help_text = action_class.get_help()
        lines.append(f"| `{name}` | {help_text} |")

    return "\n".join(lines)

def main():
    """
    Auto-updates the command list in the README.md file.
    """
    readme_path = "readme.md"

    try:
        with open(readme_path, "r") as f:
            readme_content = f.read()
    except FileNotFoundError:
        print(f"Error: {readme_path} not found.")
        return

    print("Discovering models...")
    available_models = discover_models()

    if not available_models:
        print("No models found.")
        return

    for model_cli_name, model_instance in available_models.items():
        print(f"Generating docs for model: {model_instance.get_model_name()}")

        # Define the placeholder tags
        start_tag = f"<!-- COMMANDS_START_{model_cli_name.upper()} -->"
        end_tag = f"<!-- COMMANDS_END_{model_cli_name.upper()} -->"

        # Generate the new content
        table = generate_command_table(model_instance)
        new_content = f"{start_tag}\n{table}\n{end_tag}"

        # Use regex to replace the content between the tags
        pattern = re.compile(f"{re.escape(start_tag)}.*{re.escape(end_tag)}", re.DOTALL)

        if pattern.search(readme_content):
            readme_content = pattern.sub(new_content, readme_content)
            print(f"  -> Updated command list for {model_cli_name} in README.")
        else:
            print(f"  -> Warning: Placeholder tags for {model_cli_name} not found in README.")

    # Write the updated content back to the file
    with open(readme_path, "w") as f:
        f.write(readme_content)

    print("\nREADME update process complete.")

if __name__ == "__main__":
    main()

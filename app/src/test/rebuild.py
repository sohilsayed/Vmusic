import os
import re

def parse_file_layout(lines):
    structure = []
    stack = []

    for line in lines:
        if line.strip().startswith("📁") or line.strip().startswith("📄"):
            indent_level = (len(line) - len(line.lstrip())) // 4
            item_name = line.strip()[2:].strip()

            while len(stack) > indent_level:
                stack.pop()

            if line.strip().startswith("📁"):
                stack.append(item_name)
            elif line.strip().startswith("📄"):
                file_path = os.path.join(*stack, item_name)
                structure.append(file_path)
    return structure

def parse_code_content(lines):
    files = {}
    current_file = None
    buffer = []

    for line in lines:
        if line.startswith("// File: "):
            if current_file:
                files[current_file] = "\n".join(buffer).rstrip()
                buffer = []
            current_file = line[len("// File: "):].strip()
        else:
            buffer.append(line)
    if current_file:
        files[current_file] = "\n".join(buffer).rstrip()

    return files

def recreate_project(txt_path, output_dir):
    with open(txt_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    layout_start = lines.index("PROJECT FILE LAYOUT\n") + 2
    code_start = lines.index("===================\n", layout_start) + 2

    layout_lines = lines[layout_start:code_start - 2]
    code_lines = lines[code_start:]

    structure = parse_file_layout(layout_lines)
    files_content = parse_code_content(code_lines)

    for file_path in structure:
        abs_path = os.path.join(output_dir, file_path)
        os.makedirs(os.path.dirname(abs_path), exist_ok=True)
        content = files_content.get(file_path.replace("\\", "/"), "")
        with open(abs_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"✅ Created: {abs_path}")

if __name__ == "__main__":
    # Change these paths as needed
    input_txt_file = "main.txt"  # The generated file layout and code file
    output_directory = "reconstructed_project"

    recreate_project(input_txt_file, output_directory)
    print("🎉 Project reconstructed successfully.")
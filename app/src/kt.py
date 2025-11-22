import os
from fpdf import FPDF

def generate_file_layout(base_dir, extensions):
    layout_lines = []
    print(f"📁 Scanning: {base_dir}")
    for root, dirs, files in os.walk(base_dir):
        rel_root = os.path.relpath(root, base_dir)
        indent_level = rel_root.count(os.sep)
        if rel_root == ".":
            layout_lines.append("📁 .")
        else:
            layout_lines.append("    " * indent_level + f"📁 {os.path.basename(root)}")
        
        for file in sorted(files):
            if any(file.lower().endswith(ext.lower()) for ext in extensions):
                layout_lines.append("    " * (indent_level + 1) + f"📄 {file}")
                print(f"  → Including: {os.path.join(rel_root, file)}")
            else:
                print(f"  ✘ Skipping: {os.path.join(rel_root, file)}")
    return layout_lines

def collect_code(base_dir, extensions):
    code = ""
    file_count = 0
    for root, _, files in os.walk(base_dir):
        for file in files:
            if any(file.lower().endswith(ext.lower()) for ext in extensions):
                path = os.path.join(root, file)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        relative_path = os.path.relpath(path, base_dir)
                        code += f"// File: {relative_path}\n"
                        code += f.read() + "\n\n"
                        file_count += 1
                except Exception as e:
                    print(f"⚠️ Error reading {path}: {e}")
    return code, file_count

def save_as_text(file_layout, code, output_path):
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("PROJECT FILE LAYOUT\n===================\n")
        f.write("\n".join(file_layout))
        f.write("\n\n===================\nCODE CONTENT\n\n")
        f.write(code)

def save_as_pdf(file_layout, code, output_path):
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=10)
    pdf.add_page()
    pdf.set_font("Courier", size=10)

    def write_lines(lines):
        for line in lines:
            line = line[:200]  # Avoid overflow
            line = line.encode('latin-1', 'replace').decode('latin-1')
            pdf.cell(0, 5, txt=line, ln=1)

    write_lines(["PROJECT FILE LAYOUT", "==================="] + file_layout + ["", "===================", "CODE CONTENT", ""])
    write_lines(code.splitlines())

    pdf.output(output_path)

if __name__ == "__main__":
    # ✅ Use absolute path or "." for current folder
    base_dir = r"main"  # CHANGE THIS
    output_txt = "main.txt"
    output_pdf = "codebase.pdf"
    include_extensions = [".kt", ".xml"]

    file_layout = generate_file_layout(base_dir, include_extensions)
    code, count = collect_code(base_dir, include_extensions)

    if count == 0:
        print("❌ No .kt or .xml files found.")
    else:
        save_as_text(file_layout, code, output_txt)
        save_as_pdf(file_layout, code, output_pdf)
        print(f"✅ Done. Files saved:\n- {output_txt}\n- {output_pdf}")
